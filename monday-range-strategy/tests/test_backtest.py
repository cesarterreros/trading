import pandas as pd
import pytest

from monday_range import StrategyParams, find_setups
from monday_range.backtest import BacktestParams, run_backtest, simulate_trade, summarize

from conftest import make_week
from test_rules import SHORT_CISD, _failed_breakout, _higher_sweep

NO_COSTS = BacktestParams(fee_pct=0, slippage_pct=0, funding_per_8h=0, partial_fraction=0)
WEEK = pd.Timestamp("2024-01-01", tz="UTC")
ENTRY_TIME = pd.Timestamp("2024-01-02 10:00", tz="UTC")


def long_setup(**changes) -> pd.Series:
    """Long from 100, stop 98, target 106, mid-range 103: reward:risk 3."""
    base = {
        "week_start": WEEK, "side": "long", "confirmation": "CISD", "entry_time": ENTRY_TIME,
        "entry": 100.0, "stop": 98.0, "target": 106.0, "partial_target": 103.0,
        "rr": 3.0, "meets_min_rr": True, "closes_outside": 0,
    }
    return pd.Series({**base, **changes})


def minutes(path: list[tuple[float, float, float]], start=ENTRY_TIME) -> pd.DataFrame:
    """1-minute candles from (high, low, close) tuples."""
    index = pd.date_range(start, periods=len(path), freq="1min", tz="UTC")
    return pd.DataFrame(path, columns=["high", "low", "close"], index=index)


def test_stop_is_minus_one_r():
    t = simulate_trade(long_setup(), minutes([(100.5, 99.5, 100), (100, 97.9, 98)]), NO_COSTS)
    assert t["outcome"] == "stop" and t["r_net"] == pytest.approx(-1.0)


def test_target_pays_the_reward_to_risk():
    t = simulate_trade(long_setup(), minutes([(101, 99.5, 101), (106.2, 101, 106)]), NO_COSTS)
    assert t["outcome"] == "target" and t["r_net"] == pytest.approx(3.0)


def test_stop_wins_when_both_are_hit_in_the_same_minute():
    t = simulate_trade(long_setup(), minutes([(106.5, 97.5, 100)]), NO_COSTS)
    assert t["outcome"] == "stop"


def test_short_side_is_mirrored():
    s = long_setup(side="short", entry=100.0, stop=102.0, target=94.0, partial_target=97.0)
    t = simulate_trade(s, minutes([(100.5, 99, 99), (99, 93.8, 94)]), NO_COSTS)
    assert t["outcome"] == "target" and t["r_net"] == pytest.approx(3.0)


def test_open_trade_is_closed_at_the_end_of_the_week():
    last_minute = WEEK + pd.Timedelta(days=7) - pd.Timedelta(minutes=2)
    path = minutes([(101, 100, 101), (102.2, 101, 102)], start=last_minute)
    t = simulate_trade(long_setup(entry_time=last_minute), path, NO_COSTS)
    assert t["outcome"] == "week end" and t["r_net"] == pytest.approx(1.0)
    assert t["exit_time"] == WEEK + pd.Timedelta(days=7)


def test_partial_exit_then_stop():
    bp = BacktestParams(fee_pct=0, slippage_pct=0, funding_per_8h=0, partial_fraction=0.5)
    t = simulate_trade(long_setup(), minutes([(103.1, 100, 103), (103, 97.9, 98)]), bp)
    assert t["outcome"] == "partial+stop"
    assert t["r_net"] == pytest.approx(0.5 * 1.5 + 0.5 * -1.0)


def test_partial_exit_then_breakeven_stop():
    bp = BacktestParams(fee_pct=0, slippage_pct=0, funding_per_8h=0, partial_fraction=0.5, breakeven_after_partial=True)
    t = simulate_trade(long_setup(), minutes([(103.1, 100.5, 103), (103, 99.9, 100)]), bp)
    assert t["outcome"] == "partial+stop"
    assert t["r_net"] == pytest.approx(0.5 * 1.5)


def test_partial_exit_then_target():
    bp = BacktestParams(fee_pct=0, slippage_pct=0, funding_per_8h=0, partial_fraction=0.5)
    t = simulate_trade(long_setup(), minutes([(103.1, 100, 103), (106.1, 103, 106)]), bp)
    assert t["outcome"] == "partial+target"
    assert t["r_net"] == pytest.approx(0.5 * 1.5 + 0.5 * 3.0)


def test_costs_reduce_the_result():
    path = minutes([(101, 99.5, 101), (106.2, 101, 106)])
    free = simulate_trade(long_setup(), path, NO_COSTS)
    costly = simulate_trade(long_setup(), path, BacktestParams(partial_fraction=0))
    assert costly["r_gross"] == pytest.approx(free["r_net"])
    # entry slippage 0.02 + fees 0.05% of (100.02 + 106) + 2 minutes of funding, over a risk of 2
    expected_cost = (0.02 + 0.0005 * (100.02 + 106.0) + 0.0001 * 100.02 * (2 / 60) / 8) / 2
    assert costly["cost_r"] == pytest.approx(expected_cost)
    assert costly["r_net"] == pytest.approx(3.0 - expected_cost)


def test_backtest_runs_from_candles_to_trades():
    candles = make_week(SHORT_CISD)
    minute = candles.resample("1min").ffill()  # flat minutes: price stays at 100 after entry
    trades, counts = run_backtest(candles, minute, StrategyParams(min_rr=0.5), NO_COSTS)
    assert counts["setups"] == 1 and counts["trades"] == 1
    assert trades.iloc[0]["side"] == "short"
    assert summarize(trades)["trades"] == 1


def test_low_reward_setups_are_skipped():
    candles = make_week(SHORT_CISD)  # reward:risk about 0.7
    trades, counts = run_backtest(candles, candles.resample("1min").ffill(), StrategyParams(min_rr=1.0), NO_COSTS)
    assert trades.empty and counts["skipped: reward:risk below minimum"] == 1


def test_only_one_trade_per_week_by_default():
    # A high sweep that stops out, then a second, higher sweep in the same week.
    candles = make_week({**SHORT_CISD, **_higher_sweep(100, 108.0)})
    minute = candles.resample("1min").ffill()
    sp = StrategyParams(min_rr=0.5, max_setups_per_side=2)
    one, counts = run_backtest(candles, minute, sp, NO_COSTS)
    assert len(one) == 1 and counts["skipped: already traded this week"] == 1
    both, _ = run_backtest(candles, minute, sp, BacktestParams(
        fee_pct=0, slippage_pct=0, funding_per_8h=0, partial_fraction=0, max_trades_per_week=None))
    assert len(both) == 2


# ------------------------------------------------------------ strict-sweep cap


def test_setup_records_closes_outside():
    (s,) = find_setups(make_week(SHORT_CISD))
    assert s.closes_outside == 1  # only the breach candle closed above the Monday High


def test_cap_rejects_setups_that_closed_outside_too_long():
    df = make_week(SHORT_CISD)
    assert find_setups(df, StrategyParams(max_consecutive_closes_outside=0)) == []
    assert len(find_setups(df, StrategyParams(max_consecutive_closes_outside=1))) == 1


def test_cap_ends_the_week_for_a_level_that_was_accepted():
    df = make_week({**_failed_breakout(), **_higher_sweep(100, 108.0)})
    assert len(find_setups(df)) == 1
    assert find_setups(df, StrategyParams(max_consecutive_closes_outside=8)) == []
