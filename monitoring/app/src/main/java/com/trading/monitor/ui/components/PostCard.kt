package com.trading.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.trading.monitor.App
import com.trading.monitor.db.Account
import com.trading.monitor.db.Post
import com.trading.monitor.db.Signal
import com.trading.monitor.directionOf
import com.trading.monitor.isManualId
import com.trading.monitor.parseLevels
import com.trading.monitor.postUrl
import com.trading.monitor.ui.fmtAge
import com.trading.monitor.ui.fmtPrice
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import com.trading.monitor.ui.theme.tabular
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** One post as a native card: author, time, text, the author's view, media and actions. */
@Composable
fun PostCard(
    app: App,
    post: Post,
    signal: Signal?,
    account: Account?,
    onOpenMedia: (images: List<String>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOcr by remember(post.id) { mutableStateOf(false) }
    var menuOpen by remember(post.id) { mutableStateOf(false) }
    var confirmDelete by remember(post.id) { mutableStateOf(false) }
    val images = remember(post.imageUrls) {
        post.imageUrls.split('\n').filter { it.isNotBlank() }
    }

    Column(
        modifier = modifier
            .widthIn(max = AppSize.ReadingWidth)
            .fillMaxWidth()
            .cardBorder()
            .padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PostHeader(
            post = post,
            account = account,
            showOcr = showOcr,
            onToggleOcr = { showOcr = !showOcr },
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onRequestDelete = { confirmDelete = true },
        )
        PostStatus(post)
        if (post.text.isNotBlank()) {
            Text(post.text, style = AppType.Body, color = AppColors.TextPrimary)
        } else if (images.isNotEmpty()) {
            Text(
                text = "Text is in the screenshot below",
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
        }
        if (signal != null) {
            AuthorView(signal)
        }
        PostMedia(images = images, onOpenMedia = onOpenMedia)
        if (showOcr && post.ocrText.isNotBlank()) {
            Text(post.ocrText, style = AppType.Caption, color = AppColors.TextSecondary)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this post?", style = AppType.SectionTitle, color = AppColors.TextPrimary) },
            text = {
                Text(
                    text = "Its signal and screenshots are removed too. This cannot be undone.",
                    style = AppType.Body,
                    color = AppColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        app.scope.launch { app.pipeline.deletePost(app, post.id) }
                    },
                ) {
                    Text("Delete", style = AppType.Label, color = AppColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", style = AppType.Label, color = AppColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun PostHeader(
    post: Post,
    account: Account?,
    showOcr: Boolean,
    onToggleOcr: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val authorDiffers = !post.author.equals(post.handle, ignoreCase = true)
    val name = if (authorDiffers) {
        "@${post.author}"
    } else {
        account?.displayName?.takeIf { it.isNotBlank() } ?: "@${post.handle}"
    }
    val verified = !authorDiffers && account?.verified == true
    val clock = remember(post.timeMs) { System.currentTimeMillis() }
    val age = clock - post.timeMs
    val time = if (age < SevenDaysMs) {
        fmtAge(age)
    } else {
        SimpleDateFormat("MMM d", Locale.US).format(Date(post.timeMs))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            handle = post.handle,
            displayName = account?.displayName,
            avatarPath = account?.avatarPath,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
            ) {
                Text(
                    text = name,
                    style = AppType.Label,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (verified) {
                    VerifiedMark()
                }
            }
            Text(
                text = "@${post.handle} · X · $time",
                style = AppType.Caption.tabular(),
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (authorDiffers) {
                Text(
                    text = "shared under @${post.handle}",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
            }
        }
        if (!isManualId(post.id)) {
            OpenOnX(post)
        }
        Box {
            IconButton(
                onClick = { onMenuOpenChange(true) },
                modifier = Modifier.size(AppSize.TouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More actions",
                    tint = AppColors.TextSecondary,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                if (post.ocrText.isNotBlank()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (showOcr) {
                                    "Hide text read from images"
                                } else {
                                    "Show text read from images"
                                },
                                style = AppType.Label,
                                color = AppColors.TextPrimary,
                            )
                        },
                        onClick = {
                            onToggleOcr()
                            onMenuOpenChange(false)
                        },
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = {
                        Text("Delete post", style = AppType.Label, color = AppColors.Danger)
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onRequestDelete()
                    },
                )
            }
        }
    }
}

/** Opens the original post in the X app or browser; silently ignores a missing handler. */
@Composable
private fun OpenOnX(post: Post) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .heightIn(min = AppSize.TouchTarget)
            .clip(AppShape.Small)
            .clickable(role = Role.Button) {
                try {
                    uriHandler.openUri(postUrl(post))
                } catch (e: Exception) {
                }
            }
            .padding(horizontal = AppSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        Icon(
            imageVector = AppIcons.ExternalLink,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = AppColors.TextSecondary,
        )
        Text("Open on X", style = AppType.Label, color = AppColors.TextSecondary)
    }
}

/** Anything younger than a week shows a relative age; older posts show a calendar date. */
private const val SevenDaysMs = 7L * 24L * 60L * 60L * 1000L

@Composable
private fun PostStatus(post: Post) {
    when (post.status) {
        "NEW" -> StatusChip(
            text = "Waiting for interpretation",
            tone = ChipTone.WARNING,
            showProgress = true,
        )

        "ERROR" -> StatusChip(
            text = if (post.error.isNullOrBlank()) "Error" else "Error: ${post.error}",
            tone = ChipTone.ERROR,
        )

        "SKIPPED" -> StatusChip(text = "Skipped", tone = ChipTone.NEUTRAL)
        else -> if (!post.error.isNullOrBlank()) {
            StatusChip(text = post.error.orEmpty(), tone = ChipTone.WARNING)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthorView(signal: Signal) {
    val conviction = signal.conviction.coerceIn(0, 3)
    val targets = parseLevels(signal.targets)
    val support = parseLevels(signal.support)
    val resistance = parseLevels(signal.resistance)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShape.Small)
            .background(AppColors.Canvas)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        if (signal.type == "noise") {
            Text("Not about BTC: ignored", style = AppType.Body, color = AppColors.TextSecondary)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
                verticalArrangement = Arrangement.spacedBy(AppSpace.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SentimentBadge(direction = directionOf(signal.direction))
                Text(
                    text = signal.type.replace('_', ' '),
                    style = AppType.Label,
                    color = AppColors.TextSecondary,
                )
                Text(
                    text = signal.timeframe,
                    style = AppType.Label,
                    color = AppColors.TextSecondary,
                )
                Text(
                    text = "●".repeat(conviction) + "○".repeat(3 - conviction),
                    style = AppType.Label,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.semantics {
                        contentDescription = "Conviction $conviction of 3"
                    },
                )
                val outcome = signal.outcome
                if (outcome != null) {
                    Text(
                        text = "Result: ${outcome.lowercase()}",
                        style = AppType.Label,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            if (signal.entry != null) LevelRow("Entry", fmtPrice(signal.entry))
            if (signal.stop != null) LevelRow("Stop", fmtPrice(signal.stop))
            if (targets.isNotEmpty()) {
                LevelRow("Targets", targets.joinToString(", ") { fmtPrice(it) })
            }
            if (support.isNotEmpty()) {
                LevelRow("Support", support.joinToString(", ") { fmtPrice(it) })
            }
            if (resistance.isNotEmpty()) {
                LevelRow("Resistance", resistance.joinToString(", ") { fmtPrice(it) })
            }
            if (signal.evidence.isNotBlank()) {
                Text(
                    text = "“${signal.evidence}”",
                    style = AppType.Body,
                    color = AppColors.TextSecondary,
                    fontStyle = FontStyle.Italic,
                )
            }
            Text(
                text = "The author's stated view, extracted by DeepSeek. Not a prediction.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
    }
}

/** One labelled price level, with the label on a fixed column so values line up. */
@Composable
private fun LevelRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = AppType.Caption,
            color = AppColors.TextSecondary,
        )
        Text(text = value, style = AppType.Body.tabular(), color = AppColors.TextPrimary)
    }
}

@Composable
private fun PostMedia(
    images: List<String>,
    onOpenMedia: (images: List<String>, index: Int) -> Unit,
) {
    if (images.isEmpty()) return

    if (images.size == 1) {
        SubcomposeAsyncImage(
            model = images.first(),
            contentDescription = "Post image 1 of 1, open full screen",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = AppSize.MediaMaxHeight)
                .clip(AppShape.Small)
                .border(AppSize.Border, AppColors.Border, AppShape.Small)
                .clickable(role = Role.Button) { onOpenMedia(images, 0) },
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(AppColors.Placeholder),
                )
            },
            error = { ImageUnavailable(reason = "The file could not be loaded") },
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        images.forEachIndexed { index, image ->
            SubcomposeAsyncImage(
                model = image,
                contentDescription = "Post image ${index + 1} of ${images.size}, open full screen",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(AppSize.MediaMaxHeight * 0.6f)
                    .widthIn(max = AppSize.ReadingWidth)
                    .clip(AppShape.Small)
                    .border(AppSize.Border, AppColors.Border, AppShape.Small)
                    .clickable(role = Role.Button) { onOpenMedia(images, index) },
                loading = {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(AppSize.MediaMaxHeight * 0.6f)
                            .background(AppColors.Placeholder),
                    )
                },
                error = {
                    ImageUnavailable(
                        modifier = Modifier.width(200.dp),
                        reason = "The file could not be loaded",
                    )
                },
            )
        }
    }
}
