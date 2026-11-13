package com.trading.monitor.matrix

data class Candle(val openTimeMs: Long, val high: Double, val low: Double, val close: Double)

enum class Outcome { WIN, LOSS, EXPIRED }

/**
 * Decides whether a signal's target or stop was reached first.
 *
 * - [entry] is passed only when the signal named an entry away from the price at posting time;
 *   the trade then counts only after a candle trades through it.
 * - A candle that touches both stop and target counts as a loss.
 * - Returns null while the signal is still open.
 */
fun resolveOutcome(
    direction: Direction,
    entry: Double?,
    stop: Double,
    target: Double,
    signalTimeMs: Long,
    horizonMs: Long,
    candles: List<Candle>,
    nowMs: Long,
): Outcome? {
    if (direction == Direction.NEUTRAL) return null
    val end = signalTimeMs + horizonMs
    var triggered = entry == null

    for (c in candles.sortedBy { it.openTimeMs }) {
        if (c.openTimeMs < signalTimeMs || c.openTimeMs >= end) continue
        if (!triggered) {
            if (entry != null && c.low <= entry && entry <= c.high) triggered = true
            continue
        }
        val hitStop = if (direction == Direction.BULLISH) c.low <= stop else c.high >= stop
        val hitTarget = if (direction == Direction.BULLISH) c.high >= target else c.low <= target
        if (hitStop) return Outcome.LOSS
        if (hitTarget) return Outcome.WIN
    }
    return if (nowMs >= end) Outcome.EXPIRED else null
}
