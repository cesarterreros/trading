import numpy as np
import pandas as pd
import pytest

BARS_PER_DAY = 96
TUESDAY = BARS_PER_DAY  # position of the first Tuesday candle


def make_week(overrides: dict[int, tuple[float, float, float, float]] | None = None, weeks: int = 1) -> pd.DataFrame:
    """Quiet synthetic 15m candles starting Monday 2024-01-01 00:00 UTC.

    Every candle is a doji at 100 (high 101, low 99) except that each Monday
    prints a high of 105 and a low of 95, so the Monday range is 95-105.
    `overrides` maps a candle position to (open, high, low, close).
    """
    n = BARS_PER_DAY * 7 * weeks
    index = pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC")
    df = pd.DataFrame({"open": 100.0, "high": 101.0, "low": 99.0, "close": 100.0, "volume": 1.0}, index=index)
    for w in range(weeks):
        base = w * BARS_PER_DAY * 7
        df.iloc[base + 10, df.columns.get_loc("high")] = 105.0
        df.iloc[base + 20, df.columns.get_loc("low")] = 95.0
    for pos, (o, h, l, c) in (overrides or {}).items():
        df.iloc[pos, :4] = [o, h, l, c]
    return df


@pytest.fixture
def random_walk() -> pd.DataFrame:
    """Eight weeks of random-walk 15m candles."""
    rng = np.random.default_rng(7)
    n = BARS_PER_DAY * 7 * 8
    index = pd.date_range("2024-01-01", periods=n, freq="15min", tz="UTC")
    close = 100 + np.cumsum(rng.normal(0, 0.25, n))
    open_ = np.concatenate([[100.0], close[:-1]])
    high = np.maximum(open_, close) + rng.uniform(0, 0.2, n)
    low = np.minimum(open_, close) - rng.uniform(0, 0.2, n)
    return pd.DataFrame({"open": open_, "high": high, "low": low, "close": close, "volume": 1.0}, index=index)
