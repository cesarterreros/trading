package com.trading.monitor.ingest

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.trading.monitor.Settings
import com.trading.monitor.XLink
import com.trading.monitor.db.Account
import com.trading.monitor.db.MonitorDao
import com.trading.monitor.db.Post
import com.trading.monitor.db.Signal
import com.trading.monitor.db.Usage
import com.trading.monitor.directionOf
import com.trading.monitor.llm.DeepSeek
import com.trading.monitor.llm.SignalParser
import com.trading.monitor.manualPostId
import com.trading.monitor.market.Market
import com.trading.monitor.matrix.Candle
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.firstTarget
import com.trading.monitor.matrix.resolveOutcome
import com.trading.monitor.mergePostText
import com.trading.monitor.ocr.Ocr
import com.trading.monitor.parseLevels
import com.trading.monitor.timeframeOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/** Post flow: OCR the images of posts waiting for interpretation, interpret with DeepSeek, store the signal. */
class Pipeline(
    private val dao: MonitorDao,
    private val settings: Settings,
    private val deepSeek: DeepSeek,
    private val market: Market,
    private val http: OkHttpClient,
) {
    /** Human-readable state shown at the bottom of the Matrix screen. */
    val status = MutableStateFlow("Starting")

    private val processing = Mutex()

    /** Interprets waiting posts; one run at a time so no post is interpreted twice. */
    suspend fun processPending() = withContext(Dispatchers.IO) {
        processing.withLock { processPendingLocked() }
    }

    private suspend fun processPendingLocked() {
        val pending = dao.pendingPosts()
        if (pending.isEmpty()) return
        if (settings.apiKey.isBlank()) {
            status.value = "DeepSeek API key missing: add it in Settings (${pending.size} posts waiting)"
            return
        }
        for (post in pending) {
            val day = LocalDate.now().toString()
            val used = dao.tokens(day) ?: 0
            if (used >= settings.dailyTokenCap) {
                status.value = "Daily DeepSeek token cap reached; remaining posts wait until tomorrow"
                return
            }
            status.value = "Interpreting a post from @${post.handle}"
            try {
                val ocrTexts = post.imageUrls.split('\n').filter { it.isNotBlank() }.take(MAX_IMAGES)
                    .mapNotNull { url -> readImage(url) }
                if (post.text.isBlank() && ocrTexts.isEmpty()) {
                    dao.updatePost(post.copy(status = "SKIPPED", error = null))
                    continue
                }
                val price = priceAt(post.timeMs)
                val answer = deepSeek.chat(
                    SignalParser.SYSTEM_PROMPT,
                    SignalParser.userPrompt(
                        post.handle,
                        Instant.ofEpochMilli(post.timeMs).toString(),
                        post.text,
                        ocrTexts,
                        price,
                        fromScreenshot = post.imageUrls.contains("file://"),
                    ),
                )
                dao.setUsage(Usage(day, used + answer.tokens))

                val ocrJoined = ocrTexts.joinToString("\n---\n")
                val parsed = SignalParser.parse(answer.content, price)
                if (parsed == null) {
                    dao.updatePost(post.copy(status = "ERROR", ocrText = ocrJoined, error = "Unreadable model answer"))
                    continue
                }
                dao.insertSignal(
                    Signal(
                        postId = post.id,
                        handle = post.handle,
                        timeMs = post.timeMs,
                        direction = parsed.direction.name.lowercase(),
                        type = parsed.type,
                        conviction = parsed.conviction,
                        timeframe = parsed.timeframe.name.lowercase(),
                        entry = parsed.entry,
                        stop = parsed.stop,
                        targets = parsed.targets.joinToString(","),
                        support = parsed.support.joinToString(","),
                        resistance = parsed.resistance.joinToString(","),
                        evidence = parsed.evidence,
                        priceAtSignal = price,
                    )
                )
                dao.updatePost(post.copy(status = "DONE", ocrText = ocrJoined, error = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Network or API trouble: keep the post pending and retry on the next cycle.
                dao.updatePost(post.copy(error = e.message))
                status.value = "DeepSeek call failed: ${e.message}"
                return
            } catch (e: Exception) {
                dao.updatePost(post.copy(status = "ERROR", error = e.message))
            }
        }
    }

    /**
     * Stores a post added by hand. [handle] must already be normalized. Image files are moved into the
     * app's private posts folder. A post with the same id as an existing one is merged into it and interpreted again.
     */
    suspend fun addManualPost(context: Context, handle: String, timeMs: Long, text: String, images: List<File>, link: XLink?) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.addAccount(Account(handle))
            val id = link?.postId ?: manualPostId(now)
            val author = link?.handle ?: handle
            val dir = File(context.filesDir, "posts/$id")
            dir.mkdirs()
            val existing = dao.postById(id)
            val old = existing?.imageUrls.orEmpty().split('\n').filter { it.isNotBlank() }
            val added = images.mapIndexed { i, src ->
                val dest = File(dir, "${old.size + i + 1}.img")
                if (!src.renameTo(dest)) {
                    src.copyTo(dest, overwrite = true)
                    src.delete()
                }
                "file://" + dest.absolutePath
            }
            val urls = (old + added).joinToString("\n")
            if (existing != null) {
                dao.updatePost(
                    existing.copy(
                        text = mergePostText(existing.text, text),
                        imageUrls = urls,
                        status = "NEW",
                        error = null,
                    )
                )
            } else {
                dao.insertPost(
                    Post(
                        id = id,
                        handle = handle,
                        author = author,
                        timeMs = link?.timeMs ?: timeMs,
                        text = text.trim(),
                        imageUrls = urls,
                        isRepost = !author.equals(handle, ignoreCase = true),
                        status = "NEW",
                        fetchedMs = now,
                    )
                )
            }
        }

    /** Deletes a post, its signal and its stored screenshots. Waits for any interpretation in progress, so no signal is left without its post. */
    suspend fun deletePost(context: Context, id: String) = withContext(Dispatchers.IO) {
        processing.withLock {
            dao.deleteSignal(id)
            dao.deletePost(id)
            File(context.filesDir, "posts/$id").deleteRecursively()
        }
    }

    /** OCR text of one image, or null when it cannot be downloaded, decoded or holds no text. */
    private suspend fun readImage(url: String): String? = try {
        val bytes = if (url.startsWith("file://")) {
            File(Uri.parse(url).path!!).readBytes()
        } else {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        bitmap?.let { Ocr.read(it) }?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** BTC price when the post was published: the live price for fresh posts, the hourly close otherwise. */
    private fun priceAt(timeMs: Long): Double? {
        val live = market.state.value.price
        if (System.currentTimeMillis() - timeMs < 2 * HOUR_MS && live != null) return live
        return try {
            market.klines("1h", timeMs - HOUR_MS, 1).firstOrNull()?.close ?: live
        } catch (e: Exception) {
            live
        }
    }

    /** Marks open signals as WIN / LOSS / EXPIRED from hourly candles. */
    suspend fun resolveOutcomes() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (s in dao.unresolvedSignals()) {
            val direction = directionOf(s.direction)
            val reference = s.entry ?: s.priceAtSignal
            val stop = s.stop
            val target = reference?.let { firstTarget(direction, it, parseLevels(s.targets)) }
            val stopValid = reference != null && stop != null &&
                (if (direction == Direction.BULLISH) stop < reference else stop > reference)
            if (reference == null || stop == null || target == null || !stopValid) {
                dao.setOutcome(s.postId, "INVALID", now)
                continue
            }
            val horizonMs = timeframeOf(s.timeframe).horizonDays * DAY_MS
            val end = min(now, s.timeMs + horizonMs)
            try {
                val candles = mutableListOf<Candle>()
                var from = s.timeMs
                var pages = 0
                while (from < end && pages < 4) {
                    val page = market.klines("1h", from, 1000)
                    candles += page
                    pages++
                    if (page.size < 1000) break
                    from = page.last().openTimeMs + HOUR_MS
                }
                // An entry away from the price at posting time must trade before the result counts.
                val at = s.priceAtSignal
                val pendingEntry = s.entry?.takeIf { at != null && abs(it / at - 1) > 0.005 }
                val outcome = resolveOutcome(direction, pendingEntry, stop, target, s.timeMs, horizonMs, candles, now)
                if (outcome != null) dao.setOutcome(s.postId, outcome.name, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Candles unavailable right now: try again on the next pass.
            }
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L
        private const val MAX_IMAGES = 6
    }
}
