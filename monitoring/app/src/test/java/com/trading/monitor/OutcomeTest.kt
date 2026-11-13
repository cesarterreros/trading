package com.trading.monitor

import com.trading.monitor.matrix.Candle
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.Outcome
import com.trading.monitor.matrix.resolveOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {
    private val hour = 3_600_000L
    private val horizon = 72 * hour

    private fun candle(i: Int, high: Double, low: Double) = Candle(i * hour, high, low, (high + low) / 2)

    private fun long(candles: List<Candle>, entry: Double? = null, now: Long = 10 * hour) =
        resolveOutcome(Direction.BULLISH, entry, stop = 98.0, target = 105.0, signalTimeMs = 0, horizonMs = horizon, candles = candles, nowMs = now)

    @Test
    fun targetFirstIsAWin() {
        assertEquals(Outcome.WIN, long(listOf(candle(1, 101.0, 99.0), candle(2, 106.0, 100.0))))
    }

    @Test
    fun stopFirstIsALoss() {
        assertEquals(Outcome.LOSS, long(listOf(candle(1, 101.0, 97.0), candle(2, 106.0, 100.0))))
    }

    @Test
    fun stopAndTargetInTheSameCandleIsALoss() {
        assertEquals(Outcome.LOSS, long(listOf(candle(1, 106.0, 97.0))))
    }

    @Test
    fun untouchedSignalStaysOpenThenExpires() {
        val flat = listOf(candle(1, 101.0, 99.0), candle(2, 102.0, 99.5))
        assertNull(long(flat, now = 10 * hour))
        assertEquals(Outcome.EXPIRED, long(flat, now = horizon))
    }

    @Test
    fun candlesAfterTheHorizonAreIgnored() {
        assertEquals(Outcome.EXPIRED, long(listOf(candle(80, 106.0, 100.0)), now = 100 * hour))
    }

    @Test
    fun shortsMirrorLongs() {
        val win = resolveOutcome(Direction.BEARISH, null, stop = 102.0, target = 95.0, signalTimeMs = 0, horizonMs = horizon,
            candles = listOf(candle(1, 101.0, 94.0)), nowMs = 10 * hour)
        val loss = resolveOutcome(Direction.BEARISH, null, stop = 102.0, target = 95.0, signalTimeMs = 0, horizonMs = horizon,
            candles = listOf(candle(1, 103.0, 99.0)), nowMs = 10 * hour)
        assertEquals(Outcome.WIN, win)
        assertEquals(Outcome.LOSS, loss)
    }

    @Test
    fun targetHitBeforeEntryTradesDoesNotCount() {
        fun pullbackLong(candles: List<Candle>) = resolveOutcome(Direction.BULLISH, entry = 95.0, stop = 93.0, target = 105.0,
            signalTimeMs = 0, horizonMs = horizon, candles = candles, nowMs = 10 * hour)

        assertNull(pullbackLong(listOf(candle(1, 106.0, 99.0))))
        assertEquals(Outcome.WIN, pullbackLong(listOf(candle(1, 100.0, 94.0), candle(2, 106.0, 96.0))))
    }
}
