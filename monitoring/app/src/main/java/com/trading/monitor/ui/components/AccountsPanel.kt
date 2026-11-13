package com.trading.monitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trading.monitor.FeedSection
import com.trading.monitor.SectionState
import com.trading.monitor.SentimentFilter
import com.trading.monitor.filterSections
import com.trading.monitor.ui.fmtAge
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.focusRing
import com.trading.monitor.ui.theme.tabular

/** Search, sentiment filter and the list of accounts; selecting a row picks whose posts the feed shows. */
@Composable
fun AccountsPanel(
    sections: List<FeedSection>,
    selected: String?,
    onSelect: (String?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sentiment: SentimentFilter,
    onSentimentChange: (SentimentFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PanelSearchField(query = query, onQueryChange = onQueryChange)
        SentimentFilterChips(sentiment = sentiment, onSentimentChange = onSentimentChange)
        HorizontalDivider(color = AppColors.Border)
        AccountsList(
            sections = sections,
            selected = selected,
            onSelect = onSelect,
            query = query,
            onQueryChange = onQueryChange,
            sentiment = sentiment,
            onSentimentChange = onSentimentChange,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The same accounts panel inside a bottom sheet, for widths without the fixed panel. Selecting a row closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelectorSheet(
    sections: List<FeedSection>,
    selected: String?,
    onSelect: (String?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sentiment: SentimentFilter,
    onSentimentChange: (SentimentFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        AccountsPanel(
            sections = sections,
            selected = selected,
            onSelect = { handle ->
                onSelect(handle)
                onDismiss()
            },
            query = query,
            onQueryChange = onQueryChange,
            sentiment = sentiment,
            onSentimentChange = onSentimentChange,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
        )
    }
}

@Composable
private fun PanelSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(text = "Search accounts", style = AppType.Body) },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = AppColors.TextSecondary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = AppColors.TextSecondary,
                    )
                }
            }
        },
        textStyle = AppType.Body,
        shape = AppShape.Small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpace.m, start = AppSpace.m, end = AppSpace.m),
    )
}

private val sentimentOptions: List<Pair<String, SentimentFilter>> = listOf(
    "All" to SentimentFilter.ALL,
    "Bullish" to SentimentFilter.BULLISH,
    "Bearish" to SentimentFilter.BEARISH,
    "Neutral" to SentimentFilter.NEUTRAL,
)

@Composable
private fun SentimentFilterChips(
    sentiment: SentimentFilter,
    onSentimentChange: (SentimentFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpace.m, vertical = AppSpace.s),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sentimentOptions.forEach { (label, value) ->
            FilterChip(
                selected = sentiment == value,
                onClick = { onSentimentChange(value) },
                label = { Text(text = label, style = AppType.Label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.SelectedRow,
                    selectedLabelColor = AppColors.Primary,
                ),
            )
        }
    }
}

@Composable
private fun AccountsList(
    sections: List<FeedSection>,
    selected: String?,
    onSelect: (String?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sentiment: SentimentFilter,
    onSentimentChange: (SentimentFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filterSections(sections, query, sentiment)
    val totalPosts = sections.sumOf { it.posts.size }
    val filtersActive = query.isNotBlank() || sentiment != SentimentFilter.ALL
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "all-accounts") {
            AllAccountsRow(
                isSelected = selected == null,
                totalPosts = totalPosts,
                onClick = { onSelect(null) },
            )
        }
        items(items = filtered, key = { it.handle }) { section ->
            AccountRow(
                section = section,
                isSelected = selected == section.handle,
                onClick = { onSelect(section.handle) },
            )
        }
        if (filtered.isEmpty() && filtersActive) {
            item(key = "no-matches") {
                NoMatchesItem(
                    onClearFilters = {
                        onQueryChange("")
                        onSentimentChange(SentimentFilter.ALL)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionRow(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (isSelected) AppColors.SelectedRow else Color.Transparent)
            .drawBehind {
                if (isSelected) {
                    drawRect(
                        color = AppColors.Primary,
                        size = Size(AppSize.SelectedBar.toPx(), size.height),
                    )
                }
            }
            .focusRing()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = isSelected }
            .padding(horizontal = AppSpace.m, vertical = AppSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun AllAccountsRow(isSelected: Boolean, totalPosts: Int, onClick: () -> Unit) {
    SelectionRow(isSelected = isSelected, onClick = onClick) {
        Text(
            text = "All accounts",
            style = AppType.Label.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$totalPosts",
            style = AppType.Caption.tabular(),
            color = AppColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountRow(section: FeedSection, isSelected: Boolean, onClick: () -> Unit) {
    val hasDisplayName = !section.displayName.isNullOrBlank()
    val name = if (hasDisplayName) section.displayName.orEmpty() else "@${section.handle}"
    SelectionRow(isSelected = isSelected, onClick = onClick) {
        Avatar(
            handle = section.handle,
            displayName = section.displayName,
            avatarPath = section.avatarPath,
            size = AppSize.AvatarSmall,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = AppType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (section.verified) {
                    VerifiedMark(
                        modifier = Modifier.padding(start = AppSpace.xs),
                        size = 14.dp,
                    )
                }
            }
            Text(
                text = metaLine(section, showHandle = hasDisplayName),
                style = AppType.Caption.tabular(),
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SentimentBadge(direction = section.latestStance)
        val latestPostMs = section.latestPostMs
        if (latestPostMs != null) {
            Text(
                text = fmtAge(System.currentTimeMillis() - latestPostMs),
                style = AppType.Caption.tabular(),
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NoMatchesItem(onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpace.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No accounts match",
            style = AppType.Body,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onClearFilters) {
            Text(text = "Clear filters", style = AppType.Label, color = AppColors.Primary)
        }
    }
}

private fun metaLine(section: FeedSection, showHandle: Boolean): String {
    val parts = mutableListOf<String>()
    if (showHandle) parts += "@${section.handle}"
    when (section.state) {
        SectionState.PAUSED -> parts += "paused"
        SectionState.REMOVED -> parts += "removed"
        SectionState.ACTIVE -> Unit
    }
    val posts = section.posts.size
    parts += when (posts) {
        0 -> "no posts"
        1 -> "1 post"
        else -> "$posts posts"
    }
    if (section.waiting > 0) parts += "${section.waiting} waiting"
    return parts.joinToString(" · ")
}
