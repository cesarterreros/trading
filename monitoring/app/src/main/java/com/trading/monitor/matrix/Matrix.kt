package com.trading.monitor.matrix

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** Decision-matrix engine. Pure Kotlin: no Android imports, so it runs in plain unit tests. */

enum class Direction { BULLISH, BEARISH, NEUTRAL }

enum class Timeframe(val halfLifeDays: Double, val horizonDays: Int) {
    SCALP(1.0, 3),
    SWING(5.0, 21),
    POSITION(20.0, 90),
}

enum class Verdict(val label: String) {
    STRONG_BUY("Strong buy"),
    BUY("Buy"),
    WAIT("Wait"),
    SELL("Sell"),
    STRONG_SELL("Strong sell"),
}

data class SignalInput(
    val account: String,
    val timeMs: Long,
    val direction: Direction,
    val conviction: Int,
    val timeframe: Timeframe,
    val entry: Double? = null,
    val stop: Double? = null,
    val targets: List<Double> = emptyList(),
    val support: List<Double> = emptyList(),
    val resistance: List<Double> = emptyList(),
)

data class AccountInput(
    val handle: String,
    val manualWeight: Double = 1.0,
    val wins: Int = 0,
    val losses: Int = 0,
)

data class MarketInput(
    val price: Double,
    val sma50: Double? = null,
    val sma200: Double? = null,
    val fundingRate: Double? = null,
    val fearGreed: Int? = null,
)

data class MatrixConfig(
    val minAgreeingAccounts: Int = 2,
    val buyThreshold: Double = 25.0,
    val strongThreshold: Double = 60.0,
    val minRewardRisk: Double = 2.0,
    val clusterPct: Double = 0.5,
    val minHitSample: Int = 10,
)

data class AccountRow(
    val handle: String,
    /** Latest signal if it is still fresh enough to count, else null. */
    val signal: SignalInput?,
    val ageMs: Long?,
    val weight: Double,
    val hitRate: Double?,
    val resolved: Int,
    val decay: Double,
    val score: Double,
    val rewardRisk: Double?,
    val nearestLevel: Double?,
    val nearestLevelPct: Double?,
)

data class Zone(
    val low: Double,
    val high: Double,
    val center: Double,
    val accounts: Set<String>,
    val kinds: Set<String>,
    /** Signed distance from the live price in percent; null when the price is unknown. */
    val distancePct: Double?,
)

data class DecisionMatrix(
    val rows: List<AccountRow>,
    val score: Double,
    val agreement: Double?,
    val bullCount: Int,
    val bearCount: Int,
    val medianRewardRisk: Double?,
    val verdict: Verdict,
    val reasons: List<String>,
    val cautions: List<String>,
    val zones: List<Zone>,
)

/** A signal stops counting once it has decayed below this (about 3.3 half-lives). */
const val MIN_DECAY = 0.1
private const val DAY_MS = 86_400_000.0

fun decay(ageMs: Long, timeframe: Timeframe): Double =
    0.5.pow(max(0L, ageMs) / DAY_MS / timeframe.halfLifeDays)

fun accountWeight(account: AccountInput, cfg: MatrixConfig): Double {
    val resolved = account.wins + account.losses
    val factor = if (resolved >= cfg.minHitSample) {
        (account.wins.toDouble() / resolved / 0.5).coerceIn(0.25, 1.5)
    } else 1.0
    return account.manualWeight * factor
}

/** Nearest target beyond the reference price in the trade's direction. */
fun firstTarget(direction: Direction, reference: Double, targets: List<Double>): Double? = when (direction) {
    Direction.BULLISH -> targets.filter { it > reference }.minOrNull()
    Direction.BEARISH -> targets.filter { it < reference }.maxOrNull()
    Direction.NEUTRAL -> null
}

fun rewardRisk(signal: SignalInput, price: Double?): Double? {
    val entry = signal.entry ?: price ?: return null
    val stop = signal.stop ?: return null
    val target = firstTarget(signal.direction, entry, signal.targets) ?: return null
    val risk = if (signal.direction == Direction.BULLISH) entry - stop else stop - entry
    if (risk <= 0) return null
    return abs(target - entry) / risk
}

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

private fun allLevels(s: SignalInput): List<Pair<Double, String>> = buildList {
    s.entry?.let { add(it to "entry") }
    s.stop?.let { add(it to "stop") }
    s.targets.forEach { add(it to "target") }
    s.support.forEach { add(it to "support") }
    s.resistance.forEach { add(it to "resistance") }
}

fun buildZones(rows: List<AccountRow>, price: Double?, clusterPct: Double): List<Zone> {
    data class Level(val price: Double, val account: String, val kind: String)

    val levels = rows.flatMap { r ->
        val s = r.signal ?: return@flatMap emptyList<Level>()
        allLevels(s).map { Level(it.first, r.handle, it.second) }
    }.sortedBy { it.price }

    val clusters = mutableListOf<MutableList<Level>>()
    for (level in levels) {
        val current = clusters.lastOrNull()
        val mean = current?.map { it.price }?.average()
        if (current != null && mean != null && (level.price - mean) / mean * 100 <= clusterPct) {
            current.add(level)
        } else {
            clusters.add(mutableListOf(level))
        }
    }
    return clusters.map { c ->
        val center = c.map { it.price }.average()
        Zone(
            low = c.first().price,
            high = c.last().price,
            center = center,
            accounts = c.map { it.account }.toSet(),
            kinds = c.map { it.kind }.toSet(),
            distancePct = price?.let { (center / it - 1) * 100 },
        )
    }.sortedWith(compareByDescending<Zone> { it.accounts.size }.thenBy { abs(it.distancePct ?: 0.0) })
}

fun buildMatrix(
    accounts: List<AccountInput>,
    signals: List<SignalInput>,
    market: MarketInput?,
    cfg: MatrixConfig,
    nowMs: Long,
): DecisionMatrix {
    val price = market?.price

    val rows = accounts.map { account ->
        val weight = accountWeight(account, cfg)
        val resolved = account.wins + account.losses
        val hitRate = if (resolved > 0) account.wins.toDouble() / resolved else null
        val latest = signals
            .filter { it.account.equals(account.handle, ignoreCase = true) }
            .maxByOrNull { it.timeMs }
        val d = latest?.let { decay(nowMs - it.timeMs, it.timeframe) } ?: 0.0
        if (latest == null || d < MIN_DECAY) {
            AccountRow(account.handle, null, latest?.let { nowMs - it.timeMs }, weight, hitRate, resolved, d, 0.0, null, null, null)
        } else {
            val sign = when (latest.direction) {
                Direction.BULLISH -> 1.0
                Direction.BEARISH -> -1.0
                Direction.NEUTRAL -> 0.0
            }
            val nearest = price?.let { p -> allLevels(latest).map { it.first }.minByOrNull { abs(it / p - 1) } }
            AccountRow(
                handle = account.handle,
                signal = latest,
                ageMs = nowMs - latest.timeMs,
                weight = weight,
                hitRate = hitRate,
                resolved = resolved,
                decay = d,
                score = sign * (latest.conviction.coerceIn(1, 3) / 3.0) * weight * d,
                rewardRisk = rewardRisk(latest, price),
                nearestLevel = nearest,
                nearestLevelPct = if (nearest != null) (nearest / price - 1) * 100 else null,
            )
        }
    }

    // Silent accounts stay in the denominator: the score is the consensus of everyone followed.
    val totalWeight = rows.sumOf { it.weight }
    val score = if (totalWeight > 0) 100 * rows.sumOf { it.score } / totalWeight else 0.0

    val bulls = rows.filter { it.signal?.direction == Direction.BULLISH }
    val bears = rows.filter { it.signal?.direction == Direction.BEARISH }
    val directional = bulls.size + bears.size
    val agreement = if (directional > 0) max(bulls.size, bears.size).toDouble() / directional else null

    val buySide = score > 0
    val side = if (buySide) bulls else bears
    val sideName = if (buySide) "buy" else "sell"
    val medianRr = median(side.mapNotNull { it.rewardRisk })

    val reasons = mutableListOf<String>()
    if (directional == 0) {
        reasons += "No fresh bullish or bearish signals"
    } else {
        if (abs(score) < cfg.buyThreshold) {
            reasons += String.format(Locale.US, "Score %.0f is inside the ±%.0f wait band", score, cfg.buyThreshold)
        }
        if (side.size < cfg.minAgreeingAccounts) {
            reasons += "${side.size} account(s) on the $sideName side; ${cfg.minAgreeingAccounts} required"
        }
        if (medianRr != null && medianRr < cfg.minRewardRisk) {
            reasons += String.format(Locale.US, "Median reward:risk %.1f is below %.1f", medianRr, cfg.minRewardRisk)
        }
    }

    val verdict = when {
        reasons.isNotEmpty() -> Verdict.WAIT
        abs(score) >= cfg.strongThreshold && (agreement ?: 0.0) >= 0.75 ->
            if (buySide) Verdict.STRONG_BUY else Verdict.STRONG_SELL
        else -> if (buySide) Verdict.BUY else Verdict.SELL
    }

    val cautions = mutableListOf<String>()
    if (market != null) {
        val sma200 = market.sma200
        if (sma200 != null && abs(score) >= cfg.buyThreshold) {
            if (buySide && market.price < sma200) cautions += "Buying against the trend: price is below the 200-day average"
            if (!buySide && market.price > sma200) cautions += "Selling against the trend: price is above the 200-day average"
        }
        market.fundingRate?.let {
            if (it >= 0.0005) cautions += "Funding is high: longs are crowded"
            if (it <= -0.0002) cautions += "Funding is negative: shorts are crowded"
        }
        market.fearGreed?.let {
            if (it >= 75) cautions += "Fear & Greed at $it: extreme greed"
            if (it <= 25) cautions += "Fear & Greed at $it: extreme fear"
        }
    }

    return DecisionMatrix(
        rows = rows,
        score = score,
        agreement = agreement,
        bullCount = bulls.size,
        bearCount = bears.size,
        medianRewardRisk = medianRr,
        verdict = verdict,
        reasons = reasons,
        cautions = cautions,
        zones = buildZones(rows, price, cfg.clusterPct),
    )
}
