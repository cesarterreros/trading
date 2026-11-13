package com.trading.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType

/** The three semantic tones a small status chip can carry. */
enum class ChipTone { NEUTRAL, WARNING, ERROR }

/** A small rounded chip for short statuses such as "Loading" or "Stale", with an optional spinner. */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    val (content, container) = when (tone) {
        ChipTone.NEUTRAL -> AppColors.NeutralText to AppColors.NeutralBg
        ChipTone.WARNING -> AppColors.WarningText to AppColors.WarningBg
        ChipTone.ERROR -> AppColors.BearishText to AppColors.BearishBg
    }

    Row(
        modifier = modifier
            .clip(AppShape.Small)
            .background(container)
            .padding(horizontal = AppSpace.s, vertical = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = content,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = text,
            style = AppType.Label,
            color = content,
        )
    }
}

/** A full-width warning strip with an optional right-aligned action. */
@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .background(AppColors.WarningBg)
            .border(
                width = AppSize.Border,
                color = AppColors.WarningText.copy(alpha = BannerBorderAlpha),
                shape = AppShape.Card,
            )
            .padding(AppSpace.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        Text(
            text = text,
            style = AppType.Body,
            color = AppColors.WarningText,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = AppColors.WarningText),
            ) {
                Text(text = actionLabel, style = AppType.Label)
            }
        }
    }
}

/** A centred "nothing here yet" block with optional explanatory lines and a primary action. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(AppSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = AppType.SectionTitle,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            lines.forEach { line ->
                Spacer(modifier = Modifier.height(AppSpace.s))
                Text(
                    text = line,
                    style = AppType.Body,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(AppSpace.m))
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = AppSize.TouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.OnPrimary,
                    ),
                ) {
                    Text(text = actionLabel, style = AppType.Label, color = AppColors.OnPrimary)
                }
            }
        }
    }
}

/** A static grey bar that holds the place of a value while data loads, instead of showing zeros. */
@Composable
fun PlaceholderBar(
    width: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(AppShape.Small)
            .background(AppColors.Placeholder),
    )
}

/** A bordered block that explains why a picture or chart could not be loaded. */
@Composable
fun ImageUnavailable(modifier: Modifier = Modifier, reason: String? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(AppShape.Small)
            .background(AppColors.NeutralBg)
            .border(width = AppSize.Border, color = AppColors.Border, shape = AppShape.Small),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(AppSpace.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Image unavailable",
                style = AppType.Label,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (!reason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(AppSpace.xs))
                Text(
                    text = reason,
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Alpha applied to the warning banner's hairline border. */
private const val BannerBorderAlpha = 0.3f
