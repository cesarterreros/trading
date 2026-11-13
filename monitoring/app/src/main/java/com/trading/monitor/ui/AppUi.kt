package com.trading.monitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.trading.monitor.App
import com.trading.monitor.ui.components.MediaViewer
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppTheme
import com.trading.monitor.ui.theme.AppType
import java.util.Locale


fun fmtPrice(v: Double?): String = v?.let { String.format(Locale.US, "%,.0f", it) } ?: "—"

fun fmtAge(ms: Long?): String {
    if (ms == null) return "—"
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 48 * 60 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}

private val destinations = listOf(
    "Matrix" to Icons.Filled.Home,
    "Feed" to Icons.AutoMirrored.Filled.List,
    "Accounts" to Icons.Filled.Person,
    "Settings" to Icons.Filled.Settings,
)

/**
 * The app shell. The layout follows the width available to the app: a navigation rail from
 * 600 dp, a bottom bar below that, and a fixed accounts panel beside the feed from 1000 dp.
 */
@Composable
fun AppUi(app: App) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Images shown full screen, with the index to start at. Lives here so the viewer covers the navigation too.
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    AppTheme {
        Surface(Modifier.fillMaxSize(), color = AppColors.Canvas) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val wide = maxWidth >= AppSize.WideBreakpoint
                    val content: @Composable () -> Unit = {
                        when (tab) {
                            0 -> MatrixScreen(app)
                            1 -> FeedScreen(
                                app = app,
                                wide = wide,
                                onOpenMedia = { images, index -> viewer = images to index },
                                onOpenSettings = { tab = 3 },
                            )
                            2 -> AccountsScreen(app)
                            else -> SettingsScreen(app)
                        }
                    }
                    if (maxWidth >= AppSize.MediumBreakpoint) {
                        Row(Modifier.fillMaxSize()) {
                            NavigationRail(Modifier.width(AppSize.Rail), containerColor = AppColors.Surface) {
                                destinations.forEachIndexed { index, (label, icon) ->
                                    NavigationRailItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(label, style = AppType.Caption) },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = AppColors.Primary,
                                            selectedTextColor = AppColors.Primary,
                                            indicatorColor = AppColors.SelectedRow,
                                            unselectedIconColor = AppColors.TextSecondary,
                                            unselectedTextColor = AppColors.TextSecondary,
                                        ),
                                    )
                                }
                            }
                            VerticalDivider(color = AppColors.Border)
                            Box(Modifier.weight(1f).fillMaxHeight().padding(AppSpace.m)) { content() }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth()
                                    .padding(start = AppSpace.m, top = AppSpace.m, end = AppSpace.m),
                            ) { content() }
                            HorizontalDivider(color = AppColors.Border)
                            NavigationBar(containerColor = AppColors.Surface) {
                                destinations.forEachIndexed { index, (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(label, style = AppType.Caption) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AppColors.Primary,
                                            selectedTextColor = AppColors.Primary,
                                            indicatorColor = AppColors.SelectedRow,
                                            unselectedIconColor = AppColors.TextSecondary,
                                            unselectedTextColor = AppColors.TextSecondary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    if (app.draft.open) AddPostScreen(app)
                }
                viewer?.let { (images, index) -> MediaViewer(images, index, onClose = { viewer = null }) }
            }
        }
    }
}
