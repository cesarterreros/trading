package com.trading.monitor

import com.trading.monitor.matrix.AccountInput
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.MarketInput
import com.trading.monitor.matrix.MatrixConfig
import com.trading.monitor.matrix.SignalInput
import com.trading.monitor.matrix.Timeframe
import com.trading.monitor.matrix.Verdict
import com.trading.monitor.matrix.accountWeight
import com.trading.monitor.matrix.buildMatrix
import com.trading.monitor.matrix.decay
import com.trading.monitor.matrix.rewardRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L
    private val cfg = MatrixConfig()
    private val market = MarketInput(price = 100_000.0)

    private fun long(account: String, ageDays: Long = 0, conviction: Int = 3) = SignalInput(
        account, now - ageDays * day, Direction.BULLISH, conviction, Timeframe.SWING,
        entry = 100_000.0, stop = 98_000.0, targets = listOf(106_000.0),
    )

    private fun short(account: String, target: Double = 94_000.0) = SignalInput(
        account, now, Direction.BEARISH, 3, Timeframe.SWING,
        entry = 100_000.0, stop = 102_000.0, targets = listOf(target),
    )

    @Test
    fun decayHalvesAtHalfLife() {
        assertEquals(0.5, decay(5 * day, Timeframe.SWING), 1e-9)
        assertEquals(1.0, decay(0, Timeframe.SCALP), 1e-9)
        assertEquals(0.25, decay(2 * day, Timeframe.SCALP), 1e-9)
    }

    @Test
    fun rewardRiskUsesNearestTargetBeyondEntry() {
        assertEquals(3.0, rewardRisk(long("a"), 100_000.0)!!, 1e-9)
        assertEquals(3.0, rewardRisk(short("a"), 100_000.0)!!, 1e-9)
        // A stop on the wrong side gives no ratio.
        assertNull(rewardRisk(long("a").copy(stop = 101_000.0), 100_000.0))
    }

    @Test
    fun twoFreshAgreeingAccountsGiveStrongBuy() {
        val m = buildMatrix(listOf(AccountInput("a"), AccountInput("b")), listOf(long("a"), long("b")), market, cfg, now)
        assertEquals(100.0, m.score, 1e-9)
        assertEquals(1.0, m.agreement!!, 1e-9)
        assertEquals(Verdict.STRONG_BUY, m.verdict)
        assertTrue(m.reasons.isEmpty())
    }

    @Test
    fun singleAccountIsNotEnough() {
        val accounts = listOf(AccountInput("a"), AccountInput("b"), AccountInput("c"))
        val m = buildMatrix(accounts, listOf(long("a")), market, cfg, now)
        assertEquals(100.0 / 3, m.score, 1e-9)
        assertEquals(Verdict.WAIT, m.verdict)
        assertTrue(m.reasons.any { it.contains("required") })
    }

    @Test
    fun poorRewardRiskMeansWait() {
        val signals = listOf(short("a", target = 98_000.0), short("b", target = 98_000.0))
        val m = buildMatrix(listOf(AccountInput("a"), AccountInput("b")), signals, market, cfg, now)
        assertEquals(-100.0, m.score, 1e-9)
        assertEquals(Verdict.WAIT, m.verdict)
        assertTrue(m.reasons.any { it.contains("reward:risk") })
    }

    @Test
    fun agreeingShortsGiveStrongSell() {
        val m = buildMatrix(listOf(AccountInput("a"), AccountInput("b")), listOf(short("a"), short("b")), market, cfg, now)
        assertEquals(Verdict.STRONG_SELL, m.verdict)
    }

    @Test
    fun opposingAccountsCancelOut() {
        val m = buildMatrix(listOf(AccountInput("a"), AccountInput("b")), listOf(long("a"), short("b")), market, cfg, now)
        assertEquals(0.0, m.score, 1e-9)
        assertEquals(0.5, m.agreement!!, 1e-9)
        assertEquals(Verdict.WAIT, m.verdict)
    }

    @Test
    fun staleSignalStopsCounting() {
        val m = buildMatrix(listOf(AccountInput("a")), listOf(long("a", ageDays = 30)), market, cfg, now)
        assertNull(m.rows.single().signal)
        assertEquals(0.0, m.score, 1e-9)
    }

    @Test
    fun onlyTheLatestSignalPerAccountCounts() {
        val m = buildMatrix(listOf(AccountInput("a")), listOf(long("a", ageDays = 2), short("a")), market, cfg, now)
        assertEquals(Direction.BEARISH, m.rows.single().signal!!.direction)
    }

    @Test
    fun hitRateScalesWeightOnlyWithEnoughSamples() {
        assertEquals(1.0, accountWeight(AccountInput("a", 1.0, wins = 4, losses = 1), cfg), 1e-9)
        assertEquals(1.5, accountWeight(AccountInput("a", 1.0, wins = 9, losses = 1), cfg), 1e-9)
        assertEquals(0.4, accountWeight(AccountInput("a", 1.0, wins = 2, losses = 8), cfg), 1e-9)
        assertEquals(0.8, accountWeight(AccountInput("a", 2.0, wins = 2, losses = 8), cfg), 1e-9)
    }

    @Test
    fun nearbyLevelsFromDifferentAccountsFormOneZone() {
        fun neutral(account: String, support: Double) =
            SignalInput(account, now, Direction.NEUTRAL, 1, Timeframe.SWING, support = listOf(support))

        val accounts = listOf(AccountInput("a"), AccountInput("b"), AccountInput("c"))
        val signals = listOf(neutral("a", 95_000.0), neutral("b", 95_300.0), neutral("c", 90_000.0))
        val m = buildMatrix(accounts, signals, market, cfg, now)
        assertEquals(2, m.zones.size)
        assertEquals(setOf("a", "b"), m.zones.first().accounts)
        assertEquals(95_150.0, m.zones.first().center, 1e-6)
    }

    @Test
    fun cautionsFlagCrowdedFundingAndCounterTrend() {
        val hot = MarketInput(price = 100_000.0, sma200 = 110_000.0, fundingRate = 0.0008, fearGreed = 80)
        val m = buildMatrix(listOf(AccountInput("a"), AccountInput("b")), listOf(long("a"), long("b")), hot, cfg, now)
        assertEquals(3, m.cautions.size)
    }
}
