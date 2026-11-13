import dataclasses

import pandas as pd
import pytest

from monday_range import StrategyParams, find_setups, monday_ranges
from monday_range.rules import pivot_lows

from conftest import TUESDAY, make_week

T = TUESDAY

# Sweep of the Monday High (105), then a close below the open of the up-run (100).
SHORT_CISD = {
    T + 0: (100.0, 102.0, 99.8, 101.8),
    T + 1: (101.8, 104.0, 101.5, 103.8),
    T + 2: (103.8, 106.0, 103.5, 105.5),  # first breach, closes outside
    T + 3: (105.5, 105.6, 103.0, 103.2),  # back inside, above the CISD level
    T + 4: (103.2, 103.3, 99.5, 99.7),  # closes below 100 -> CISD
}

# Sweep of the Monday Low (95) with a swing high at 97.5 on the way down.
LONG_MSB = {
    T + 0: (100.0, 100.1, 97.0, 97.1),
    T + 1: (97.1, 97.2, 96.0, 96.1),
    T + 2: (96.1, 96.3, 95.8, 96.0),
    T + 3: (96.0, 97.5, 95.9, 97.3),  # swing high
    T + 4: (97.3, 97.4, 95.5, 95.6),  # down-run starts, open 97.3 = CISD level
    T + 5: (95.6, 95.7, 94.0, 94.2),  # first breach, extreme 94
    T + 6: (94.2, 96.0, 94.1, 95.9),  # back inside, no confirmation yet
    T + 7: (95.9, 97.7, 95.8, 97.6),  # closes above 97.3 and 97.5
}


# ------------------------------------------------------------------ Monday range


def test_monday_range_uses_only_monday_candles():
    df = make_week({T + 5: (100, 120, 80, 100)})  # Tuesday spike must be ignored
    (rng,) = monday_ranges(df)
    assert rng.week_start == pd.Timestamp("2024-01-01", tz="UTC")
    assert (rng.high, rng.low, rng.mid) == (105.0, 95.0, 100.0)
    assert rng.complete


def test_monday_boundary_is_utc_midnight():
    df = make_week({95: (100, 107, 99, 100), 96: (100, 111, 99, 100)})
    (rng,) = monday_ranges(df)
    assert rng.high == 107.0  # 23:45 Monday counts, 00:00 Tuesday does not


def test_week_timezone_is_a_parameter():
    df = make_week(weeks=2)
    ranges = monday_ranges(df, StrategyParams(week_tz="America/New_York"))
    assert pd.Timestamp("2024-01-08 05:00", tz="UTC") in [r.week_start for r in ranges]


def test_incomplete_monday_is_skipped():
    df = make_week(SHORT_CISD).drop(index=pd.Timestamp("2024-01-01 03:00", tz="UTC"))
    assert not monday_ranges(df)[0].complete
    assert find_setups(df) == []
    assert len(find_setups(df, StrategyParams(require_complete_monday=False))) == 1


# ------------------------------------------------------------------------ setups


def test_quiet_week_has_no_setups():
    assert find_setups(make_week()) == []


def test_short_setup_on_cisd():
    df = make_week(SHORT_CISD)
    (s,) = find_setups(df)
    assert (s.side, s.swept_level, s.confirmation) == ("short", "monday_high", "CISD")
    assert s.sweep_time == df.index[T + 2]
    assert s.extreme == 106.0 and s.extreme_time == df.index[T + 2]
    assert s.confirm_time == df.index[T + 4]
    assert s.entry_time == df.index[T + 5]
    assert s.bars_to_confirm == 2
    assert s.entry == 99.7
    assert s.stop == pytest.approx(106.0 * 1.0005)
    assert s.target == 95.0 and s.partial_target == 100.0
    assert s.rr == pytest.approx((99.7 - 95.0) / (s.stop - 99.7))
    assert s.sweep_depth == pytest.approx(1.0)


def test_close_back_inside_without_confirmation_is_not_an_entry():
    candles = dict(SHORT_CISD)
    del candles[T + 4]  # price returns inside but never closes below the CISD level
    assert find_setups(make_week(candles)) == []


def test_long_setup_on_cisd_and_msb():
    df = make_week(LONG_MSB)
    (s,) = find_setups(df)
    assert (s.side, s.swept_level, s.confirmation) == ("long", "monday_low", "CISD+MSB")
    assert s.sweep_time == df.index[T + 5]
    assert s.extreme == 94.0
    assert s.confirm_time == df.index[T + 7]
    assert s.entry == 97.6
    assert s.stop == pytest.approx(94.0 * 0.9995)
    assert s.target == 105.0
    assert s.rr == pytest.approx((105.0 - 97.6) / (97.6 - s.stop))


def test_msb_alone_confirms_when_cisd_is_disabled():
    (s,) = find_setups(make_week(LONG_MSB), StrategyParams(use_cisd=False))
    assert s.confirmation == "MSB"


def test_cisd_fires_before_msb_when_close_is_between_the_levels():
    candles = dict(LONG_MSB)
    candles[T + 7] = (95.9, 97.45, 95.8, 97.4)  # above 97.3, below the 97.5 swing high
    (s,) = find_setups(make_week(candles))
    assert s.confirmation == "CISD"


def test_wick_below_minimum_sweep_distance_is_ignored():
    candles = dict(SHORT_CISD)
    candles[T + 2] = (103.8, 105.01, 103.5, 104.5)  # 0.01 beyond 105 < 0.02% of 105
    candles[T + 3] = (104.5, 104.6, 103.0, 103.2)
    assert find_setups(make_week(candles)) == []


def test_breakout_that_holds_outside_is_not_a_sweep():
    candles = {T + i: (106.0, 107.0, 105.5, 106.0) for i in range(40)}
    candles[T + 0] = (100.0, 107.0, 99.8, 106.0)  # up candle delivering the breakout
    candles[T + 40] = (106.0, 106.1, 99.0, 99.2)  # late collapse, after the window
    assert find_setups(make_week(candles)) == []


def _failed_breakout() -> dict:
    """Breakout to 107 that holds above the Monday High for 10 hours, then collapses."""
    candles = {T + i: (106.0, 107.0, 105.5, 106.0) for i in range(40)}
    candles[T + 0] = (100.0, 107.0, 99.8, 106.0)
    candles[T + 40] = (106.0, 106.1, 99.0, 100.0)
    return candles


def _higher_sweep(offset: int, high: float) -> dict:
    """The SHORT_CISD pattern moved `offset` candles later, reaching `high`."""
    candles = {pos + offset: ohlc for pos, ohlc in SHORT_CISD.items()}
    o, _, l, c = candles[T + offset + 2]
    candles[T + offset + 2] = (o, high, l, c)
    return candles


def test_retest_after_a_failed_breakout_is_not_a_sweep():
    # Price pokes back above the level (to 106) but below the week's high (107).
    retest = {pos + 100: ohlc for pos, ohlc in SHORT_CISD.items()}
    assert find_setups(make_week({**_failed_breakout(), **retest})) == []


def test_new_weekly_extreme_after_a_failed_breakout_is_a_sweep():
    df = make_week({**_failed_breakout(), **_higher_sweep(100, 108.0)})
    (s,) = find_setups(df)
    assert s.sweep_time == df.index[T + 102]
    assert s.extreme == 108.0
    assert s.stop > 108.0


def test_window_restarts_when_the_sweep_makes_a_new_extreme():
    # A small poke starts the clock; the real sweep comes 52 candles later.
    poke = {T: (100.0, 105.5, 99.8, 100.5)}
    df = make_week({**poke, **_higher_sweep(50, 106.0)})
    (s,) = find_setups(df)
    assert s.extreme_time == df.index[T + 52]
    assert s.confirm_time == df.index[T + 54]


def test_one_setup_per_side_per_week_by_default():
    df = make_week({**SHORT_CISD, **_higher_sweep(100, 108.0)})
    assert len(find_setups(df)) == 1
    assert len(find_setups(df, StrategyParams(max_setups_per_side=2))) == 2


def test_both_sides_can_trigger_in_one_week():
    low_sweep = {pos + 100: ohlc for pos, ohlc in LONG_MSB.items()}
    setups = find_setups(make_week({**SHORT_CISD, **low_sweep}))
    assert [s.side for s in setups] == ["short", "long"]


def test_low_reward_setup_is_flagged():
    (s,) = find_setups(make_week(SHORT_CISD), StrategyParams(min_rr=5.0))
    assert s.rr < 5.0 and not s.meets_min_rr


def test_each_week_uses_its_own_monday():
    second_week = {pos + 96 * 7: ohlc for pos, ohlc in SHORT_CISD.items()}
    (s,) = find_setups(make_week(second_week, weeks=2))
    assert s.week_start == pd.Timestamp("2024-01-08", tz="UTC")


# --------------------------------------------------------------------- look-ahead


def test_pivot_needs_k_candles_on_each_side():
    import numpy as np

    low = np.array([5.0, 4.0, 3.0, 4.0, 5.0, 2.0])
    assert list(np.flatnonzero(pivot_lows(low, 2))) == [2]  # last candle is not confirmable


def test_setups_do_not_depend_on_future_candles(random_walk):
    params = StrategyParams(max_setups_per_side=3)
    full = find_setups(random_walk, params)
    assert len(full) >= 5, "random walk should produce setups for this test to mean anything"
    for s in full:
        visible = random_walk.loc[: s.confirm_time]  # nothing after the confirming candle
        replay = [r for r in find_setups(visible, params) if r.confirm_time == s.confirm_time and r.side == s.side]
        assert len(replay) == 1
        assert dataclasses.asdict(replay[0]) == dataclasses.asdict(s)


def test_truncated_history_yields_a_prefix_of_the_full_result(random_walk):
    full = find_setups(random_walk)
    cut = random_walk.index[len(random_walk) // 2]
    early = find_setups(random_walk.loc[:cut])
    assert early == [s for s in full if s.confirm_time <= cut]
