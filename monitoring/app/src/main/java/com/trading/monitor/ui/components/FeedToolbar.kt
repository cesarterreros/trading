package com.trading.monitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trading.monitor.FeedSection
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.focusRing

/** The bar above the posts: whose feed this is, view controls, and the Add post action. */
@Composable
fun FeedToolbar(
    selectedSection: FeedSection?,
    showSelector: Boolean,
    onOpenSelector: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onAddPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.m, vertical = AppSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        FeedContext(
            selectedSection = selectedSection,
            showSelector = showSelector,
            onOpenSelector = onOpenSelector,
            modifier = Modifier.weight(1f),
        )
        if (selectedSection == null) {
            ViewControls(onExpandAll = onExpandAll, onCollapseAll = onCollapseAll)
        }
        AddPostButton(onAddPost = onAddPost)
    }
}

@Composable
private fun FeedContext(
    selectedSection: FeedSection?,
    showSelector: Boolean,
    onOpenSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val section = selectedSection
    val rowModifier = if (showSelector) {
        modifier
            .heightIn(min = AppSize.TouchTarget)
            .clip(AppShape.Small)
            .focusRing()
            .clickable(role = Role.Button, onClickLabel = "Choose account", onClick = onOpenSelector)
            .padding(horizontal = AppSpace.s)
    } else {
        modifier.padding(horizontal = AppSpace.s)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        if (section == null) {
            Text(
                text = "All accounts",
                style = AppType.SectionTitle,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            Avatar(
                handle = section.handle,
                displayName = section.displayName,
                avatarPath = section.avatarPath,
                size = AppSize.AvatarSmall,
            )
            Text(
                text = if (section.displayName.isNullOrBlank()) {
                    "@${section.handle}"
                } else {
                    section.displayName.orEmpty()
                },
                style = AppType.SectionTitle,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (section.verified) {
                VerifiedMark(size = 16.dp)
            }
            SentimentBadge(direction = section.latestStance)
        }
        if (showSelector) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ViewControls(onExpandAll: () -> Unit, onCollapseAll: () -> Unit) {
    TextButton(
        onClick = onExpandAll,
        modifier = Modifier.heightIn(min = AppSize.TouchTarget),
    ) {
        Text(text = "Expand all", style = AppType.Label, color = AppColors.TextSecondary)
    }
    TextButton(
        onClick = onCollapseAll,
        modifier = Modifier.heightIn(min = AppSize.TouchTarget),
    ) {
        Text(text = "Collapse all", style = AppType.Label, color = AppColors.TextSecondary)
    }
}

@Composable
private fun AddPostButton(onAddPost: () -> Unit) {
    Button(
        onClick = onAddPost,
        shape = AppShape.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary,
            contentColor = AppColors.OnPrimary,
        ),
        modifier = Modifier.heightIn(min = AppSize.TouchTarget),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(AppSpace.xs))
        Text(text = "Add post", style = AppType.Label)
    }
}
