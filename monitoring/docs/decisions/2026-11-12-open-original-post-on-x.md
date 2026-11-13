# Specification: link from each post to the original on X

Status: approved 2026-11-12.

## Context

The Feed shows the collected text and images of each post, but there is no way to get from a post in the app to the original on X. That matters for checking context: replies, the full text of posts that X shortens ("Show more"), videos, and whether the post was edited or deleted.

This adds a link on every post in the Feed that opens the original post.

## Behaviour

### The link

- **Where:** at the right end of each post's first line (the line with the handle, age and status), on every post in the Feed, in every state (interpreted, waiting, skipped, error).
- **Label:** "Open on X", in the app's link colour, the same style as "Show text read from images".
- **Tap target:** at least 48 dp high so it is easy to hit on the tablet.

### What tapping does

- Opens `https://x.com/<author>/status/<post id>`.
- **On the tablet:** Android offers the link to the X app, which is installed and logged in, so the post opens there. If the X app does not take the link, it opens in the browser.
- **Returning:** the Back button brings you back to the Feed at the same position, with the same sections open.
- **If nothing can open the link** (no browser and no X app), the app does nothing and does not crash.

### Reposts

- For a repost, the link goes to the original author's post, which is the post the Feed is showing. The line already says "repost of @author".

### Deleted or unavailable posts

- If the post was deleted or the account became private, X shows its own "post unavailable" page. The app does not check this in advance.

## Not included

- Opening the post inside the app's own embedded browser.
- Copying or sharing the link.
- A link to the account's profile.
- Links in the Matrix or Accounts screens.
- Collecting the full text of shortened posts (a separate change).

## Design

- **No stored-data change.** Each stored post already has its id and author, which is all the address needs, so posts collected before this change get the link too.
- **Address builder:** a small pure function `postUrl(post: Post): String` in `FeedGrouping.kt`, unit-tested.
- **Opening:** Compose's standard `LocalUriHandler.current.openUri(url)`, wrapped so that a missing handler is ignored.

| File | Change |
|---|---|
| `app/src/main/java/com/trading/monitor/FeedGrouping.kt` | Add `fun postUrl(post: Post): String = "https://x.com/${post.author}/status/${post.id}"` |
| `app/src/main/java/com/trading/monitor/ui/FeedScreen.kt` | In `PostItem`, add a spacer and the "Open on X" link at the end of the first row |
| `app/src/test/java/com/trading/monitor/FeedGroupingTest.kt` | Two tests: own post, and repost (address uses the original author) |

## Task breakdown and model assignment

| # | Task | Model | Why this model | Depends on |
|---|---|---|---|---|
| 1 | Add `postUrl`, its two tests, and the link in `PostItem` | **Haiku** | About 20 lines across three files, fully specified, following patterns already in those files. One task rather than three, because the pieces are too small to be worth separate agents and the files are not shared with any other work | — |
| 2 | Review the diff, build, run all unit tests | **Fable 5.2** (orchestrator) | Review and integration stay with the orchestrator | 1 |
| 3 | Verify on the emulator, then install on the tablet and confirm the link opens the X app | **Fable 5.2** (orchestrator) | Needs the devices in this session and judgment on what the screens show | 2 |

Escalation: if task 1 fails review, it goes back once with the specific defects, then to Sonnet.

### Acceptance criteria

- **Task 1:** `postUrl` returns the address above; both tests are correct; the link appears at the right end of the first row of every post; tapping calls `openUri` with `postUrl(post)`; a failure to open is caught; nothing else in `PostItem` changes. No builds are run by the subagent.
- **Tasks 2–3:** the project compiles without new warnings; all unit tests pass (36 existing plus 2 new); the checks below pass.

## Verification

- **Unit tests** (`.\gradlew test`): address for an own post; address for a repost.
- **Emulator:** expand a section, tap "Open on X" on a post, confirm the browser opens that post's page; press Back and confirm the Feed is where it was.
- **Tablet:** tap "Open on X" and confirm the post opens in the X app; press Back and confirm the return to the Feed.

## Outcome (2026-11-12)

- **Built as specified.** Task 1 (Haiku) was accepted on first review; no escalation. Review, build and verification by Fable 5.2.
- **Tests:** 38 unit tests pass (36 existing, 2 new). The build has no warnings.
- **Verified on the emulator:** "Open on X" appears at the right end of each post's first line; tapping it sends an `https://x.com/...` address to the browser; Back returns to the Feed at the same position with the same section open.
- **Not verified:** the post page itself was not seen, because the emulator's Chrome stopped at its first-run sign-in screen, and Android's log hides the full address. The exact address is covered by the unit tests.
- **Verified on the tablet (Tab S10+, Android 16):** the build installed over the previous one with data intact; tapping "Open on X" on a @doctorcatx post opened that exact post in the X app, with its full text and replies; Back returned to the Feed.
