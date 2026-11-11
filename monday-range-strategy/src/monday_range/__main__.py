"""Command line: python -m monday_range {download,check,scan}"""

from __future__ import annotations

import argparse
from pathlib import Path

from . import data
from .config import StrategyParams
from .rules import find_setups, monday_ranges, setups_frame


def _common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--exchange", default=data.DEFAULT_EXCHANGE)
    p.add_argument("--symbol", default=data.DEFAULT_SYMBOL)
    p.add_argument("--timeframe", default="15m")
    p.add_argument("--data-dir", default="data")


def _path(args) -> Path:
    return data.store_path(args.data_dir, args.exchange, args.symbol, args.timeframe)


def cmd_download(args) -> None:
    path = _path(args)

    def progress(ts, pending):
        print(f"\r  fetched up to {ts:%Y-%m-%d %H:%M}", end="", flush=True)

    df = data.update_store(path, args.exchange, args.symbol, args.timeframe, args.since, progress=progress)
    print(f"\n{len(df):,} candles, {df.index[0]} -> {df.index[-1]}\nsaved to {path}")


def cmd_check(args) -> None:
    df = data.load(_path(args))
    gaps = data.find_gaps(df, args.timeframe)
    bad = data.invalid_rows(df)
    print(f"{len(df):,} candles, {df.index[0]} -> {df.index[-1]}")
    print(f"gaps: {len(gaps)} ({int(gaps['missing_bars'].sum())} missing candles)")
    if len(gaps):
        print(gaps.to_string(index=False))
    print(f"invalid OHLC rows: {len(bad)}")


def cmd_scan(args) -> None:
    if args.timeframe != "15m":
        raise SystemExit("scan runs on 15m candles")
    df = data.load(_path(args))
    params = StrategyParams()
    ranges = monday_ranges(df, params)
    setups = setups_frame(find_setups(df, params))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    setups.to_csv(out, index=False)
    incomplete = sum(not r.complete for r in ranges)
    print(f"weeks: {len(ranges)} ({incomplete} skipped for an incomplete Monday)")
    print(f"setups: {len(setups)}")
    if len(setups):
        print(setups.groupby(["side", "confirmation"]).size().to_string())
        print(f"meeting min reward:risk ({params.min_rr}): {int(setups['meets_min_rr'].sum())}")
        cols = ["confirm_time", "side", "confirmation", "entry", "stop", "target", "rr"]
        print("\nlast 10 setups:")
        print(setups[cols].tail(10).to_string(index=False, float_format="%.2f"))
    print(f"\nfull list saved to {out}")


def cmd_review(args) -> None:
    from .review import VERDICTS, build_review_pack

    if args.timeframe != "15m":
        raise SystemExit("review runs on 15m candles")
    sheet = build_review_pack(data.load(_path(args)), args.out, force=args.force)
    print(sheet.groupby(["kind", "picked_because"], sort=False).size().to_string())
    print(f"\n{len(sheet)} charts saved to {args.out}")
    print(f"open {Path(args.out) / 'index.html'} to view them, then fill the verdict column in review.csv")
    print(f"verdicts: {VERDICTS}")


def main() -> None:
    parser = argparse.ArgumentParser(prog="monday_range")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("download", help="download or update candles")
    _common(p)
    p.add_argument("--since", default=data.DEFAULT_SINCE)
    p.set_defaults(func=cmd_download)

    p = sub.add_parser("check", help="report gaps and invalid candles")
    _common(p)
    p.set_defaults(func=cmd_check)

    p = sub.add_parser("scan", help="list every setup the rules engine finds")
    _common(p)
    p.add_argument("--out", default="output/setups.csv")
    p.set_defaults(func=cmd_scan)

    p = sub.add_parser("review", help="draw charts of selected setups and empty weeks for manual checking")
    _common(p)
    p.add_argument("--out", default="output/review")
    p.add_argument("--force", action="store_true", help="overwrite a review sheet that already has verdicts")
    p.set_defaults(func=cmd_review)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
