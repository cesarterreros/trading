"""Candle download (public exchange API via ccxt), Parquet storage and integrity checks."""

from __future__ import annotations

import time
from pathlib import Path
from typing import Callable

import pandas as pd

COLUMNS = ["open", "high", "low", "close", "volume"]
DEFAULT_EXCHANGE = "binanceusdm"
DEFAULT_SYMBOL = "BTC/USDT:USDT"
DEFAULT_SINCE = "2019-09-01"


def timeframe_ms(timeframe: str) -> int:
    units = {"m": 60_000, "h": 3_600_000, "d": 86_400_000}
    return int(timeframe[:-1]) * units[timeframe[-1]]


def store_path(data_dir: str | Path, exchange_id: str, symbol: str, timeframe: str) -> Path:
    safe = symbol.replace("/", "").replace(":", "_")
    return Path(data_dir) / f"{exchange_id}_{safe}_{timeframe}.parquet"


def to_frame(rows: list[list]) -> pd.DataFrame:
    """Raw [ms, o, h, l, c, v] rows -> sorted, de-duplicated UTC-indexed frame."""
    df = pd.DataFrame(rows, columns=["time", *COLUMNS])
    df["time"] = pd.to_datetime(df["time"], unit="ms", utc=True)
    df = df.drop_duplicates("time").set_index("time").sort_index()
    return df.astype(float)


def drop_unclosed(df: pd.DataFrame, timeframe: str, now: pd.Timestamp | None = None) -> pd.DataFrame:
    """Remove candles that had not closed yet when they were downloaded."""
    now = now or pd.Timestamp.now(tz="UTC")
    return df[df.index + pd.Timedelta(milliseconds=timeframe_ms(timeframe)) <= now]


def load(path: str | Path) -> pd.DataFrame:
    return pd.read_parquet(path)


def save(df: pd.DataFrame, path: str | Path) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    df.to_parquet(tmp)
    tmp.replace(path)


def _fetch_with_retry(exchange, symbol: str, timeframe: str, since: int, limit: int, attempts: int = 5):
    import ccxt

    for attempt in range(attempts):
        try:
            return exchange.fetch_ohlcv(symbol, timeframe, since=since, limit=limit)
        except (ccxt.NetworkError, ccxt.RateLimitExceeded):
            if attempt == attempts - 1:
                raise
            time.sleep(2**attempt)


def update_store(
    path: str | Path,
    exchange_id: str = DEFAULT_EXCHANGE,
    symbol: str = DEFAULT_SYMBOL,
    timeframe: str = "15m",
    since: str = DEFAULT_SINCE,
    limit: int = 1000,
    checkpoint_every: int = 100,
    progress: Callable[[pd.Timestamp, int], None] | None = None,
) -> pd.DataFrame:
    """Download candles into `path`, resuming from the last stored candle."""
    import ccxt

    path = Path(path)
    step = timeframe_ms(timeframe)
    existing = load(path) if path.exists() else to_frame([])
    if len(existing):
        cursor = int(existing.index[-1].value // 1_000_000) + step
    else:
        cursor = int(pd.Timestamp(since, tz="UTC").value // 1_000_000)

    exchange = getattr(ccxt, exchange_id)({"enableRateLimit": True})
    rows: list[list] = []
    batches = 0

    def flush() -> pd.DataFrame:
        nonlocal existing, rows
        if rows:
            merged = pd.concat([existing, to_frame(rows)])
            merged = merged[~merged.index.duplicated(keep="last")].sort_index()
            existing = drop_unclosed(merged, timeframe)
            rows = []
            save(existing, path)
        return existing

    while cursor < int(time.time() * 1000):
        batch = _fetch_with_retry(exchange, symbol, timeframe, cursor, limit)
        if not batch:
            break
        rows.extend(batch)
        next_cursor = batch[-1][0] + step
        if next_cursor <= cursor:
            break
        cursor = next_cursor
        batches += 1
        if progress:
            progress(pd.Timestamp(batch[-1][0], unit="ms", tz="UTC"), len(rows))
        if batches % checkpoint_every == 0:
            flush()
    return flush()


# ------------------------------------------------------------------ integrity


def find_gaps(df: pd.DataFrame, timeframe: str) -> pd.DataFrame:
    """Missing candle ranges: first missing open time, last missing open time, count."""
    step = pd.Timedelta(milliseconds=timeframe_ms(timeframe))
    delta = df.index.to_series().diff()
    breaks = delta[delta > step]
    after = breaks.index  # first candle present after each gap
    return pd.DataFrame(
        {
            "first_missing": list(after - breaks.to_numpy() + step),
            "last_missing": list(after - step),
            "missing_bars": [int(d / step) - 1 for d in breaks],
        }
    )


def invalid_rows(df: pd.DataFrame) -> pd.DataFrame:
    """Candles whose OHLC values are impossible (e.g. high below the close)."""
    bad = (
        (df["high"] < df[["open", "close", "low"]].max(axis=1))
        | (df["low"] > df[["open", "close"]].min(axis=1))
        | (df[["open", "high", "low", "close"]] <= 0).any(axis=1)
        | df[["open", "high", "low", "close"]].isna().any(axis=1)
    )
    return df[bad]
