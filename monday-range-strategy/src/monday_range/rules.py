"""Deterministic rules engine: Monday range, liquidity sweep, CISD / MSB confirmation.

Input is a candle DataFrame indexed by candle OPEN time (tz-aware UTC) with columns
open, high, low, close. Every decision at candle j uses only candles <= j, so the
same code is safe for backtesting and for live use.

The long side (sweep of the Monday Low) is computed by mirroring prices
(negating them and swapping high/low) and reusing the short-side logic.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass

import numpy as np
import pandas as pd

from .config import StrategyParams


@dataclass(frozen=True)
class MondayRange:
    week_start: pd.Timestamp  # Monday 00:00 in week_tz, expressed in UTC
    high: float
    low: float
    complete: bool  # True when Monday has all its candles

    @property
    def mid(self) -> float:
        return (self.high + self.low) / 2


@dataclass(frozen=True)
class Setup:
    week_start: pd.Timestamp
    side: str  # "short" (Monday High swept) or "long" (Monday Low swept)
    swept_level: str  # "monday_high" or "monday_low"
    range_high: float
    range_low: float
    sweep_time: pd.Timestamp  # open time of the first candle beyond the level
    extreme: float  # furthest price reached during the sweep
    extreme_time: pd.Timestamp
    confirm_time: pd.Timestamp  # open time of the confirming candle
    entry_time: pd.Timestamp  # close time of the confirming candle
    confirmation: str  # "CISD", "MSB" or "CISD+MSB"
    bars_to_confirm: int
    entry: float
    stop: float
    target: float  # opposite side of the range
    partial_target: float  # mid-range
    rr: float  # reward:risk to the full target
    meets_min_rr: bool
    sweep_depth: float  # distance of the extreme beyond the level
    atr: float

    def to_dict(self) -> dict:
        return asdict(self)


# --------------------------------------------------------------------------- helpers


def _validate(df: pd.DataFrame) -> None:
    if not isinstance(df.index, pd.DatetimeIndex) or df.index.tz is None:
        raise ValueError("candles must be indexed by a tz-aware DatetimeIndex")
    if not df.index.is_monotonic_increasing or df.index.has_duplicates:
        raise ValueError("candle index must be sorted and unique")
    missing = {"open", "high", "low", "close"} - set(df.columns)
    if missing:
        raise ValueError(f"candles are missing columns: {sorted(missing)}")


def _week_keys(index: pd.DatetimeIndex, tz: str) -> tuple[pd.DatetimeIndex, np.ndarray]:
    """For each candle: its week's Monday 00:00 (in tz, expressed in UTC) and its weekday."""
    local = index.tz_convert(tz)
    weekday = np.asarray(local.day_of_week)
    monday = (local.normalize() - pd.to_timedelta(weekday, unit="D")).tz_convert("UTC")
    return monday, weekday


def atr(df: pd.DataFrame, period: int) -> np.ndarray:
    """Simple-average true range. Used only to size the stop buffer."""
    high, low, close = (df[c].to_numpy(float) for c in ("high", "low", "close"))
    prev_close = np.roll(close, 1)
    prev_close[0] = close[0]
    tr = np.maximum(high - low, np.maximum(abs(high - prev_close), abs(low - prev_close)))
    return pd.Series(tr).rolling(period, min_periods=1).mean().to_numpy()


def pivot_lows(low: np.ndarray, k: int) -> np.ndarray:
    """True at p when low[p] is below the k lows before it and not above the k after.

    A pivot at p is only knowable once candle p + k has closed; callers must
    enforce that themselves.
    """
    n = len(low)
    out = np.zeros(n, dtype=bool)
    for p in range(k, n - k):
        if low[p] < low[p - k : p].min() and low[p] <= low[p + 1 : p + k + 1].min():
            out[p] = True
    return out


# ------------------------------------------------------------------ Monday range


def monday_ranges(df: pd.DataFrame, params: StrategyParams = StrategyParams()) -> list[MondayRange]:
    """High and low of each Monday in the data."""
    _validate(df)
    week, weekday = _week_keys(df.index, params.week_tz)
    expected = 24 * 60 // params.timeframe_minutes
    monday = df[weekday == 0]
    grouped = monday.groupby(week[weekday == 0])
    out = []
    for key, g in grouped:
        out.append(
            MondayRange(
                week_start=pd.Timestamp(key),
                high=float(g["high"].max()),
                low=float(g["low"].min()),
                complete=len(g) == expected,
            )
        )
    return out


# ------------------------------------------------- confirmation levels (short side)


def _cisd_level(o: np.ndarray, c: np.ndarray, e: int, max_gap: int) -> tuple[int, float] | None:
    """Bearish CISD level for a high made at candle e.

    The delivery run is the series of consecutive up-close candles that drove
    price into the high. Returns (index of the run's last candle, open of the
    run's first candle), or None when no such run ends near e.
    """
    u = e
    while u >= 0 and e - u <= max_gap and not c[u] > o[u]:
        u -= 1
    if u < 0 or e - u > max_gap:
        return None
    s = u
    while s > 0 and c[s - 1] > o[s - 1]:
        s -= 1
    return u, float(o[s])


def _swing_low(
    is_pivot: np.ndarray, low: np.ndarray, e: int, j: int, k: int, lookback: int
) -> float | None:
    """Most recent swing low before the extreme e that is already confirmed at j."""
    p = min(e - 1, j - k)
    floor = max(e - lookback, 0)
    while p >= floor:
        if is_pivot[p]:
            return float(low[p])
        p -= 1
    return None


def _scan_high_sweeps(
    o: np.ndarray,
    h: np.ndarray,
    l: np.ndarray,
    c: np.ndarray,
    is_pivot_low: np.ndarray,
    start: int,
    end: int,
    level: float,
    params: StrategyParams,
) -> list[tuple[int, int, int, str]]:
    """Find sweeps of `level` from above in candles [start, end).

    A sweep is live only while price is taking fresh liquidity: it starts, and
    its confirmation window restarts, each time a candle makes a new extreme for
    the week beyond the level. A later poke across the level that does not
    exceed the week's extreme is a retest, not a sweep.

    Returns (first breach index, extreme index, confirmation index, kind), where
    the first breach is the candle that opened the current sweep.
    """
    threshold = level * (1 + params.min_sweep_pct) if level >= 0 else level * (1 - params.min_sweep_pct)
    found: list[tuple[int, int, int, str]] = []
    extreme, e, i0 = -np.inf, -1, -1
    live = False
    for j in range(start, end):
        if h[j] > threshold and h[j] > extreme:
            if not live or j - e > params.max_bars_to_confirm:
                i0 = j  # a new sweep begins
            extreme, e, live = h[j], j, True
        if not live or j - e > params.max_bars_to_confirm:
            continue
        if c[j] >= level:
            continue  # still outside the range: no entry yet
        kinds = []
        if params.use_cisd:
            cisd = _cisd_level(o, c, e, params.cisd_max_gap)
            if cisd is not None and j > cisd[0] and c[j] < cisd[1]:
                kinds.append("CISD")
        if params.use_msb:
            swing = _swing_low(is_pivot_low, l, e, j, params.pivot_k, params.pivot_lookback)
            if swing is not None and c[j] < swing:
                kinds.append("MSB")
        if kinds:
            found.append((i0, e, j, "+".join(kinds)))
            live = False
            if len(found) >= params.max_setups_per_side:
                break
    return found


# ------------------------------------------------------------------------ setups


def find_setups(df: pd.DataFrame, params: StrategyParams = StrategyParams()) -> list[Setup]:
    """All sweep + confirmation setups in the data, in chronological order."""
    _validate(df)
    if df.empty:
        return []

    o, h, l, c = (df[col].to_numpy(float) for col in ("open", "high", "low", "close"))
    atr_values = atr(df, params.atr_period)
    week, weekday = _week_keys(df.index, params.week_tz)
    bar = pd.Timedelta(minutes=params.timeframe_minutes)
    times = df.index

    # Short side works on real prices; long side on mirrored prices.
    views = {
        "short": (o, h, l, c, pivot_lows(l, params.pivot_k)),
        "long": (-o, -l, -h, -c, pivot_lows(-h, params.pivot_k)),
    }

    setups: list[Setup] = []
    for rng in monday_ranges(df, params):
        if params.require_complete_monday and not rng.complete:
            continue
        in_week = np.flatnonzero(np.asarray(week == rng.week_start) & (weekday >= 1))
        if len(in_week) == 0:
            continue
        start, end = int(in_week[0]), int(in_week[-1]) + 1

        for side, (vo, vh, vl, vc, piv) in views.items():
            sign = 1.0 if side == "short" else -1.0
            level = rng.high if side == "short" else rng.low
            target = rng.low if side == "short" else rng.high
            for i0, e, j, kind in _scan_high_sweeps(vo, vh, vl, vc, piv, start, end, sign * level, params):
                extreme = float(h[e] if side == "short" else l[e])
                entry = float(c[j])
                stop = extreme + sign * params.stop_buffer_atr * float(atr_values[j])
                risk = sign * (stop - entry)
                reward = sign * (entry - target)
                rr = reward / risk if risk > 0 else float("nan")
                setups.append(
                    Setup(
                        week_start=rng.week_start,
                        side=side,
                        swept_level="monday_high" if side == "short" else "monday_low",
                        range_high=rng.high,
                        range_low=rng.low,
                        sweep_time=times[i0],
                        extreme=extreme,
                        extreme_time=times[e],
                        confirm_time=times[j],
                        entry_time=times[j] + bar,
                        confirmation=kind,
                        bars_to_confirm=j - i0,
                        entry=entry,
                        stop=stop,
                        target=target,
                        partial_target=rng.mid,
                        rr=rr,
                        meets_min_rr=bool(rr >= params.min_rr),
                        sweep_depth=sign * (extreme - level),
                        atr=float(atr_values[j]),
                    )
                )

    setups.sort(key=lambda s: s.confirm_time)
    return setups


def setups_frame(setups: list[Setup]) -> pd.DataFrame:
    return pd.DataFrame([s.to_dict() for s in setups])
