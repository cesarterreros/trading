# Specification: posts added by hand; automated reading of X removed

## Context

The app currently loads X profile pages in a hidden browser and extracts posts by script. That is scraping under X's terms, X throttled it during testing, and it already broke once when X changed its page layout. You decided (2026-11-12):

- **Remove the automated checks** of X accounts.
- **Add posts by hand**, by sharing a screenshot or copied text from the X app into this app.

The result stays within X's terms: this app never accesses X by software. The cost is that it stops being a monitor: the matrix reflects only the posts you add, and there are no alerts for new posts.

This supersedes `docs/decisions/2026-11-12-full-text-and-x-style-posts.md`: full text now comes from what you copy or capture, and a screenshot already looks exactly like X.

## Behaviour

### Adding a post

Three ways in, all ending on the same "Add post" screen:

| Way | How |
|---|---|
| Share a screenshot | From the screenshot toolbar or Gallery: Share → BTC Signal Monitor. Several images can be shared at once |
| Share text or the post link | In the X app: select the post text → Share, or the post's Share → BTC Signal Monitor |
| Inside the app | "Add post" button in the Feed: paste text, pick images |

- **One draft collects everything.** If you share again while the Add post screen is open (for example the link, then two screenshots), the new items are added to the same draft.
- **The post link is used only as text.** If the shared text contains an `x.com/<handle>/status/<id>` link, the app reads the account, the post ID and the exact post time from the link itself. The page is never loaded.

### The Add post screen

| Field | Content |
|---|---|
| Account | Pre-filled from the link, otherwise from an `@handle` recognised in the screenshot that matches one of your accounts. A list of your accounts to choose from, or type a new handle (it is added to Accounts) |
| Posted | Exact time when a link was shared. Otherwise "now", changeable to 1 h, 3 h, 6 h, 12 h, 1 day, 2 days, 3 days or 1 week ago |
| Text | What you shared or pasted, editable. May be empty when a screenshot carries the text |
| Images | Thumbnails of the shared screenshots, each removable; "Add image" opens the photo picker |
| Save / Cancel | Save needs an account and at least text or one image |

### After saving

- Screenshots are copied into the app's private storage, so they stay available if you delete them from the Gallery.
- Text in each screenshot is recognised on the tablet (as today), then DeepSeek interprets the pasted text and the recognised text together.
- DeepSeek is told that screenshot text may include buttons, counts and other people's replies, and to use only the main post by the chosen account. Spanish (X's translation) is accepted.
- The post appears in the Feed under its account, with its signal once interpreted.
- **Same post twice:** if a saved post has the same post ID (from a link) as an existing one, the new text and images are added to the existing post and it is interpreted again.

### Feed

- Posts added by hand show their screenshots at the full width of the section, keeping proportions, so the capture reads as it does on X.
- "Open on X" appears only when the post ID is known (a link was shared, or the post was collected earlier).
- Posts collected automatically before this change stay in the Feed as they are.

### What is removed

- The hidden browser, the page-reading script and the X login screen.
- In Accounts: the X login line, "Check now", "checked … ago" and reader errors.
- In Settings: "Minutes between checks". The monitoring switch becomes "Price and verdict alerts in the background".
- On the Matrix: "Check now" becomes "Refresh" (market data and any posts waiting for interpretation).

### What stays

- The matrix, verdict rules, level zones, account weights and hit rates.
- Market data, the background service for price-near-level and verdict-change alerts, and win/loss tracking.
- DeepSeek interpretation with the daily token cap.

### Tips shown on the Add post screen

- Expand the post ("Show more") before capturing.
- For charts, open the image full screen in X and capture that: larger labels are read more reliably.
- Crop out replies in the screenshot toolbar when possible.

## Not included

- Any loading of X pages by the app.
- Cropping or zooming images inside the app.
- X-styled redrawing of posts (avatar, counts): a screenshot already shows them.
- Reading posts from the clipboard automatically.

## Design

- **No database change.** A hand-added post without a link gets the ID `m-<time>`; one with a link uses the real post ID. Image paths are stored in the existing image field as `file://` addresses. Existing data is untouched.
- **Shared contract** (pure Kotlin in a new `ManualPost.kt`, package `com.trading.monitor`, unit-tested):

```kotlin
data class XLink(val handle: String, val postId: String, val timeMs: Long)
fun findXLink(text: String): XLink?                 // x.com or twitter.com …/<handle>/status/<id>
fun timeFromPostId(id: String): Long?               // post IDs encode their creation time
fun cleanSharedText(text: String): String           // text without the link and share boilerplate
fun guessHandle(text: String, known: List<String>): String?   // first @handle, preferring known accounts
fun manualPostId(nowMs: Long): String               // "m-<nowMs>"
fun isManualId(id: String): Boolean
// llm/SignalParser.kt
fun userPrompt(..., fromScreenshot: Boolean = false): String  // adds the "main post only" instruction
```

| File | Change |
|---|---|
| `ingest/XReader.kt`, `assets/extract_posts.js`, `ui/LoginScreen.kt` | Deleted |
| `MainActivity.kt` | Remove the hidden browser; receive shared text and images (on start and while open) into the draft |
| `AndroidManifest.xml` | Accept shares of `text/plain` and `image/*` (single and multiple) |
| `ingest/MonitorService.kt`, `ingest/Pipeline.kt` | Remove account polling; keep market loop, pending interpretation, outcome tracking; read images from local files; pass `fromScreenshot` |
| `ui/AddPostScreen.kt` (new) | The Add post screen and draft |
| `ui/FeedScreen.kt`, `ui/AccountsScreen.kt`, `ui/SettingsScreen.kt`, `ui/MatrixScreen.kt`, `ui/AppUi.kt`, `Settings.kt` | "Add post" button, full-width screenshots, removals listed above |
| `ManualPost.kt`, `llm/SignalParser.kt`, tests | Contract above with unit tests |
| `README.md` | Rewritten for the manual workflow |

## Task breakdown and model assignment

| # | Task | Model | Why this model | Depends on |
|---|---|---|---|---|
| 0 | Save this specification in `monitoring/docs/decisions/2026-11-12-manual-posts-no-automated-reading.md`; mark the full-text / X-style record as superseded | **Fable 5.2** (me) | Copying text I already hold | — |
| 1 | Remove automated reading: delete the reader, script and login screen; clean the service, pipeline, activity, Accounts, Settings and Matrix | **Sonnet** | A multi-file removal that must leave the app compiling and the service working; standard refactoring | — |
| 2 | `ManualPost.kt` functions, the screenshot instruction in the prompt, and unit tests | **Sonnet** | Text-pattern logic with edge cases (link forms, share boilerplate, handles) plus tests | Contract only |
| 3 | Share handling, the Add post screen, saving (copy images, create post, merge duplicates), Feed changes | **Sonnet** | The main feature: sizeable, ordinary Android and Compose work against a fixed contract | 1 and 2 |
| 4 | Rewrite `README.md` for the manual workflow | **Haiku** | Plain documentation from a list of facts I supply | — |
| 5 | Review all diffs, build, run all unit tests, fix integration issues | **Fable 5.2** (me) | Review and integration stay with the orchestrator | 1–4 |
| 6 | Verify on the emulator and on the tablet with the real X app | **Fable 5.2** (me) | Needs the devices and judgment on the results | 5 |

- Tasks 1, 2 and 4 run in parallel. Task 3 starts after 1 and 2, because it edits files that task 1 changes.
- Subagents write code only and run no builds. A task that fails review goes back once with the defects, then one tier up.

### Acceptance criteria

- **Task 1:** no code loads an X page; the three files are gone; the app compiles; the service still refreshes market data, interprets waiting posts, tracks outcomes and sends alerts; removed controls are gone from the screens.
- **Task 2:** functions match the contract; tests cover `x.com` and `twitter.com` links, links with query strings, text without a link, time from a known post ID, handle guessing with and without known accounts, and the prompt with and without the screenshot instruction.
- **Task 3:** each row of "Adding a post" and "The Add post screen" works; a second share joins the open draft; saving without account or content is blocked; images survive deletion from the Gallery; duplicate post IDs merge.
- **Task 4:** the README describes only the manual workflow, with no mention of X login or automatic checks.
- **Tasks 5–6:** no new build warnings; all unit tests pass (38 existing plus the new ones); the verification below passes.

## Verification

- **Unit tests** (`.\gradlew test`): link parsing, time from ID, text cleaning, handle guessing, prompt variants.
- **Emulator:** send a text share containing a post link by command and confirm account, time and "Open on X"; add a post with pasted text only; add one with an image; confirm each appears in the Feed and is interpreted; confirm no X page is requested (network log shows no `x.com` page loads).
- **Tablet:** install over the current build and confirm existing accounts, posts and signals are intact. In the X app, share a real post's link, then a screenshot of the same post with "Show more" expanded; confirm one post with full text, exact time, a signal, and levels reaching the Matrix. Delete the screenshot from the Gallery and confirm it still shows in the Feed.

## Amendment (2026-11-12): delete a post

Requested by the user after approval, because posts are now entered by hand and mistakes must be correctable.

### Behaviour

- Every post in the Feed has a "Delete" link on its first line, to the left of "Open on X".
- Tapping it asks for confirmation: "Delete this post? Its signal and screenshots are removed too. This cannot be undone." with Delete and Cancel.
- Deleting removes the post, its signal and its stored screenshots. The Matrix, level zones and the account's hit rate update at once, since they are computed from what remains.
- The account itself is kept, even if it has no posts left.
- If the post is being interpreted at that moment, the deletion waits for the interpretation to finish, so no signal is left behind without its post.

### Design

- `db/Db.kt`: `deletePost(id)` and `deleteSignal(postId)` queries. No schema change.
- `ingest/Pipeline.kt`: `deletePost(context, id)` takes the same lock as interpretation, deletes both rows and the folder `filesDir/posts/<id>`.
- `ui/FeedScreen.kt`: the link and the confirmation dialog in `PostItem`.

### Task

Added to task 3 (Sonnet), which was still running and already edits these three files.

### Acceptance and verification

- Deleting a hand-added post removes it from the Feed, removes its signal from the Matrix and deletes its image files; Cancel changes nothing.
- Emulator: add a post, confirm its signal reaches the Matrix, delete it, confirm the Feed, the Matrix and the private posts folder no longer contain it.

## Outcome (2026-11-13)

- **Built as specified, including the delete amendment.** Tasks 1, 2 and 3 (Sonnet) and task 4 (Haiku) were each accepted on first review; no escalation. Task 3 compiled on the first build. Review, integration and verification by Fable 5.2.
- **Tests:** 58 unit tests pass (38 existing, 20 new). The build has no warnings. No code that loads an X page remains; the only X address left is the "Open on X" link the user taps.
- **Changes made during review (Fable 5.2):**
  - Shared images are copied one after another, so several screenshots keep their order.
  - A notice appears on the Add post screen when a shared image cannot be read.
  - The link pattern no longer leaves "https://" behind when a link follows text without a space; a test covers it.
  - Feed rows no longer squeeze text letter by letter on a narrow screen: the button row scrolls sideways and labels stay on one line.
- **Verified on the emulator:** a shared text with a post link fills the account and the exact post time without any page load; a share arriving with the app closed opens the Add post screen; a second share joins the open draft; an image added through the photo picker is attached and, after Save, stored in the app's private folder; a post with an existing post ID is merged into the existing post; a text-only post appears in the Feed with "Delete" and without "Open on X"; Delete, after confirmation, removes the post and its folder.
- **Verified on the tablet (Tab S10+, Android 16):** installing over the previous build kept the accounts, posts and signals; a shared test post saved under @doctorcatx was interpreted by DeepSeek as bullish, trade setup, entry 70,500, stop 68,900, target 76,000, and the section header changed to "Bullish"; deleting it returned the section to 12 posts and "Neutral".
- **Not verified:**
  - A real share from the X app or the Samsung screenshot toolbar (the test shares were sent by command). What text X's share button actually sends is therefore unconfirmed.
  - Text recognition and DeepSeek on a real screenshot, including the "main post only" instruction.
  - Sharing several images at once, the "1 h ago … 1 week ago" choices, and the account guess from a screenshot.
  - That a screenshot survives deletion from the Gallery (the copy into private storage was confirmed; the Gallery deletion was not performed).
- **Known rough edges:** the remove (✕) button on a thumbnail is hard to see on a light image; "Add image" sits to the right of the thumbnails and scrolls with them.
- **Test leftovers:** the tablet is back to its previous data. The emulator still has test accounts and a test image merged into one @bitcoinmagazine post.
