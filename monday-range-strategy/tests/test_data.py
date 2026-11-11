import pandas as pd

from monday_range import data

from conftest import make_week


def test_to_frame_sorts_and_removes_duplicates():
    rows = [
        [1704068100000, 2, 3, 1, 2, 10],
        [1704067200000, 1, 2, 0.5, 1.5, 10],
        [1704068100000, 2, 3, 1, 2, 10],
    ]
    df = data.to_frame(rows)
    assert list(df.index) == [pd.Timestamp("2024-01-01 00:00", tz="UTC"), pd.Timestamp("2024-01-01 00:15", tz="UTC")]
    assert df.iloc[0]["open"] == 1.0


def test_drop_unclosed_removes_the_running_candle():
    df = make_week().iloc[:4]
    now = pd.Timestamp("2024-01-01 00:50", tz="UTC")  # the 00:45 candle is still open
    assert len(data.drop_unclosed(df, "15m", now)) == 3


def test_find_gaps_reports_missing_ranges():
    df = make_week()
    assert data.find_gaps(df, "15m").empty
    holed = df.drop(index=df.index[10:13])
    gaps = data.find_gaps(holed, "15m")
    assert len(gaps) == 1
    assert gaps.loc[0, "first_missing"] == df.index[10]
    assert gaps.loc[0, "last_missing"] == df.index[12]
    assert gaps.loc[0, "missing_bars"] == 3


def test_invalid_rows_flags_impossible_candles():
    df = make_week()
    assert data.invalid_rows(df).empty
    df.iloc[5, df.columns.get_loc("high")] = 90.0  # high below open/close
    assert list(data.invalid_rows(df).index) == [df.index[5]]


def test_save_and_load_round_trip(tmp_path):
    df = make_week().iloc[:50]
    path = tmp_path / "sub" / "candles.parquet"
    data.save(df, path)
    pd.testing.assert_frame_equal(data.load(path), df, check_freq=False)


def test_store_path_is_filesystem_safe():
    path = data.store_path("data", "binanceusdm", "BTC/USDT:USDT", "15m")
    assert path.name == "binanceusdm_BTCUSDT_USDT_15m.parquet"
