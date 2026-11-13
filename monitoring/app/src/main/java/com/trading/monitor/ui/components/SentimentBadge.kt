package com.trading.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trading.monitor.matrix.Direction
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType

/** The text colour, background colour, word and icon that represent one sentiment. */
data class SentimentStyle(
    val text: Color,
    val background: Color,
    val label: String,
    val icon: ImageVector,
)

/** Maps a [Direction] to the colours, word and icon used to show it. */
fun sentimentStyle(direction: Direction): SentimentStyle = when (direction) {
    Direction.BULLISH -> SentimentStyle(
        text = AppColors.BullishText,
        background = AppColors.BullishBg,
        label = "Bullish",
        icon = AppIcons.ArrowUpRight,
    )

    Direction.BEARISH -> SentimentStyle(
        text = AppColors.BearishText,
        background = AppColors.BearishBg,
        label = "Bearish",
        icon = AppIcons.ArrowDownRight,
    )

    Direction.NEUTRAL -> SentimentStyle(
        text = AppColors.NeutralText,
        background = AppColors.NeutralBg,
        label = "Neutral",
        icon = AppIcons.Dash,
    )
}

/**
 * Renders a sentiment pill that always pairs an icon with its word, so the meaning never depends on
 * colour alone; renders nothing when [direction] is null.
 */
@Composable
fun SentimentBadge(
    direction: Direction?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    if (direction == null) return
    val style = sentimentStyle(direction)
    val iconSize = if (large) 20.dp else 14.dp
    val horizontal = if (large) AppSpace.s + AppSpace.xs else AppSpace.s
    val vertical = if (large) AppSpace.s else AppSpace.xs
    val gap = AppSpace.xs
    val textStyle = if (large) AppType.SectionTitle else AppType.Label

    Row(
        modifier = modifier
            .clip(AppShape.Pill)
            .background(style.background)
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = style.text,
        )
        Text(
            text = style.label,
            style = textStyle,
            color = style.text,
        )
    }
}
