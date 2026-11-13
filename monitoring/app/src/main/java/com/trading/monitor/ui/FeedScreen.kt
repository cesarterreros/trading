package com.trading.monitor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trading.monitor.App
import com.trading.monitor.FeedSection
import com.trading.monitor.SectionState
import com.trading.monitor.SentimentFilter
import com.trading.monitor.db.Post
import com.trading.monitor.groupFeed
import com.trading.monitor.resolveSelectedAccount
import com.trading.monitor.ui.components.AccountSelectorSheet
import com.trading.monitor.ui.components.AccountsPanel
import com.trading.monitor.ui.components.Avatar
import com.trading.monitor.ui.components.EmptyState
import com.trading.monitor.ui.components.FeedToolbar
import com.trading.monitor.ui.components.PostCard
import com.trading.monitor.ui.components.SentimentBadge
import com.trading.monitor.ui.components.VerifiedMark
import com.trading.monitor.ui.components.WarningBanner
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import com.trading.monitor.ui.theme.focusRing
import com.trading.monitor.ui.theme.tabular

private const val PAGE = 20

/** Feed screen: the posts of one account, or every account with sticky section headers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    app: App,
    wide: Boolean,
    onOpenMedia: (images: List<String>, index: Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val accounts by remember { app.dao.accounts() }.collectAsState(initial = emptyList())
    val posts by remember { app.dao.recentPosts() }.collectAsState(initial = emptyList())
    val signals by remember { app.dao.signals() }.collectAsState(initial = emptyList())
    val byPost = remember(signals) { signals.associateBy { it.postId } }
    val accountsByHandle = remember(accounts) { accounts.associateBy { it.handle } }
    val sections = remember(accounts, posts, signals) { groupFeed(accounts, posts, signals) }

    var selectedPref by remember { mutableStateOf(app.settings.selectedFeedAccount) }
    // Display-only fallback: the lists are briefly empty at start, so it is never written back.
    val selected = resolveSelectedAccount(selectedPref, sections)
    fun select(handle: String?) {
        selectedPref = handle
        app.settings.selectedFeedAccount = handle
    }

    var query by rememberSaveable { mutableStateOf("") }
    var sentiment by remember {
        mutableStateOf(
            SentimentFilter.entries.firstOrNull {
                it.name.equals(app.settings.feedSentimentFilter, ignoreCase = true)
            } ?: SentimentFilter.ALL,
        )
    }
    fun setSentiment(value: SentimentFilter) {
        sentiment = value
        app.settings.feedSentimentFilter = value.name.lowercase()
    }

    var selectorOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(app.settings.expandedFeedAccounts) }
    val shown = remember { mutableStateMapOf<String, Int>() } // page size per handle, not persisted
    fun setExpanded(value: Set<String>) {
        expanded = value
        app.settings.expandedFeedAccounts = value
    }
    val apiKeyMissing = remember { app.settings.apiKey.isBlank() }
    val body: @Composable (Modifier, Boolean) -> Unit = { contentModifier, showSelector ->
        FeedBody(
            sections = sections,
            selected = selected,
            apiKeyMissing = apiKeyMissing,
            showSelector = showSelector,
            expanded = expanded,
            shown = shown,
            modifier = contentModifier,
            onOpenSettings = onOpenSettings,
            onOpenSelector = { selectorOpen = true },
            onExpandAll = { setExpanded(sections.map { it.handle }.toSet()) },
            onCollapseAll = { setExpanded(emptySet()) },
            onAddPost = { app.draft.open = true },
            onToggleSection = { handle ->
                setExpanded(if (handle in expanded) expanded - handle else expanded + handle)
            },
            onShowMore = { handle -> shown[handle] = (shown[handle] ?: PAGE) + PAGE },
            itemContent = { post ->
                PostCard(
                    app = app,
                    post = post,
                    signal = byPost[post.id],
                    account = accountsByHandle[post.handle],
                    onOpenMedia = onOpenMedia,
                )
            },
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AppSpace.m)) {
            AccountsPanel(
                sections = sections,
                selected = selected,
                onSelect = { select(it) },
                query = query,
                onQueryChange = { query = it },
                sentiment = sentiment,
                onSentimentChange = { setSentiment(it) },
                modifier = Modifier.width(AppSize.AccountsPanel).fillMaxHeight().cardBorder(),
            )
            body(Modifier.weight(1f).fillMaxHeight(), false)
        }
    } else {
        body(Modifier.fillMaxSize(), true)
        if (selectorOpen) {
            AccountSelectorSheet(
                sections = sections,
                selected = selected,
                onSelect = { select(it) },
                query = query,
                onQueryChange = { query = it },
                sentiment = sentiment,
                onSentimentChange = { setSentiment(it) },
                onDismiss = { selectorOpen = false },
            )
        }
    }
}

@Composable
private fun FeedBody(
    sections: List<FeedSection>,
    selected: String?,
    apiKeyMissing: Boolean,
    showSelector: Boolean,
    expanded: Set<String>,
    shown: Map<String, Int>,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenSelector: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onAddPost: () -> Unit,
    onToggleSection: (String) -> Unit,
    onShowMore: (String) -> Unit,
    itemContent: @Composable (Post) -> Unit,
) {
    Column(modifier) {
        if (apiKeyMissing) {
            WarningBanner(
                text = "Add your DeepSeek API key so posts can be interpreted.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )
            Spacer(Modifier.height(AppSpace.s))
        }
        val selectedSection = sections.firstOrNull { it.handle == selected }
        FeedToolbar(
            selectedSection = selectedSection,
            showSelector = showSelector,
            onOpenSelector = onOpenSelector,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            onAddPost = onAddPost,
        )
        when {
            sections.isEmpty() -> EmptyState(
                title = "No posts yet",
                modifier = Modifier.weight(1f),
                lines = listOf(
                    "1. Add your DeepSeek API key in Settings.",
                    "2. Share a post from the X app to BTC Signal Monitor, or press Add post.",
                    "3. Read the verdict on the Matrix screen.",
                ),
                actionLabel = "Add post",
                onAction = onAddPost,
            )
            selected != null -> {
                val section = sections.firstOrNull { it.handle == selected }
                if (section == null || section.posts.isEmpty()) {
                    EmptyState(
                        title = "No posts for @$selected yet",
                        modifier = Modifier.weight(1f),
                        actionLabel = "Add post",
                        onAction = onAddPost,
                    )
                } else {
                    AccountPostList(
                        posts = section.posts,
                        shown = shown[section.handle] ?: PAGE,
                        onShowMore = { onShowMore(section.handle) },
                        modifier = Modifier.weight(1f),
                        itemContent = itemContent,
                    )
                }
            }
            else -> AllAccountsList(
                sections = sections,
                expanded = expanded,
                shown = shown,
                modifier = Modifier.weight(1f),
                onToggleSection = onToggleSection,
                onShowMore = onShowMore,
                itemContent = itemContent,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllAccountsList(
    sections: List<FeedSection>,
    expanded: Set<String>,
    shown: Map<String, Int>,
    modifier: Modifier = Modifier,
    onToggleSection: (String) -> Unit,
    onShowMore: (String) -> Unit,
    itemContent: @Composable (Post) -> Unit,
) {
    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = AppSpace.s),
    ) {
        val now = System.currentTimeMillis()
        for (section in sections) {
            val handle = section.handle
            val isOpen = handle in expanded
            stickyHeader(key = "h:$handle") {
                SectionHeader(section, isOpen, now) { onToggleSection(handle) }
            }
            if (isOpen) {
                val limit = shown[handle] ?: PAGE
                val visible = section.posts.take(limit)
                items(visible, key = { it.id }) { post -> itemContent(post) }
                val older = section.posts.size - visible.size
                if (older > 0) {
                    item(key = "more:$handle") {
                        ShowMoreButton(older, { onShowMore(handle) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPostList(
    posts: List<Post>,
    shown: Int,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (Post) -> Unit,
) {
    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = AppSpace.s),
    ) {
        val visible = posts.take(shown)
        items(visible, key = { it.id }) { post -> itemContent(post) }
        val older = posts.size - visible.size
        if (older > 0) {
            item(key = "more") {
                ShowMoreButton(older, onShowMore, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: FeedSection,
    isOpen: Boolean,
    now: Long,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(AppColors.Canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusRing()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isOpen) "Collapse" else "Expand",
                    onClick = onToggle,
                )
                .padding(vertical = AppSpace.s, horizontal = AppSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
        ) {
            Icon(
                imageVector = if (isOpen) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = AppColors.TextSecondary,
            )
            Avatar(
                handle = section.handle,
                displayName = section.displayName,
                avatarPath = section.avatarPath,
                size = AppSize.AvatarSmall,
            )
            Text(
                text = section.displayName?.takeIf { it.isNotBlank() } ?: "@${section.handle}",
                style = AppType.Label,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (section.verified) VerifiedMark()
            when (section.state) {
                SectionState.PAUSED -> Text(
                    text = "paused",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
                SectionState.REMOVED -> Text(
                    text = "removed",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                )
                SectionState.ACTIVE -> {}
            }
            SentimentBadge(section.latestStance)
            Spacer(Modifier.weight(1f))
            Text(
                text = when (section.posts.size) {
                    0 -> "no posts yet"
                    1 -> "1 post"
                    else -> "${section.posts.size} posts"
                },
                style = AppType.Caption.tabular(),
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
            if (section.waiting > 0) {
                Text(
                    text = "${section.waiting} waiting",
                    style = AppType.Caption.tabular(),
                    color = AppColors.WarningText,
                    maxLines = 1,
                )
            }
            section.latestPostMs?.let { latest ->
                Text(
                    text = fmtAge(now - latest),
                    style = AppType.Caption.tabular(),
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = AppColors.Border)
    }
}

@Composable
private fun ShowMoreButton(older: Int, onShowMore: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onShowMore, modifier = modifier) {
        Text(
            text = "Show $PAGE more ($older older)",
            style = AppType.Label,
            color = AppColors.Primary,
        )
    }
}
