package com.trading.monitor

import com.trading.monitor.db.Account
import com.trading.monitor.db.Post
import com.trading.monitor.db.Signal
import com.trading.monitor.matrix.Direction

enum class SectionState { ACTIVE, PAUSED, REMOVED }

data class FeedSection(
    val handle: String,
    val state: SectionState,
    val posts: List<Post>,          // newest first
    val waiting: Int,               // posts with status NEW or ERROR
    val latestStance: Direction?,   // from the newest non-noise signal, else null
    val latestPostMs: Long?,        // null when the account has no posts
    val displayName: String? = null,
    val verified: Boolean = false,
    val avatarPath: String? = null,
)

/**
 * Groups posts by the account that collected them (not the post author), one section per
 * account plus one per removed handle that still has posts. Sections with posts come first,
 * newest activity first; empty sections follow alphabetically.
 */
fun groupFeed(accounts: List<Account>, posts: List<Post>, signals: List<Signal>): List<FeedSection> {
    val byHandle = accounts.associateBy { it.handle }
    val postsByHandle = posts.groupBy { it.handle }
    val newestSignal = signals
        .filter { it.type != "noise" }
        .groupBy { it.handle }
        .mapValues { (_, list) -> list.maxByOrNull { it.timeMs } }

    val sections = (byHandle.keys + postsByHandle.keys).map { handle ->
        val sectionPosts = postsByHandle[handle].orEmpty().sortedByDescending { it.timeMs }
        val account = byHandle[handle]
        FeedSection(
            handle = handle,
            state = when {
                account == null -> SectionState.REMOVED
                account.enabled -> SectionState.ACTIVE
                else -> SectionState.PAUSED
            },
            posts = sectionPosts,
            waiting = sectionPosts.count { it.status == "NEW" || it.status == "ERROR" },
            latestStance = newestSignal[handle]?.let { directionOf(it.direction) },
            latestPostMs = sectionPosts.firstOrNull()?.timeMs,
            displayName = account?.displayName,
            verified = account?.verified ?: false,
            avatarPath = account?.avatarPath,
        )
    }
    return sections.sortedWith(
        compareBy<FeedSection> { it.latestPostMs == null }
            .thenByDescending { it.latestPostMs ?: 0L }
            .thenBy { it.handle },
    )
}

/** Address of the original post on X. For a repost, [Post.author] is the original author. */
fun postUrl(post: Post): String = "https://x.com/${post.author}/status/${post.id}"

/** Sentiment filter applied to feed sections; see [filterSections]. */
enum class SentimentFilter { ALL, BULLISH, BEARISH, NEUTRAL }

/**
 * Keeps the sections whose handle or display name contains [query] (case-insensitive, one
 * leading "@" ignored, blank matches all) and whose [FeedSection.latestStance] matches
 * [sentiment]. A null stance only matches [SentimentFilter.ALL]. Order is preserved.
 */
fun filterSections(
    sections: List<FeedSection>,
    query: String,
    sentiment: SentimentFilter,
): List<FeedSection> {
    val trimmed = query.trim()
    val needle = if (trimmed.startsWith("@")) trimmed.substring(1) else trimmed
    return sections.filter { section ->
        val matchesQuery = needle.isBlank() ||
            section.handle.contains(needle, ignoreCase = true) ||
            section.displayName?.contains(needle, ignoreCase = true) == true
        val matchesSentiment = when (sentiment) {
            SentimentFilter.ALL -> true
            SentimentFilter.BULLISH -> section.latestStance == Direction.BULLISH
            SentimentFilter.BEARISH -> section.latestStance == Direction.BEARISH
            SentimentFilter.NEUTRAL -> section.latestStance == Direction.NEUTRAL
        }
        matchesQuery && matchesSentiment
    }
}

/**
 * First letter or digit of the display name, or of the handle when the display name is blank
 * or has none; upper-cased, or "?" when neither contains a letter or digit.
 */
fun initialOf(handle: String, displayName: String?): String {
    fun firstAlphaNumericOrNull(text: String): Char? = text.firstOrNull { it.isLetterOrDigit() }
    val initial = displayName
        ?.takeIf { it.isNotBlank() }
        ?.let { firstAlphaNumericOrNull(it) }
        ?: firstAlphaNumericOrNull(handle)
    return initial?.uppercaseChar()?.toString() ?: "?"
}

/** Stable palette index in `0 until paletteSize` derived from the lower-cased handle without a leading "@". */
fun avatarColorIndex(handle: String, paletteSize: Int): Int {
    val key = handle.removePrefix("@").lowercase()
    return Math.floorMod(key.hashCode(), paletteSize.coerceAtLeast(1))
}

/** [selected] when a section with that handle exists, otherwise null ("All accounts"). */
fun resolveSelectedAccount(selected: String?, sections: List<FeedSection>): String? =
    selected?.takeIf { handle -> sections.any { it.handle == handle } }

