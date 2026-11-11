"""Review pack: chart images of selected setups and empty weeks, plus a verdict sheet.

The purpose is to compare what the rules engine detects against a human reading
of the chart. Charts stop a few candles after the entry so the outcome of the
trade does not influence the verdict.
"""

from __future__ import annotations

import html
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from .config import StrategyParams
from .rules import MondayRange, find_setups, monday_ranges, setups_frame

SURFACE = "#fcfcfb"
INK = "#0b0b0b"
INK_SOFT = "#52514e"
GRID = "#e6e5e0"
SHADE = "#f0efec"
UP = "#2a78d6"  # close above open
DOWN = "#d03b3b"  # close below open

BARS_AFTER_ENTRY = 8
BARS_BEFORE_SWEEP = 30
VERDICTS = "agree | wrong sweep | wrong confirmation | valid but would not take"
SHEET_COLUMNS = [
    "id", "image", "kind", "picked_because", "week_start", "side", "confirmation",
    "sweep_time", "confirm_time", "entry", "stop", "target", "rr", "verdict", "notes",
]


# ------------------------------------------------------------------- selection


def _spread(items: list, k: int) -> list:
    """k items evenly spaced through the list."""
    if len(items) <= k:
        return list(items)
    picks = sorted(set(np.linspace(0, len(items) - 1, k).round().astype(int)))
    return [items[i] for i in picks]


def select_setups(setups: pd.DataFrame, recent: int = 8, low_rr: int = 7) -> list[tuple[int, str]]:
    """(row position, reason) for the setups worth reviewing first."""
    chosen: dict[int, str] = {}
    for pos in range(max(len(setups) - recent, 0), len(setups)):
        chosen[pos] = "most recent"
    for pos in np.flatnonzero(setups["confirmation"] == "MSB"):
        chosen.setdefault(int(pos), "confirmed by MSB only")
    late = [int(p) for p in np.flatnonzero(setups["rr"] < 1) if int(p) not in chosen]
    for pos in _spread(late, low_rr):
        chosen[pos] = "reward:risk below 1"
    return sorted(chosen.items(), reverse=True)


def select_empty_weeks(
    df: pd.DataFrame, ranges: list[MondayRange], setups: pd.DataFrame, params: StrategyParams, k: int = 10
) -> list[MondayRange]:
    """Finished weeks with a complete Monday in which the engine found nothing."""
    used = set(setups["week_start"]) if len(setups) else set()
    data_end = df.index[-1] + pd.Timedelta(minutes=params.timeframe_minutes)
    empty = [
        r for r in ranges
        if r.complete and r.week_start not in used and r.week_start + pd.Timedelta(days=7) <= data_end
    ]
    return _spread(empty, k)


# --------------------------------------------------------------------- drawing


def _style(ax) -> None:
    ax.set_facecolor(SURFACE)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(GRID)
    ax.tick_params(colors=INK_SOFT, labelsize=9, length=0)
    ax.grid(axis="y", color=GRID, linewidth=0.6)
    ax.set_axisbelow(True)


def _candles(ax, d: pd.DataFrame) -> None:
    x = np.arange(len(d))
    o, h, l, c = (d[col].to_numpy(float) for col in ("open", "high", "low", "close"))
    color = np.where(c >= o, UP, DOWN)
    wick = 0.5 if len(d) > 200 else 1.0
    ax.vlines(x, l, h, colors=color, linewidth=wick)
    tiny = float(np.nanmean(c)) * 2e-5
    ax.bar(x, np.maximum(np.abs(c - o), tiny), bottom=np.minimum(o, c), width=0.7, color=color, linewidth=0)
    ax.set_xlim(-1, len(d))
    pad = (h.max() - l.min()) * 0.08
    ax.set_ylim(l.min() - pad, h.max() + pad)


def _time_ticks(ax, d: pd.DataFrame, daily: bool) -> None:
    if daily:
        pos = [i for i, t in enumerate(d.index) if t.hour == 0 and t.minute == 0]
        labels = [d.index[i].strftime("%a %d %b") for i in pos]
    else:
        pos = list(np.linspace(0, len(d) - 1, min(8, len(d))).round().astype(int))
        labels = [d.index[i].strftime("%a %d %b\n%H:%M") for i in pos]
    ax.set_xticks(pos, labels)


def _right_labels(ax, labels: list[tuple[float, str]]) -> None:
    """Label price levels in the right margin, nudged apart so they never overlap."""
    lo, hi = ax.get_ylim()
    gap = (hi - lo) * 0.045
    placed = []
    for y, text in sorted((y, t) for y, t in labels if lo <= y <= hi):
        y_text = y if not placed else max(y, placed[-1] + gap)
        placed.append(y_text)
        ax.annotate(
            text, xy=(1.0, y), xycoords=("axes fraction", "data"),
            xytext=(1.012, y_text), textcoords=("axes fraction", "data"),
            fontsize=9, color=INK, va="center", annotation_clip=False,
            arrowprops={"arrowstyle": "-", "color": INK_SOFT, "linewidth": 0.6},
        )


def _range_lines(ax, high: float, low: float) -> list[tuple[float, str]]:
    for level in (high, low):
        ax.axhline(level, color=INK_SOFT, linestyle=(0, (5, 4)), linewidth=1)
    return [(high, f"Monday High {high:,.1f}"), (low, f"Monday Low {low:,.1f}")]


def _shade_monday(ax, d: pd.DataFrame, week_start: pd.Timestamp) -> None:
    monday = np.flatnonzero((d.index >= week_start) & (d.index < week_start + pd.Timedelta(days=1)))
    if len(monday):
        ax.axvspan(monday[0] - 0.5, monday[-1] + 0.5, color=SHADE, zorder=0)
        ax.text(monday[0], 0.985, " Monday", transform=ax.get_xaxis_transform(), va="top", fontsize=9, color=INK_SOFT)


def _mark(ax, d: pd.DataFrame, when: pd.Timestamp, text: str, above: bool) -> None:
    if when not in d.index:
        return
    x = d.index.get_loc(when)
    y = d["high"].iloc[x] if above else d["low"].iloc[x]
    ax.annotate(
        text, xy=(x, y), xytext=(0, 22 if above else -22), textcoords="offset points",
        ha="center", va="bottom" if above else "top", fontsize=9, color=INK,
        arrowprops={"arrowstyle": "-|>", "color": INK, "linewidth": 0.9},
    )


def _save(fig, path: Path) -> None:
    fig.savefig(path, dpi=110, facecolor=SURFACE)
    plt.close(fig)


def draw_setup(df: pd.DataFrame, s: pd.Series, label: str, path: Path) -> None:
    i_sweep = min(df.index.get_loc(s["sweep_time"]), df.index.get_loc(s["confirm_time"]) - 8)
    i_confirm = df.index.get_loc(s["confirm_time"])
    i_end = min(i_confirm + BARS_AFTER_ENTRY, len(df) - 1)
    overview = df.iloc[df.index.get_loc(s["week_start"]) : i_end + 1]
    zoom = df.iloc[max(i_sweep - BARS_BEFORE_SWEEP, 0) : i_end + 1]
    short = s["side"] == "short"

    fig, (top, bottom) = plt.subplots(2, 1, figsize=(14, 9), height_ratios=[1, 1.5], facecolor=SURFACE)
    fig.subplots_adjust(left=0.06, right=0.86, top=0.875, bottom=0.07, hspace=0.28)
    fig.suptitle(
        f"{label}   {s['side'].upper()}: Monday {'High' if short else 'Low'} swept, confirmed by {s['confirmation']}",
        x=0.06, ha="left", fontsize=14, color=INK, fontweight="bold",
    )
    fig.text(
        0.06, 0.925,
        f"entry {s['entry']:,.1f}   stop {s['stop']:,.1f}   target {s['target']:,.1f}   "
        f"reward:risk {s['rr']:.2f}   (blue = up candle, red = down candle; times in UTC; "
        f"chart ends {BARS_AFTER_ENTRY} candles after entry)",
        fontsize=10, color=INK_SOFT,
    )

    _style(top)
    _candles(top, overview)
    _shade_monday(top, overview, s["week_start"])
    _right_labels(top, _range_lines(top, s["range_high"], s["range_low"]))
    _time_ticks(top, overview, daily=True)
    span = (overview.index.get_loc(zoom.index[0]), len(overview) - 1)
    top.axvspan(span[0] - 0.5, span[1] + 0.5, facecolor="none", edgecolor=INK_SOFT, linewidth=0.8)
    top.set_title("Week so far (boxed area is enlarged below)", loc="left", fontsize=10, color=INK_SOFT)

    _style(bottom)
    _candles(bottom, zoom)
    lo, hi = bottom.get_ylim()
    pad = (hi - lo) * 0.06
    bottom.set_ylim(min(lo, s["stop"] - pad, s["entry"] - pad), max(hi, s["stop"] + pad, s["entry"] + pad))
    labels = _range_lines(bottom, s["range_high"], s["range_low"])
    x_entry = zoom.index.get_loc(s["confirm_time"])
    for level, name in ((s["entry"], "Entry"), (s["stop"], "Stop"), (s["target"], "Target")):
        bottom.hlines(level, x_entry, len(zoom), colors=INK, linewidth=1.2)
        labels.append((level, f"{name} {level:,.1f}"))
    _right_labels(bottom, labels)
    if s["extreme_time"] == s["confirm_time"]:
        _mark(bottom, zoom, s["extreme_time"], "sweep extreme + confirmation", above=short)
    else:
        _mark(bottom, zoom, s["extreme_time"], "sweep extreme", above=short)
        _mark(bottom, zoom, s["confirm_time"], "confirmation", above=not short)
    _time_ticks(bottom, zoom, daily=False)
    bottom.set_title("Sweep and confirmation, 15-minute candles", loc="left", fontsize=10, color=INK_SOFT)
    _save(fig, path)


def draw_empty_week(df: pd.DataFrame, rng: MondayRange, label: str, path: Path) -> None:
    week = df[(df.index >= rng.week_start) & (df.index < rng.week_start + pd.Timedelta(days=7))]
    fig, ax = plt.subplots(figsize=(14, 6), facecolor=SURFACE)
    fig.subplots_adjust(left=0.06, right=0.86, top=0.86, bottom=0.09)
    fig.suptitle(
        f"{label}   Week of {rng.week_start:%d %b %Y}: no setup found",
        x=0.06, ha="left", fontsize=14, color=INK, fontweight="bold",
    )
    fig.text(
        0.06, 0.9,
        "Would you have traded a sweep this week? (full week, 15-minute candles, UTC; blue = up candle, red = down candle)",
        fontsize=10, color=INK_SOFT,
    )
    _style(ax)
    _candles(ax, week)
    _shade_monday(ax, week, rng.week_start)
    _right_labels(ax, _range_lines(ax, rng.high, rng.low))
    _time_ticks(ax, week, daily=True)
    _save(fig, path)


# ------------------------------------------------------------------------ pack


def _has_verdicts(sheet: Path) -> bool:
    if not sheet.exists():
        return False
    old = pd.read_csv(sheet, dtype=str)
    return "verdict" in old and old["verdict"].fillna("").str.strip().ne("").any()


def _write_index(rows: list[dict], out_dir: Path) -> None:
    parts = [
        "<!doctype html><meta charset='utf-8'><title>Setup review</title>",
        "<style>body{font:15px system-ui;margin:24px auto;max-width:1500px;padding:0 16px;color:#0b0b0b;background:#fcfcfb}"
        "img{width:100%;border:1px solid #e6e5e0;margin:8px 0 36px}p{color:#52514e;margin:0}</style>",
        "<h1>Setup review</h1>",
        f"<p>Record a verdict for each chart in review.csv: {html.escape(VERDICTS)}. "
        "For the empty weeks, write what you would have traded, or 'agree'.</p><br>",
    ]
    for r in rows:
        parts.append(f"<p>{html.escape(r['id'])}: picked because {html.escape(r['picked_because'])}</p>")
        parts.append(f"<img src='{html.escape(r['image'])}' alt='{html.escape(r['id'])}'>")
    (out_dir / "index.html").write_text("\n".join(parts), encoding="utf-8")


def build_review_pack(
    df: pd.DataFrame, out_dir: str | Path, params: StrategyParams = StrategyParams(), force: bool = False
) -> pd.DataFrame:
    out_dir = Path(out_dir)
    sheet = out_dir / "review.csv"
    if _has_verdicts(sheet) and not force:
        raise SystemExit(f"{sheet} already contains verdicts; use --force to overwrite it")
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.png"):
        old.unlink()

    ranges = monday_ranges(df, params)
    setups = setups_frame(find_setups(df, params))
    rows: list[dict] = []

    for n, (pos, reason) in enumerate(select_setups(setups), start=1):
        s = setups.iloc[pos]
        rid = f"S{n:02d}"
        image = f"{rid}_{s['confirm_time']:%Y-%m-%d}_{s['side']}.png"
        draw_setup(df, s, rid, out_dir / image)
        rows.append({
            "id": rid, "image": image, "kind": "setup", "picked_because": reason,
            "week_start": f"{s['week_start']:%Y-%m-%d}", "side": s["side"], "confirmation": s["confirmation"],
            "sweep_time": f"{s['sweep_time']:%Y-%m-%d %H:%M}", "confirm_time": f"{s['confirm_time']:%Y-%m-%d %H:%M}",
            "entry": round(s["entry"], 1), "stop": round(s["stop"], 1), "target": round(s["target"], 1),
            "rr": round(s["rr"], 2), "verdict": "", "notes": "",
        })

    for n, rng in enumerate(reversed(select_empty_weeks(df, ranges, setups, params)), start=1):
        rid = f"W{n:02d}"
        image = f"{rid}_{rng.week_start:%Y-%m-%d}_no_setup.png"
        draw_empty_week(df, rng, rid, out_dir / image)
        rows.append({
            "id": rid, "image": image, "kind": "empty week", "picked_because": "no setup found this week",
            "week_start": f"{rng.week_start:%Y-%m-%d}", "verdict": "", "notes": "",
        })

    result = pd.DataFrame(rows, columns=SHEET_COLUMNS)
    result.to_csv(sheet, index=False)
    _write_index(rows, out_dir)
    return result
