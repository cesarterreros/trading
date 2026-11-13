package com.trading.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trading.monitor.avatarColorIndex
import com.trading.monitor.initialOf
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import java.io.File

/**
 * A circular identity chip showing the account's picture when it has one, and otherwise the coloured
 * initial circle that also acts as the picture's fallback.
 */
@Composable
fun Avatar(
    handle: String,
    displayName: String?,
    avatarPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = AppSize.Avatar,
) {
    val context = LocalContext.current
    val palette = AppColors.AvatarPalette
    val background = palette[avatarColorIndex(handle, palette.size).coerceIn(0, palette.lastIndex)]
    val initial = initialOf(handle, displayName)
    val initialSize = with(LocalDensity.current) { (size * 0.42f).toSp() }

    Box(
        modifier = modifier
            .size(size)
            .clip(AppShape.Pill)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = AppColors.OnPrimary,
            fontSize = initialSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (avatarPath != null) {
            AsyncImage(
                model = File(context.filesDir, avatarPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(AppShape.Pill),
            )
        }
    }
}

/** The small check-in-a-circle badge that marks a verified account. */
@Composable
fun VerifiedMark(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(
        imageVector = AppIcons.Verified,
        contentDescription = "Verified",
        modifier = modifier
            .size(size)
            .clip(AppShape.Pill),
        tint = AppColors.Primary,
    )
}
