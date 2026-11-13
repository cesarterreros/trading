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


def cmd_backtest(args) -> None:
    import pandas as pd

    from .backtest import BacktestParams, by_year, plot_cumulative, run_backtest, summarize

    candles = data.load(data.store_path(args.data_dir, args.exchange, args.symbol, "15m"))
    minute = data.load(data.store_path(args.data_dir, args.exchange, args.symbol, "1m"))
    bp = BacktestParams(
        partial_fraction=args.partial, breakeven_after_partial=args.breakeven,
        fee_pct=args.fee, slippage_pct=args.slippage,
        max_trades_per_week=args.per_week if args.per_week > 0 else None,
    )
    variants = {
        "no cap": StrategyParams(min_rr=args.min_rr),
        f"cap {args.cap}": StrategyParams(min_rr=args.min_rr, max_consecutive_closes_outside=args.cap),
    }
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    results, summary, counts, years = {}, {}, {}, {}
    for name, sp in variants.items():
        trades, counts[name] = run_backtest(candles, minute, sp, bp)
        results[name], summary[name], years[name] = trades, summarize(trades, bp), by_year(trades)
        trades.to_csv(out / f"trades_{name.replace(' ', '_')}.csv", index=False)

    pd.set_option("display.width", 200)
    table = pd.DataFrame(summary)
    table.to_csv(out / "summary.csv")
    print(f"{candles.index[0]:%Y-%m-%d} to {candles.index[-1]:%Y-%m-%d}; fee {bp.fee_pct:.3%} per side, "
          f"slippage {bp.slippage_pct:.3%}, partial exit {bp.partial_fraction:.0%} at mid-range, "
          f"breakeven after partial: {bp.breakeven_after_partial}, minimum reward:risk {args.min_rr:g}, "
          f"max trades per week: {bp.max_trades_per_week}\n")
    print(pd.DataFrame(counts).to_string())
    print()
    print(table.to_string(float_format=lambda v: f"{v:,.2f}"))
    print("\nby year (trades, total R, average R):")
    print(pd.concat(years, axis=1).to_string(float_format=lambda v: f"{v:,.2f}"))
    plot_cumulative(results, out / "cumulative_r.png")
    print(f"\ntrades, summary and chart saved to {out}")


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

    p = sub.add_parser("backtest", help="backtest the setups on 1m candles, with and without the strict-sweep cap")
    _common(p)
    p.add_argument("--cap", type=int, default=8, help="max consecutive 15m closes beyond the level (strict variant)")
    p.add_argument("--partial", type=float, default=0.5, help="share closed at mid-range; 0 disables")
    p.add_argument("--breakeven", action="store_true", help="move the stop to entry after the partial exit")
    p.add_argument("--fee", type=float, default=0.0005, help="fee per side as a fraction")
    p.add_argument("--slippage", type=float, default=0.0002, help="slippage on market fills as a fraction")
    p.add_argument("--min-rr", type=float, default=StrategyParams().min_rr, help="minimum reward:risk to take a trade")
    p.add_argument("--per-week", type=int, default=1, help="max trades per week; 0 removes the limit")
    p.add_argument("--out", default="output/backtest")
    p.set_defaults(func=cmd_backtest)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
