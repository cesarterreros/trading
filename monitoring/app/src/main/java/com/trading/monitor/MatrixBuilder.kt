package com.trading.monitor

import com.trading.monitor.db.Account
import com.trading.monitor.db.Signal
import com.trading.monitor.market.MarketState
import com.trading.monitor.matrix.AccountInput
import com.trading.monitor.matrix.DecisionMatrix
import com.trading.monitor.matrix.Direction
import com.trading.monitor.matrix.MarketInput
import com.trading.monitor.matrix.MatrixConfig
import com.trading.monitor.matrix.SignalInput
import com.trading.monitor.matrix.Timeframe
import com.trading.monitor.matrix.buildMatrix

fun parseLevels(csv: String): List<Double> = csv.split(',').mapNotNull { it.trim().toDoubleOrNull() }

fun directionOf(name: String): Direction =
    Direction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Direction.NEUTRAL

fun timeframeOf(name: String): Timeframe =
    Timeframe.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Timeframe.SWING

/** Accepts "@name", "name" or a profile URL; returns the lowercase handle or null if invalid. */
fun normalizeHandle(raw: String): String? {
    val handle = raw.trim().trimEnd('/').substringAfterLast('/').substringBefore('?').removePrefix("@").lowercase()
    return handle.takeIf { it.matches(Regex("[a-z0-9_]{1,15}")) }
}

/** Posts judged as noise carry no opinion, so they never become matrix inputs. */
fun Signal.toInput(): SignalInput? {
    if (type == "noise") return null
    return SignalInput(
        account = handle,
        timeMs = timeMs,
        direction = directionOf(direction),
        conviction = conviction,
        timeframe = timeframeOf(timeframe),
        entry = entry,
        stop = stop,
        targets = parseLevels(targets),
        support = parseLevels(support),
        resistance = parseLevels(resistance),
    )
}

fun computeMatrix(
    accounts: List<Account>,
    signals: List<Signal>,
    market: MarketState,
    cfg: MatrixConfig,
    nowMs: Long,
): DecisionMatrix {
    val byHandle = signals.groupBy { it.handle }
    val accountInputs = accounts.filter { it.enabled }.map { a ->
        val own = byHandle[a.handle].orEmpty()
        AccountInput(
            handle = a.handle,
            manualWeight = a.weight,
            wins = own.count { it.outcome == "WIN" },
            losses = own.count { it.outcome == "LOSS" },
        )
    }
    val marketInput = market.price?.let {
        MarketInput(it, market.sma50, market.sma200, market.fundingRate, market.fearGreed)
    }
    return buildMatrix(accountInputs, signals.mapNotNull { it.toInput() }, marketInput, cfg, nowMs)
}
