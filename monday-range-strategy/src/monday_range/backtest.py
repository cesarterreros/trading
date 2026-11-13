"""Event-driven backtest of the setups on 1-minute candles, with costs.

Each trade is entered at the close of the confirming candle and followed minute
by minute to its stop, its targets, or the end of the week. When a stop and a
target fall inside the same minute the stop is assumed to come first.

Results are in R: profit or loss as a multiple of the amount risked, where the
risk is the distance from the planned entry to the stop.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from .config import StrategyParams
from .rules import find_setups, setups_frame


@dataclass(frozen=True)
class BacktestParams:
    # Exchange fee per side, as a fraction of traded value (0.05% = Binance taker).
    fee_pct: float = 0.0005
    # Adverse price move assumed on market fills (entry, stop, week-end exit).
    slippage_pct: float = 0.0002
    # Funding assumed every 8 hours: longs pay it, shorts receive it. Real funding
    # varies in size and sign; this is a flat stand-in for its typical level.
    funding_per_8h: float = 0.0001
    # Share of the position closed at mid-range; 0 disables the partial exit.
    partial_fraction: float = 0.5
    # Move the stop to the entry price once the partial exit is filled.
    breakeven_after_partial: bool = False
    # Fraction of equity risked per trade, used only for the equity figure.
    risk_pct: float = 0.01
    # Skip a setup that appears while another trade is still open.
    one_trade_at_a_time: bool = True
    # The strategy trades about once per week: take the first qualifying setup
    # of the week and ignore the rest. None removes the limit.
    max_trades_per_week: int | None = 1


def _first(mask: np.ndarray) -> int | None:
    if mask.size == 0:
        return None
    i = int(mask.argmax())
    return i if mask[i] else None


def simulate_trade(s, minute: pd.DataFrame, bp: BacktestParams) -> dict | None:
    """Follow one setup (a row of the setups frame) on 1-minute candles."""
    idx = minute.index
    week_end = s["week_start"] + pd.Timedelta(days=7)
    a, b = idx.searchsorted(s["entry_time"]), idx.searchsorted(week_end)
    if a >= b:
        return None  # no candles after the entry yet
    high = minute["high"].to_numpy(float)[a:b]
    low = minute["low"].to_numpy(float)[a:b]
    close = minute["close"].to_numpy(float)[a:b]
    times = idx[a:b]

    d = 1.0 if s["side"] == "long" else -1.0
    entry, stop, target, partial = (float(s[k]) for k in ("entry", "stop", "target", "partial_target"))
    risk = abs(entry - stop)
    entry_fill = entry * (1 + d * bp.slippage_pct)

    def touched(level: float, favourable: bool) -> np.ndarray:
        up = (d > 0) == favourable  # is the level above the entry?
        return high >= level if up else low <= level

    stop_hit, target_hit = touched(stop, False), touched(target, True)
    i_stop, i_target = _first(stop_hit), _first(target_hit)
    use_partial = bp.partial_fraction > 0 and d * (partial - entry) > 0 and d * (target - partial) > 0
    i_partial = _first(touched(partial, True)) if use_partial else None

    exits: list[tuple[float, float, float, int, str]] = []  # share, fill, reference, minute, reason

    def market(share: float, ref: float, i: int, reason: str) -> None:
        exits.append((share, ref * (1 - d * bp.slippage_pct), ref, i, reason))

    def limit(share: float, ref: float, i: int, reason: str) -> None:
        exits.append((share, ref, ref, i, reason))

    last = len(close) - 1
    if i_partial is not None and (i_stop is None or i_partial < i_stop):
        f = bp.partial_fraction
        limit(f, partial, i_partial, "partial")
        new_stop = entry if bp.breakeven_after_partial else stop
        later_stop = _first(touched(new_stop, False)[i_partial + 1 :])
        later_stop = None if later_stop is None else later_stop + i_partial + 1
        later_target = _first(target_hit[i_partial:])
        later_target = None if later_target is None else later_target + i_partial
        if later_stop is not None and (later_target is None or later_stop <= later_target):
            market(1 - f, new_stop, later_stop, "stop")
        elif later_target is not None:
            limit(1 - f, target, later_target, "target")
        else:
            market(1 - f, close[last], last, "week end")
    elif i_stop is not None and (i_target is None or i_stop <= i_target):
        market(1.0, stop, i_stop, "stop")
    elif i_target is not None:
        limit(1.0, target, i_target, "target")
    else:
        market(1.0, close[last], last, "week end")

    gross = sum(share * d * (ref - entry) for share, _, ref, _, _ in exits)
    pnl = sum(share * d * (fill - entry_fill) for share, fill, _, _, _ in exits)
    fees = bp.fee_pct * (entry_fill + sum(share * fill for share, fill, _, _, _ in exits))
    hours = sum(share * (i + 1) / 60 for share, _, _, i, _ in exits)
    funding = d * bp.funding_per_8h * entry_fill * hours / 8
    return {
        "week_start": s["week_start"],
        "side": s["side"],
        "confirmation": s["confirmation"],
        "entry_time": s["entry_time"],
        "exit_time": times[exits[-1][3]] + pd.Timedelta(minutes=1),
        "entry": entry,
        "stop": stop,
        "target": target,
        "rr": float(s["rr"]),
        "closes_outside": int(s["closes_outside"]),
        "outcome": "+".join(reason for *_, reason in exits),
        "r_gross": gross / risk,
        "r_net": (pnl - fees - funding) / risk,
        "cost_r": (gross - (pnl - fees - funding)) / risk,
    }


def run_backtest(
    candles: pd.DataFrame,
    minute: pd.DataFrame,
    sp: StrategyParams = StrategyParams(),
    bp: BacktestParams = BacktestParams(),
) -> tuple[pd.DataFrame, dict]:
    """Returns the trades taken and a count of setups found, taken and skipped."""
    setups = setups_frame(find_setups(candles, sp))
    counts = {
        "setups": len(setups),
        "skipped: reward:risk below minimum": 0,
        "skipped: already traded this week": 0,
        "skipped: trade already open": 0,
    }
    trades = []
    busy_until = None
    per_week: dict = {}
    for _, s in setups.sort_values("entry_time").iterrows():
        if not s["meets_min_rr"]:
            counts["skipped: reward:risk below minimum"] += 1
            continue
        if bp.max_trades_per_week is not None and per_week.get(s["week_start"], 0) >= bp.max_trades_per_week:
            counts["skipped: already traded this week"] += 1
            continue
        if bp.one_trade_at_a_time and busy_until is not None and s["entry_time"] < busy_until:
            counts["skipped: trade already open"] += 1
            continue
        trade = simulate_trade(s, minute, bp)
        if trade is None:
            continue
        trades.append(trade)
        busy_until = trade["exit_time"]
        per_week[s["week_start"]] = per_week.get(s["week_start"], 0) + 1
    counts["trades"] = len(trades)
    return pd.DataFrame(trades), counts


def _max_drawdown(r: np.ndarray) -> float:
    curve = np.concatenate([[0.0], np.cumsum(r)])
    return float((np.maximum.accumulate(curve) - curve).max())


def summarize(trades: pd.DataFrame, bp: BacktestParams = BacktestParams(), seed: int = 0) -> dict:
    """Headline statistics. The interval is a bootstrap 95% range for the average net R."""
    if trades.empty:
        return {"trades": 0}
    r = trades["r_net"].to_numpy(float)
    rng = np.random.default_rng(seed)
    means = rng.choice(r, size=(10_000, len(r)), replace=True).mean(axis=1)
    wins, losses = r[r > 0].sum(), -r[r < 0].sum()
    years = (trades["exit_time"].max() - trades["entry_time"].min()).days / 365.25
    return {
        "trades": len(r),
        "trades per year": len(r) / years if years > 0 else float("nan"),
        "winning trades %": 100 * float((r > 0).mean()),
        "reached full target %": 100 * float(trades["outcome"].str.endswith("target").mean()),
        "average R before costs": float(trades["r_gross"].mean()),
        "average R after costs": float(r.mean()),
        "average R, 95% low": float(np.percentile(means, 2.5)),
        "average R, 95% high": float(np.percentile(means, 97.5)),
        "total R after costs": float(r.sum()),
        "profit factor": float(wins / losses) if losses > 0 else float("inf"),
        "max drawdown (R)": _max_drawdown(r),
        "longest losing streak": int(max((len(run) for run in "".join("L" if x <= 0 else "W" for x in r).split("W")), default=0)),
        f"equity multiple at {bp.risk_pct:.0%} risk": float(np.prod(1 + bp.risk_pct * r)),
    }


def by_year(trades: pd.DataFrame) -> pd.DataFrame:
    g = trades.groupby(trades["entry_time"].dt.year)["r_net"]
    return pd.DataFrame({"trades": g.size(), "total R": g.sum(), "average R": g.mean()})


def plot_cumulative(variants: dict[str, pd.DataFrame], path) -> None:
    """Cumulative net R over time, one line per variant."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    surface, ink, soft, grid = "#fcfcfb", "#0b0b0b", "#52514e", "#e6e5e0"
    colors = ["#2a78d6", "#eb6834"]
    fig, ax = plt.subplots(figsize=(12, 6), facecolor=surface)
    fig.subplots_adjust(left=0.07, right=0.84, top=0.86, bottom=0.09)
    ax.set_facecolor(surface)
    ends = []
    for (name, trades), color in zip(variants.items(), colors):
        if trades.empty:
            continue
        t = trades.sort_values("exit_time")
        x = [t["entry_time"].iloc[0], *t["exit_time"]]
        y = np.concatenate([[0.0], t["r_net"].cumsum().to_numpy()])
        ax.step(x, y, where="post", color=color, linewidth=2, label=name)
        ends.append((y[-1], f"{name}: {y[-1]:+.1f} R"))
    ax.axhline(0, color=soft, linewidth=1)
    lo, hi = ax.get_ylim()
    gap, placed = (hi - lo) * 0.06, []
    for y, text in sorted(ends):
        y_text = y if not placed else max(y, placed[-1] + gap)
        placed.append(y_text)
        ax.annotate(text, xy=(1.0, y), xycoords=("axes fraction", "data"), xytext=(1.012, y_text),
                    textcoords=("axes fraction", "data"), fontsize=10, color=ink, va="center", annotation_clip=False)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(grid)
    ax.tick_params(colors=soft, labelsize=9, length=0)
    ax.grid(axis="y", color=grid, linewidth=0.6)
    ax.set_axisbelow(True)
    ax.set_ylabel("cumulative R", color=soft, fontsize=10)
    ax.legend(loc="lower left", frameon=False, fontsize=10, labelcolor=ink)
    fig.suptitle("Cumulative result after costs", x=0.07, ha="left", fontsize=14, color=ink, fontweight="bold")
    fig.text(0.07, 0.9, "Sum of trade results in R (1 R = the amount risked on one trade)", fontsize=10, color=soft)
    fig.savefig(path, dpi=110, facecolor=surface)
    plt.close(fig)
