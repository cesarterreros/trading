package com.trading.monitor.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trading.monitor.App
import com.trading.monitor.guessHandle
import com.trading.monitor.importImagesInto
import com.trading.monitor.normalizeHandle
import com.trading.monitor.ocr.Ocr
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

private val POSTED_OFFSETS = listOf(
    "now" to 0L,
    "1 h ago" to HOUR,
    "3 h ago" to 3 * HOUR,
    "6 h ago" to 6 * HOUR,
    "12 h ago" to 12 * HOUR,
    "1 day ago" to DAY,
    "2 days ago" to 2 * DAY,
    "3 days ago" to 3 * DAY,
    "1 week ago" to 7 * DAY,
)

private val POSTED_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@Composable
private fun hScroll(): Modifier = Modifier.horizontalScroll(rememberScrollState())

/** Full-screen form for a post added by hand; its content lives in [App.draft]. */
@Composable
fun AddPostScreen(app: App) {
    val draft = app.draft
    val accounts by remember { app.dao.accounts() }.collectAsState(initial = emptyList())
    val knownHandles = accounts.map { it.handle }
    var handle by remember { mutableStateOf("") }
    var offsetMs by remember { mutableLongStateOf(0L) }
    val normalized = normalizeHandle(handle)

    BackHandler { draft.clear(deleteFiles = true) }

    // Fills the account once, from the link, the text, or the first screenshot; never overwrites input.
    LaunchedEffect(draft.images.size, draft.link, knownHandles) {
        if (handle.isNotBlank()) return@LaunchedEffect
        val fromLink = draft.link?.handle?.let { normalizeHandle(it) }
        if (fromLink != null) {
            handle = fromLink
            return@LaunchedEffect
        }
        val fromText = guessHandle(draft.text, knownHandles)?.let { normalizeHandle(it) }
        if (fromText != null) {
            handle = fromText
            return@LaunchedEffect
        }
        val first = draft.images.firstOrNull() ?: return@LaunchedEffect
        val ocrText = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(first.absolutePath)?.let { Ocr.read(it) }
            } catch (e: Exception) {
                null
            }
        } ?: return@LaunchedEffect
        val guess = guessHandle(ocrText, knownHandles)
        val known = knownHandles.firstOrNull { it.equals(guess, ignoreCase = true) }
        if (known != null && handle.isBlank()) handle = known
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        app.scope.launch { importImagesInto(app, draft, uris) }
    }

    val onPickImages: () -> Unit = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val onCancel: () -> Unit = { draft.clear(deleteFiles = true) }
    val onSave: () -> Unit = {
        val h = normalized
        if (h != null) {
            val time = System.currentTimeMillis() - offsetMs
            val text = draft.text
            val images = draft.images.toList()
            val postLink = draft.link
            draft.clear(deleteFiles = false)
            app.scope.launch {
                app.pipeline.addManualPost(app, h, time, text, images, postLink)
                app.pollNow.tryEmit(Unit)
                app.pipeline.processPending()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= AppSize.WideBreakpoint) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.TextPrimary.copy(alpha = 0.4f))
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                AddPostContent(
                    app = app,
                    modifier = Modifier
                        .widthIn(max = AppSize.ReadingWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .cardBorder(),
                    handle = handle,
                    onHandleChange = { handle = it },
                    knownHandles = knownHandles,
                    normalized = normalized,
                    offsetMs = offsetMs,
                    onOffsetChange = { offsetMs = it },
                    onPickImages = onPickImages,
                    onSave = onSave,
                    onCancel = onCancel,
                )
            }
        } else {
            AddPostContent(
                app = app,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Surface)
                    .pointerInput(Unit) { detectTapGestures { } },
                handle = handle,
                onHandleChange = { handle = it },
                knownHandles = knownHandles,
                normalized = normalized,
                offsetMs = offsetMs,
                onOffsetChange = { offsetMs = it },
                onPickImages = onPickImages,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

/** The shared content of the form; the caller chooses between a sheet and a full screen. */
@Composable
private fun AddPostContent(
    app: App,
    modifier: Modifier,
    handle: String,
    onHandleChange: (String) -> Unit,
    knownHandles: List<String>,
    normalized: String?,
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onPickImages: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = app.draft
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Add post", style = AppType.ScreenTitle, color = AppColors.TextPrimary)
        }
        HorizontalDivider(color = AppColors.Border)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(AppSpace.m),
            verticalArrangement = Arrangement.spacedBy(AppSpace.m),
        ) {
            OutlinedTextField(
                value = handle,
                onValueChange = onHandleChange,
                label = { Text("Account (X handle)", style = AppType.Caption) },
                singleLine = true,
                textStyle = AppType.Body,
                shape = AppShape.Small,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                hScroll(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
            ) {
                knownHandles.forEach { h ->
                    AssistChip(
                        onClick = { onHandleChange(h) },
                        label = { Text("@$h", style = AppType.Label) },
                    )
                }
            }

            val link = draft.link
            if (link != null) {
                Text(
                    "Posted: ${POSTED_FORMAT.format(Instant.ofEpochMilli(link.timeMs))} (from the link)",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            } else {
                Text("Posted", style = AppType.Label, color = AppColors.TextPrimary)
                Row(
                    hScroll(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
                ) {
                    POSTED_OFFSETS.forEach { (label, ms) ->
                        FilterChip(
                            selected = offsetMs == ms,
                            onClick = { onOffsetChange(ms) },
                            label = { Text(label, style = AppType.Caption) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.text,
                onValueChange = { draft.text = it },
                label = { Text("Post text (optional when a screenshot has it)", style = AppType.Caption) },
                minLines = 4,
                textStyle = AppType.Body,
                shape = AppShape.Small,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Screenshots", style = AppType.Label, color = AppColors.TextPrimary)
            if (draft.images.isNotEmpty()) {
                Row(
                    hScroll(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    draft.images.toList().forEach { file ->
                        Box {
                            AsyncImage(
                                model = file,
                                contentDescription = "Screenshot",
                                modifier = Modifier
                                    .height(160.dp)
                                    .clip(AppShape.Small)
                                    .border(AppSize.Border, AppColors.Border, AppShape.Small),
                                contentScale = ContentScale.Fit,
                            )
                            IconButton(
                                onClick = {
                                    draft.images.remove(file)
                                    app.scope.launch(Dispatchers.IO) { file.delete() }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                                    .background(AppColors.TextPrimary, CircleShape),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove image",
                                    tint = AppColors.OnPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onPickImages,
                shape = AppShape.Small,
                border = BorderStroke(AppSize.Border, AppColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
            ) { Text("Add image", style = AppType.Label) }

            Text(
                "Expand the post (\"Show more\") before capturing.",
                color = AppColors.TextSecondary,
                style = AppType.Caption,
            )
            Text(
                "For charts, open the image full screen in X and capture that.",
                color = AppColors.TextSecondary,
                style = AppType.Caption,
            )
            Text(
                "Crop out replies when possible.",
                color = AppColors.TextSecondary,
                style = AppType.Caption,
            )

            if (handle.isNotBlank() && normalized == null) {
                Text("Not a valid X handle", color = AppColors.WarningText, style = AppType.Caption)
            }
            if (draft.failedImages > 0) {
                Text(
                    "${draft.failedImages} shared image(s) could not be read. Add them with \"Add image\".",
                    color = AppColors.WarningText,
                    style = AppType.Caption,
                )
            }
        }

        HorizontalDivider(color = AppColors.Border)
        Row(
            Modifier.fillMaxWidth().padding(AppSpace.m),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
            ) {
                Text("Cancel", color = AppColors.TextSecondary, style = AppType.Label)
            }
            Button(
                enabled = normalized != null && (draft.text.isNotBlank() || draft.images.isNotEmpty()),
                onClick = onSave,
                shape = AppShape.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                ),
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
            ) {
                Text("Save", style = AppType.Label)
            }
        }
    }
}
