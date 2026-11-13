package com.trading.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trading.monitor.App
import com.trading.monitor.computeMatrix
import com.trading.monitor.market.MarketState
import com.trading.monitor.matrix.AccountRow
import com.trading.monitor.matrix.DecisionMatrix
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.Verdict
import com.trading.monitor.ui.components.PlaceholderBar
import com.trading.monitor.ui.components.SentimentBadge
import com.trading.monitor.ui.components.WarningBanner
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import com.trading.monitor.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun f(format: String, vararg args: Any?): String = String.format(Locale.US, format, *args)

private fun verdictTextColor(v: Verdict): Color = when (v) {
    Verdict.STRONG_BUY, Verdict.BUY -> AppColors.BullishText
    Verdict.STRONG_SELL, Verdict.SELL -> AppColors.BearishText
    Verdict.WAIT -> AppColors.NeutralText
}

private fun verdictBgColor(v: Verdict): Color = when (v) {
    Verdict.STRONG_BUY, Verdict.BUY -> AppColors.BullishBg
    Verdict.STRONG_SELL, Verdict.SELL -> AppColors.BearishBg
    Verdict.WAIT -> AppColors.NeutralBg
}

/** Decision matrix: verdict, market snapshot, per-account signals, level zones and pipeline status. */
@Composable
fun MatrixScreen(app: App) {
    val accounts by remember { app.dao.accounts() }.collectAsState(initial = emptyList())
    val signals by remember { app.dao.signals() }.collectAsState(initial = emptyList())
    val market by app.market.state.collectAsState()
    val status by app.pipeline.status.collectAsState()
    val scope = rememberCoroutineScope()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val matrix = remember(accounts, signals, market, now) {
        computeMatrix(accounts, signals, market, app.settings.matrixConfig(), now)
    }
    val missingKey = remember { app.settings.apiKey.isBlank() }
    val refresh: () -> Unit = {
        app.pollNow.tryEmit(Unit)
        scope.launch { try { app.market.refresh(); app.pipeline.processPending() } catch (e: Exception) { } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpace.m),
    ) {
        if (missingKey) {
            WarningBanner("Add your DeepSeek API key so posts can be interpreted. Open Settings to add it.")
        }
        VerdictCard(matrix)
        MarketCard(market, refresh)
        AccountsCard(matrix)
        ZonesCard(matrix)
        Column(verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            Text(status, style = AppType.Caption, color = AppColors.TextSecondary)
            Text(
                "Decision aid only. Opinions from X accounts have no proven edge; check each account's hit rate in the Accounts tab.",
                style = AppType.Caption,
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun VerdictCard(matrix: DecisionMatrix) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardBorder()
            .padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        Text(
            matrix.verdict.label.uppercase(),
            Modifier
                .background(verdictBgColor(matrix.verdict), AppShape.Pill)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = verdictTextColor(matrix.verdict),
            style = AppType.ScreenTitle,
        )
        Spacer(Modifier.height(AppSpace.xs))
        Text(f("Score %+.0f", matrix.score), style = AppType.Body.tabular(), color = AppColors.TextSecondary)
        val agreement = matrix.agreement?.let { f("%.0f%% agree", it * 100) } ?: "no directional signals"
        Text(
            "${matrix.bullCount} bullish · ${matrix.bearCount} bearish · $agreement",
            style = AppType.Body.tabular(),
            color = AppColors.TextSecondary,
        )
        matrix.medianRewardRisk?.let {
            Text(f("Median reward:risk %.1f", it), style = AppType.Body.tabular(), color = AppColors.TextSecondary)
        }
        if (matrix.reasons.isNotEmpty() || matrix.cautions.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.xs))
            matrix.reasons.forEach {
                Text("• $it", style = AppType.Body, color = AppColors.TextSecondary)
            }
            matrix.cautions.forEach {
                Text("• Caution: $it", style = AppType.Body, color = AppColors.WarningText)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketCard(market: MarketState, onRefresh: () -> Unit) {
    val loading = market.updatedMs == 0L && market.error == null
    val price = market.price
    Column(
        Modifier
            .fillMaxWidth()
            .cardBorder()
            .padding(AppSpace.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Market",
                Modifier.weight(1f),
                style = AppType.SectionTitle,
                color = AppColors.TextPrimary,
            )
            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = AppSize.TouchTarget)) {
                Text("Refresh", style = AppType.Label, color = AppColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(AppSpace.s))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpace.l),
            verticalArrangement = Arrangement.spacedBy(AppSpace.m),
        ) {
            Stat("BTC price", if (loading) null else fmtPrice(market.price), null)
            Stat(
                "24h change",
                if (loading) null else market.change24hPct?.let { f("%+.1f%%", it) } ?: "—",
                market.change24hPct?.let { it >= 0 },
            )
            Stat("50-day avg", if (loading) null else fmtPrice(market.sma50), price?.let { p -> market.sma50?.let { p >= it } })
            Stat("200-day avg", if (loading) null else fmtPrice(market.sma200), price?.let { p -> market.sma200?.let { p >= it } })
            Stat("Funding / 8h", if (loading) null else market.fundingRate?.let { f("%+.4f%%", it * 100) } ?: "—", null)
            Stat("Fear & Greed", if (loading) null else market.fearGreed?.let { "$it ${market.fearGreedLabel.orEmpty()}" } ?: "—", null)
        }
        if (market.updatedMs > 0) {
            Spacer(Modifier.height(AppSpace.s))
            Text(
                "Updated ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(market.updatedMs))}",
                style = AppType.Caption.tabular(),
                color = AppColors.TextSecondary,
            )
            if (System.currentTimeMillis() - market.updatedMs > 5 * 60_000) {
                Text("Market data may be out of date", style = AppType.Caption, color = AppColors.WarningText)
            }
        }
        market.error?.let {
            Spacer(Modifier.height(AppSpace.xs))
            Text("Market data problem: $it", style = AppType.Caption, color = AppColors.WarningText)
        }
    }
}

/** One labelled figure: the value is a placeholder bar while [value] is null; [above] colours it bullish or bearish. */
@Composable
private fun Stat(label: String, value: String?, above: Boolean?) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
        Text(label, style = AppType.Caption, color = AppColors.TextSecondary)
        if (value == null) {
            PlaceholderBar(width = 72.dp, height = 20.dp)
        } else {
            Text(
                value,
                style = AppType.SectionTitle.tabular(),
                color = when (above) {
                    true -> AppColors.BullishText
                    false -> AppColors.BearishText
                    null -> AppColors.TextPrimary
                },
            )
        }
    }
}

/** One table cell: flexible column [weight], optional [color], [bold], and right alignment for numbers. */
@Composable
fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color = Color.Unspecified,
    bold: Boolean = false,
    alignEnd: Boolean = false,
) {
    Text(
        text,
        Modifier.weight(weight).padding(horizontal = AppSpace.xs),
        color = color,
        fontWeight = if (bold) FontWeight.Bold else null,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        style = AppType.Body.tabular(),
    )
}

/** One table header cell, styled as a caption. */
@Composable
private fun RowScope.HeadCell(text: String, weight: Float, alignEnd: Boolean = false) {
    Text(
        text,
        Modifier.weight(weight).padding(horizontal = AppSpace.xs),
        color = AppColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        style = AppType.Caption,
    )
}

/** Stance column: a sentiment pill when the row has a direction, otherwise quiet text. */
@Composable
private fun RowScope.StanceCell(direction: Direction?, weight: Float, fallback: String, conviction: Int = 0) {
    Row(
        Modifier.weight(weight).padding(horizontal = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
    ) {
        if (direction == null) {
            Text(
                fallback,
                style = AppType.Body.tabular(),
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            SentimentBadge(direction)
            val n = conviction.coerceIn(1, 3)
            Text(
                "●".repeat(n) + "○".repeat(3 - n),
                Modifier.semantics { contentDescription = "Conviction $n of 3" },
                style = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Canvas)
            .padding(horizontal = AppSpace.s, vertical = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadCell("Account", 2.4f)
        HeadCell("Stance", 2f)
        HeadCell("Age", 1.6f, alignEnd = true)
        HeadCell("Entry", 1.1f, alignEnd = true)
        HeadCell("Stop", 1.1f, alignEnd = true)
        HeadCell("Targets", 2f)
        HeadCell("Nearest level", 1.6f)
        HeadCell("R:R", 0.7f, alignEnd = true)
        HeadCell("Wt", 0.8f, alignEnd = true)
        HeadCell("Hit rate", 1.1f, alignEnd = true)
        HeadCell("Score", 0.8f, alignEnd = true)
    }
}

@Composable
private fun MatrixRow(row: AccountRow) {
    val s = row.signal
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = AppSpace.s, vertical = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell("@${row.handle}", 2.4f, AppColors.TextPrimary, bold = true)
        if (s == null) {
            StanceCell(null, 2f, if (row.ageMs == null) "No signal" else "Stale")
            Cell(fmtAge(row.ageMs), 1.6f, AppColors.TextSecondary, alignEnd = true)
            Cell("—", 1.1f, AppColors.TextSecondary, alignEnd = true)
            Cell("—", 1.1f, AppColors.TextSecondary, alignEnd = true)
            Cell("—", 2f, AppColors.TextSecondary)
            Cell("—", 1.6f, AppColors.TextSecondary)
            Cell("—", 0.7f, AppColors.TextSecondary, alignEnd = true)
        } else {
            StanceCell(s.direction, 2f, "", s.conviction)
            Cell("${fmtAge(row.ageMs)} · ${s.timeframe.name.lowercase()}", 1.6f, AppColors.TextPrimary, alignEnd = true)
            Cell(fmtPrice(s.entry), 1.1f, AppColors.TextPrimary, alignEnd = true)
            Cell(fmtPrice(s.stop), 1.1f, AppColors.TextPrimary, alignEnd = true)
            Cell(if (s.targets.isEmpty()) "—" else s.targets.joinToString(", ") { fmtPrice(it) }, 2f, AppColors.TextPrimary)
            Cell(
                if (row.nearestLevel != null && row.nearestLevelPct != null) "${fmtPrice(row.nearestLevel)} (${f("%+.1f%%", row.nearestLevelPct)})" else "—",
                1.6f,
                AppColors.TextPrimary,
            )
            Cell(row.rewardRisk?.let { f("%.1f", it) } ?: "—", 0.7f, AppColors.TextPrimary, alignEnd = true)
        }
        Cell(f("%.2f", row.weight), 0.8f, AppColors.TextPrimary, alignEnd = true)
        Cell(row.hitRate?.let { f("%.0f%% of %d", it * 100, row.resolved) } ?: "—", 1.1f, AppColors.TextPrimary, alignEnd = true)
        Cell(f("%+.2f", row.score), 0.8f, AppColors.TextPrimary, bold = true, alignEnd = true)
    }
}

/** Card holding the accounts matrix table, which scrolls sideways on narrow screens. */
@Composable
private fun AccountsCard(matrix: DecisionMatrix) {
    Column(Modifier.fillMaxWidth().cardBorder()) {
        Text(
            "Accounts",
            Modifier.padding(horizontal = AppSpace.m, vertical = AppSpace.s),
            style = AppType.SectionTitle,
            color = AppColors.TextPrimary,
        )
        ScrollableTable {
            HeaderRow()
            HorizontalDivider(color = AppColors.Border)
            matrix.rows.forEach { row ->
                MatrixRow(row)
                HorizontalDivider(color = AppColors.Border)
            }
        }
        if (matrix.rows.isEmpty()) {
            Text(
                "No accounts yet. Add them in the Accounts tab.",
                Modifier.padding(horizontal = AppSpace.m, vertical = AppSpace.s),
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(AppSpace.s))
    }
}

/** Card holding the clustered level zones, in the same table style as the accounts matrix. */
@Composable
private fun ZonesCard(matrix: DecisionMatrix) {
    Column(Modifier.fillMaxWidth().cardBorder()) {
        Text(
            "Level zones",
            Modifier.padding(horizontal = AppSpace.m, vertical = AppSpace.s),
            style = AppType.SectionTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            "Levels from different accounts within 0.5%",
            Modifier.padding(horizontal = AppSpace.m),
            style = AppType.Caption,
            color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(AppSpace.s))
        ScrollableTable {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Canvas)
                    .padding(horizontal = AppSpace.s, vertical = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeadCell("Range", 2f)
                HeadCell("Distance", 1f, alignEnd = true)
                HeadCell("Kinds", 2f)
                HeadCell("Accounts", 3f)
            }
            HorizontalDivider(color = AppColors.Border)
            matrix.zones.take(12).forEach { zone ->
                val range =
                    if (zone.high - zone.low < 1) fmtPrice(zone.center)
                    else "${fmtPrice(zone.low)} – ${fmtPrice(zone.high)}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(horizontal = AppSpace.s, vertical = AppSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell(range, 2f, AppColors.TextPrimary, bold = zone.accounts.size > 1)
                    Cell(zone.distancePct?.let { f("%+.1f%%", it) } ?: "—", 1f, AppColors.TextPrimary, alignEnd = true)
                    Cell(zone.kinds.joinToString(", "), 2f, AppColors.TextPrimary)
                    Cell(zone.accounts.joinToString(", ") { "@$it" }, 3f, AppColors.TextSecondary)
                }
                HorizontalDivider(color = AppColors.Border)
            }
        }
        if (matrix.zones.isEmpty()) {
            Text(
                "No levels extracted yet.",
                Modifier.padding(horizontal = AppSpace.m, vertical = AppSpace.s),
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(AppSpace.s))
    }
}

/** Lays [content] out at least 1150 dp wide, scrolling sideways when the card is narrower than that. */
@Composable
private fun ScrollableTable(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableWidth = maxOf(1150.dp, maxWidth)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.widthIn(min = 1150.dp).width(tableWidth)) { content() }
        }
    }
}
