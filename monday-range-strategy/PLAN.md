# Plan: automating the Monday Range + liquidity sweep strategy

**No tool can deliver 99% accurate trade outcomes.** At 1:3 risk-reward the break-even win rate is 25%, and sweep-reversal setups like this typically win well under half the time. What can be near-100% is the deterministic part: the bot detecting and executing the rules exactly as specified. For the probabilistic part, the realistic goal is calibrated probabilities (when it says 40%, about 40% win) and positive expectancy after fees.

## 1. Deterministic part (rules engine)

These need an exact definition each, because the public description is ambiguous. The defaults below are assumptions, and all should be parameters.

| Component | Proposed definition |
|---|---|
| **Monday range** | High and low of Monday 00:00–23:59 UTC, from M15 candles on one fixed venue (e.g. Binance BTCUSDT perp) |
| **Sweep** | From Tuesday, an M15 candle makes a new high/low for the week beyond the level by at least a minimum distance, and price closes back inside the range within N bars of that extreme. A later poke across the level that makes no new weekly extreme is a retest, not a sweep |
| **CISD** | After the sweep, a candle closes through the open of the first candle in the consecutive run that drove price into the extreme |
| **MSB** | A candle closes beyond the most recent confirmed swing point (fractal pivot, k bars each side) against the sweep direction |
| **Entry** | Market at the close of the confirming candle |
| **Stop** | Beyond the sweep extreme plus a buffer (e.g. 0.1 × ATR) |
| **Target** | Opposite side of the range, with a partial exit at mid-range |
| **Sizing** | Fixed % of equity at risk (e.g. 0.5–1%), position size = risk ÷ stop distance |
| **Limits** | Skip if risk-reward is below a minimum; max one open trade; time stop at week end |

## 2. Probabilistic part (recommendation layer)

The question it answers: given a valid setup, what is the probability the target is hit before the stop, and is the expected value positive after costs?

The binding constraint is sample size. BTC has roughly 450 weeks of usable history, so there are a few hundred setups at most.

| Approach | Strengths | Weaknesses | Verdict |
|---|---|---|---|
| **Bucketed base rates with Bayesian shrinkage** (win rate by sweep side, range size, day, trend) | Transparent, robust on small samples, gives honest uncertainty bands | Handles only 2–3 features at once | **Start here** |
| **Regularized logistic regression as a take/skip filter** (meta-labeling) | Combines 5–8 features, easy to calibrate | Needs about 300+ setups, drifts with regime | Add second, only if it beats the base rates out-of-sample |
| **Gradient boosting / neural nets** | Captures interactions | Will overfit a few hundred samples | Avoid |
| **LLM or discretionary chart reading** | Flexible | Not backtestable, not calibrated | Avoid for the decision; fine for writing the journal |

Candidate features: Monday range ÷ ATR, sweep depth, time from sweep to confirmation, CISD vs MSB, weekly/daily trend alignment, day of week, session, funding rate.

Validation is walk-forward only, judged by Brier score, a reliability curve and expectancy net of costs. Each recommendation is output as "P(win) 38% (CI 29–47%), EV +0.4R → take / skip".

## 3. Edge cases to decide up front

- **Week boundary:** UTC Monday is the chosen definition; keep it a parameter and run New York Monday once as a robustness check.
- **Both sides swept:** allow a second trade, or only the first sweep per week?
- **Sweep without a close back inside:** that is a breakout, not a sweep; no trade or the separate breakout-retest variant.
- **Stop and target inside the same M15 bar:** resolve with 1-minute data, otherwise count it as a loss.
- **Pivot look-ahead:** a swing is only confirmed k bars later; the backtest must not use it earlier.
- **Tiny or huge Monday range:** a tiny range puts the target too close to cover costs, and a huge one may never be swept.
- **Venue differences:** wicks differ across exchanges, so a sweep on one may not exist on another.
- **Macro news (CPI, FOMC):** sweeps there behave differently; flag as a feature or block.
- **Gold and other non-24/7 markets:** out of scope for now; weekend gaps and a different Monday session would need separate handling.
- **Costs:** fees, slippage and funding must be in the backtest.

## 4. Build phases

1. **Data:** Python, `ccxt`, M15 and 1-minute candles stored in Parquet, with gap checks.
2. **Rules engine:** pure functions for range, sweep, CISD and MSB, unit-tested against 30–50 hand-labeled setups from charts. This is where the 99% target applies.
3. **Backtester:** event-driven, with costs, producing a setup table with outcomes in R.
4. **Probability layer:** base rates first, then the logistic filter, walk-forward validated.
5. **Alerts only:** a Telegram message with levels, size, P(win) and EV; the trade is placed manually. Run 8–12 weeks and compare live against the backtest.
6. **Auto-execution:** exchange testnet, then small size, with a kill switch and daily/weekly loss limits.

## 5. Alternatives

- **Stay semi-automatic (stop at phase 5):** most of the benefit, far less operational risk. Recommended until live results match the backtest.
- **Limit entry on a retrace after confirmation:** better risk-reward, but misses the trades that never retrace.
- **Add the general 4H range variant:** more setups for the statistics, but it is a different distribution, so model it separately.

If the backtest in phase 3 shows no edge after costs, the probability layer cannot create one, and the project should stop there.

## Status

- **Phases 1–2 are built** (data download and rules engine, with tests). Phases 3–6 are not started.
- **First chart review done:** 20 setups and 10 empty weeks were checked against charts (verdicts in `output/review_v1/review.csv`). It exposed stale sweeps and a confirmation window that started too early; both were fixed by the new-weekly-extreme rule. The current pack in `output/review/` still has unreviewed rows.

Commands (run from this folder):

| Command | What it does |
|---|---|
| `uv run python -m monday_range download --timeframe 15m` | Download or update candles (also `--timeframe 1m`) |
| `uv run python -m monday_range check` | Report gaps and invalid candles |
| `uv run python -m monday_range scan` | List every setup the rules find, saved to `output/setups.csv` |
| `uv run python -m monday_range review` | Draw charts of 20 setups and 10 empty weeks into `output/review/` for manual checking |
| `uv run pytest` | Run the tests |

## Decisions

- **Language:** Python for every phase, so the backtest and the live bot share one rules engine.
- **Instrument:** BTC only.
- **Week boundary:** Monday 00:00–23:59 UTC, matching the exchange daily candle.

## Open decisions

- **Venue:** Binance BTCUSDT perpetual is assumed as the price source until decided otherwise.
