# Specification: Feed grouped by X account, with collapsible sections

## Context

The Feed tab currently shows all collected posts in one list, newest first, so posts from different authors are interleaved. You want one collapsible section per X account so each author's posts stay together.

This changes only how the Feed is displayed. Collection, interpretation, the matrix and stored data are untouched.

## Behaviour

### Sections

- **One section per monitored account.** A post belongs to the account it was collected from, so a repost stays under the account that reposted it (still labelled "repost of @author").
- **Section order:** the account with the most recent post comes first; accounts without posts go last, alphabetically.
- **Inside a section:** posts newest first, each drawn exactly as today (text, images, signal line, levels, evidence quote, "Show text read from images").
- **Accounts with no posts yet** still get a section, showing "no posts yet", so it is clear they are being monitored.
- **Paused accounts** (switched off in Accounts) keep their section, marked "paused".
- **Removed accounts:** their already-collected posts stay in the Feed in a section marked "removed".

### Section header (always visible, one line)

| Element | Content |
|---|---|
| Arrow | Points right when collapsed, down when expanded |
| Handle | `@handle`, bold |
| Latest stance | Coloured word from the account's most recent non-noise signal: Bullish (green), Bearish (red), Neutral (grey); blank if none |
| Counts | "12 posts", plus "3 waiting" in amber if any are waiting for interpretation or failed |
| Latest post | Age of the newest post, e.g. "4h ago" |

- Tapping anywhere on the header expands or collapses the section.
- While scrolling through an expanded section, its header stays pinned at the top of the screen, so you always know whose posts you are reading.

### Expanding and collapsing

- **Default: all sections collapsed.** Opening the Feed shows a compact overview of accounts; you open the ones you want.
- **The app remembers** which sections are open, including after it is closed and reopened.
- **"Expand all" and "Collapse all"** buttons sit at the top of the Feed.

### Long sections

- An expanded section shows its 20 newest posts, followed by a "Show 20 more (N older)" row when there are more.
- The Feed keeps the 500 most recent posts overall (currently 200), so a very active account is less likely to push quieter accounts out.

### Empty state

- With no accounts at all, the Feed shows the current message asking you to add accounts and log in to X.

## Not included

- An "unread" or "new posts" counter per account.
- Filtering or searching posts.
- Changes to the Matrix or Accounts screens.

## Implementation

| File | Change |
|---|---|
| `app/src/main/java/com/trading/monitor/ui/FeedScreen.kt` | Replace the flat list with sections: pinned header item + post items per account; "Expand all / Collapse all" row; "Show more" row. Reuse the existing `PostItem` unchanged, and `fmtAge`, `Green`/`Red`/`Amber`/`Muted` from `ui/AppUi.kt`. |
| `app/src/main/java/com/trading/monitor/FeedGrouping.kt` (new) | Pure function `groupFeed(accounts, posts, signals)` returning ordered sections (handle, state: active / paused / removed, posts, waiting count, latest stance, latest post time). No Android imports, so it is unit-tested. |
| `app/src/main/java/com/trading/monitor/Settings.kt` | New `expandedFeedAccounts: Set<String>` stored in the existing preferences. |
| `app/src/main/java/com/trading/monitor/db/Db.kt` | `recentPosts()` limit 200 → 500. No schema change, so stored data is kept. |
| `app/src/test/java/com/trading/monitor/FeedGroupingTest.kt` (new) | Tests listed below. |

## Task breakdown and model assignment

I act as orchestrator (Fable 5.2): I fix the shared contract below, delegate tasks 1–3 to subagents, then review, build, test and verify myself.

### Shared contract (fixed up front so tasks 1–3 can run in parallel)

```kotlin
// FeedGrouping.kt, package com.trading.monitor
enum class SectionState { ACTIVE, PAUSED, REMOVED }
data class FeedSection(
    val handle: String,
    val state: SectionState,
    val posts: List<Post>,          // newest first
    val waiting: Int,               // posts with status NEW or ERROR
    val latestStance: Direction?,   // from the newest non-noise signal, else null
    val latestPostMs: Long?,        // null when the account has no posts
)
fun groupFeed(accounts: List<Account>, posts: List<Post>, signals: List<Signal>): List<FeedSection>

// Settings.kt
var expandedFeedAccounts: Set<String>
```

### Decision record

Before any code changes, I save this specification (behaviour, contract, task breakdown and model choices) in the project as `monitoring/docs/decisions/2026-11-12-feed-sections-per-account.md`. Future specifications go in the same folder, one dated file each, so the architecture decisions can be tracked. After verification I add a short "Outcome" section to the file (what was built, test results, any deviations).

### Tasks

| # | Task | Files (no overlap between tasks) | Model | Why this model | Depends on |
|---|---|---|---|---|---|
| 0 | Save this specification as the decision record | `docs/decisions/2026-11-12-feed-sections-per-account.md` (new) | **Fable 5.2** (me) | A direct copy of text I already hold; delegating it would cost more than doing it | — |
| 1 | Add `expandedFeedAccounts` to settings; raise the Feed query limit from 200 to 500 | `Settings.kt`, `db/Db.kt` | **Haiku** | Two small, fully specified edits that copy an existing pattern; no reasoning needed | — |
| 2 | Write `groupFeed` and its unit tests | `FeedGrouping.kt` (new), `FeedGroupingTest.kt` (new) | **Sonnet** | Standard, well-specified logic plus test writing; several ordering and edge-case rules to get right, but no design decisions left open | Contract only |
| 3 | Rebuild the Feed screen with sections, pinned headers, expand/collapse, "Expand all / Collapse all", "Show 20 more" | `ui/FeedScreen.kt` | **Sonnet** | Ordinary feature work in an existing Compose screen following existing patterns; moderate size, low risk | Contract only |
| 4 | Review the three diffs against this specification, build, run all unit tests, fix integration issues | whole project | **Fable 5.2** (me) | Integration and review are the orchestrator's responsibility and need the full project context I already hold | 1, 2, 3 |
| 5 | Verify on the emulator, then install on the tablet | — | **Fable 5.2** (me) | Needs the build tools and devices in this session, and judgment on what the screenshots show | 4 |

Tasks 1, 2 and 3 run in parallel: they touch different files and share only the contract above.

### Acceptance criteria

- **Task 1:** `Settings.expandedFeedAccounts` reads and writes a string set in the existing preferences, defaulting to empty; `recentPosts()` returns up to 500 posts; nothing else changes.
- **Task 2:** `groupFeed` matches the contract and the Behaviour section (order by newest post; empty accounts last, alphabetical; reposts under the collecting account; paused and removed states; waiting count; latest stance ignores noise). The file has no Android imports. Tests cover each of those rules.
- **Task 3:** every item in the Behaviour section is implemented; the existing `PostItem` is reused unchanged; all sections start collapsed; open sections come from and are saved to `Settings.expandedFeedAccounts`.
- **Tasks 4–5:** the project compiles without new warnings, all unit tests pass (26 existing plus the new ones), and the Verification steps below pass.

### Rules for the delegated tasks

- **No builds by subagents:** they write code only. Parallel Gradle runs would collide, and a half-finished sibling file would break their compile. I build once all three are back.
- **Escalation:** if a task's output fails review, I send it back once with the specific defects. If it fails again, I redo it one tier up (Haiku → Sonnet → me).
- **Cost note:** each subagent starts without this conversation's context, so each prompt carries the contract, the relevant existing code and its acceptance criteria. Task 1 is small enough that delegating it saves little; it goes to Haiku because you asked for the cheapest capable model per change.

## Verification

- **Unit tests** (`.\gradlew test`): section order by newest post; empty accounts last and alphabetical; repost grouped under the collecting account; removed and paused states; waiting count includes failed posts; latest stance ignores noise signals.
- **Emulator:** add a second account, run "Check now", then confirm: two collapsed sections on opening; tap expands one only; header stays pinned while scrolling; "Expand all / Collapse all" work; open sections are remembered after closing and reopening the app; "Show 20 more" appears for an account with over 20 posts.
- **Tablet:** install the new build over the existing one and confirm the Feed shows the same sections with existing data intact.

## Outcome (2026-11-12)

- **Built as specified.** Task 1 (Haiku), tasks 2 and 3 (Sonnet) were each accepted on first review; no escalation was needed. Integration, review and verification by Fable 5.2.
- **Tests:** 36 unit tests pass (26 existing, 10 new in `FeedGroupingTest`). The build has no warnings.
- **Verified on the emulator:** sections start collapsed and are ordered by newest post; tapping expands one section; the header stays pinned while scrolling; open sections are remembered after an app restart; "Collapse all" works; an account without posts shows "no posts yet".
- **Not verified:** the "Show 20 more" row (no test account had more than 20 posts) and "Expand all" (same code path as "Collapse all").
- **Installed on the tablet** over the previous build; the app starts without crashes.
- **Found during verification, not part of this change:** X shortens long posts on profile pages, so their collected text ends in "Show more" and the rest of the post is missing.
