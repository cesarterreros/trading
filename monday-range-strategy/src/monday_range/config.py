"""Every tunable rule of the strategy lives here, so the backtest and the live bot share one definition."""

from dataclasses import dataclass


@dataclass(frozen=True)
class StrategyParams:
    # --- candles ---
    timeframe_minutes: int = 15

    # --- Monday range ---
    # Timezone that defines "Monday". UTC matches the exchange daily candle.
    week_tz: str = "UTC"
    # Skip weeks whose Monday has missing candles (the range would be unreliable).
    require_complete_monday: bool = True

    # --- sweep ---
    # Price must trade beyond the Monday level by at least this fraction of the level.
    min_sweep_pct: float = 0.0002
    # A sweep must be confirmed within this many candles of its extreme, i.e. of
    # the last new weekly high/low beyond the level (32 x 15m = 8h). A poke across
    # the level that makes no new weekly extreme is a retest and never a sweep.
    max_bars_to_confirm: int = 32
    # Setups allowed per side (Monday High / Monday Low) per week.
    max_setups_per_side: int = 1

    # --- confirmation ---
    use_cisd: bool = True
    use_msb: bool = True
    # CISD: if the candle that made the extreme is not itself part of the delivery
    # run, the run must end within this many candles before it.
    cisd_max_gap: int = 3
    # MSB: a swing point needs this many candles on each side to be a pivot.
    pivot_k: int = 2
    # MSB: how far back from the sweep extreme to look for the swing point.
    pivot_lookback: int = 96

    # --- trade levels ---
    # Stop is placed beyond the sweep extreme by this multiple of ATR.
    stop_buffer_atr: float = 0.1
    atr_period: int = 14
    # Setups with a lower reward:risk to the full target are flagged, not dropped.
    min_rr: float = 1.0
