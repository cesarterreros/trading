# Specification: full post text, and posts styled like X

Status: superseded on 2026-11-12 by `2026-11-12-manual-posts-no-automated-reading.md`, before any implementation. The user chose to remove automated reading of X and add posts by hand, so full text comes from what is copied or captured and a screenshot already looks like X. The task 1 findings below remain valid as a record of X's public page.

## Context

Two problems with how posts appear in the Feed today:

1. **Long posts are cut.** X shortens long posts on profile pages. The app stores only the visible part, ending in "Show more" / "Mostrar más". Example on the tablet: a @doctorcatx post stops at "which I made respectively Mostrar más", while the original goes on to name the 80–81K and 72.5K levels. DeepSeek never sees those levels, so the matrix misses them.
2. **Posts do not look like X.** The Feed shows plain text with fixed-size cropped images, so a post is harder to read and recognise than on X.

This change collects the complete text of every post and redraws posts in the Feed to match X's layout.

## Part A: full text

### Behaviour

- **Complete text:** every newly collected post is stored with its full text. The "Show more" label is never part of the stored text.
- **Already-collected posts:** when a shortened post is seen again on the account's profile page, its text is replaced by the full version. Shortened posts that have scrolled off the profile page stay as they are.
- **Re-interpretation:** if a post's text grows after it was already interpreted, it is sent to DeepSeek again and its signal is replaced, because the missing part may contain the levels.
- **Language:** the text is stored as the author wrote it. X's automatic translation (which your X app shows in Spanish) is not used.
- **Quoted posts:** when a post quotes another post, the quoted author and text are collected too. They are shown in the Feed as an embedded card and passed to DeepSeek as labelled context ("Quoted post"), never as the account's own statement.

### How the full text is obtained

Which method works depends on how X's logged-in page behaves, which is not known yet. Task 1 settles it on the tablet, in this order of preference:

1. **Expand in place:** before reading the page, the reader activates each "Show more" control. If the text expands on the profile page, no extra page loads are needed.
2. **Open the post page:** if "Show more" navigates away instead, the reader opens the post's own page for shortened posts only, with a limit of 5 such pages per check cycle and the same random pauses as between accounts. Remaining shortened posts are completed in later cycles.

Method 2 adds page loads under your X login, which raises the automated-reading risk described in the README. The limit keeps it small.

### Marking

- A post whose full text could not be obtained yet shows "shortened by X" next to its status, so you know to use "Open on X".

## Part B: posts styled like X

### What "the same as X" covers, and its limits

- **Matched:** layout, spacing, sizes, colours, text colouring, image arrangement, time format, and the reply / repost / like / view row.
- **Not possible to copy exactly:** X's typeface (Chirp) and X's icon artwork are proprietary. The app uses the tablet's system typeface and its own simple icons of the same shape and size. X's logo is not used.
- **Theme (decided by the user):** X's light theme, matching the X app on the tablet. Each post is drawn on a white card with X's light colours: text `#0F1419`, grey `#536471`, blue `#1D9BF0`, divider `#EFF3F4`, image and quote borders `#CFD9DE`. The rest of the app, including the information strip under each post, keeps the app's dark style.

### Post layout

| Element | Appearance |
|---|---|
| Repost line | Above the post: small repost icon and "<name> reposted", grey |
| Avatar | Round, 40 dp, at the left |
| Header line | **Display name** (bold) · verified badge if any · `@handle` grey · "·" · time grey |
| Time | X's short form: "45s", "12m", "5h", "3d"; older than 7 days as "Nov 9"; other years as "Nov 9, 2025" |
| Text | 15 sp, X's line spacing; `$BTC` cashtags, `#hashtags`, `@mentions` and links in X blue; line breaks kept |
| Images | Rounded 16 dp corners with a thin border. One image keeps its proportions (up to 510 dp high). Two sit side by side. Three: one tall on the left, two stacked on the right. Four: a 2×2 grid |
| Quoted post | Bordered rounded card under the text: small avatar, name, handle, time, text, first image |
| Action row | Reply, repost, like and view counts in grey with icons, in X's short form ("27.6K") |
| Divider | Thin line between posts, X's divider colour |

- **Counts are a snapshot** from when the post was collected or last seen on the profile page. They are display-only; tapping them does nothing.
- **Tapping an image** opens the post on X, like "Open on X".

### The app's own information

Kept visually separate from the X-styled post, in a strip under it:

- status ("waiting for interpretation", "skipped", "error", "shortened by X");
- the signal line, levels and evidence quote;
- "Show text read from images";
- "Open on X".

### Posts collected before this change

- They have no display name, avatar or counts stored. They are drawn with the handle as the name, a round placeholder showing the first letter, and no action row, until they are seen again on the profile page and updated.

## Not included

- Full-screen image viewer with zoom.
- Playing videos or GIFs (the preview image is shown).
- Polls, link preview cards and X "Articles".
- Liking, reposting or replying from the app.
- Live-updating counts.
- X's automatic translation.

## Design

### Stored data

The posts table gains these columns: `authorName`, `avatarUrl`, `verified`, `replyCount`, `repostCount`, `likeCount`, `viewCount`, `textComplete`, `quoteAuthor`, `quoteAuthorName`, `quoteAvatarUrl`, `quoteText`, `quoteImageUrl`, `quoteTimeMs`.

- **Migration, not reset:** the database goes from version 1 to version 2 with an explicit migration that adds the columns, so existing accounts, posts, signals and results are kept. The current "wipe on version change" fallback is removed.
- **Text colouring needs no storage:** cashtags, hashtags, mentions and links are found in the plain text when a post is drawn.

### Files

| File | Change |
|---|---|
| `app/src/main/assets/extract_posts.js` | Expand or flag shortened posts; return name, avatar, verified, counts, quoted post, and a `truncated` flag; never include the "Show more" label |
| `ingest/XReader.kt` | Parse the new fields; open post pages for shortened posts if method 2 is needed |
| `db/Db.kt`, `App.kt` | New columns, version 2, migration; an update query for re-seen posts |
| `ingest/Pipeline.kt` | Store new fields; update re-seen posts; reset to "waiting" when text grows |
| `llm/SignalParser.kt` | Add the quoted post to the prompt as labelled context |
| `ui/XPost.kt` (new) | The X-styled post, image grid, quote card, action row, icons |
| `XText.kt` (new) | Pure functions: split text into plain / cashtag / hashtag / mention / link segments; X time format; X count format. Unit-tested |
| `ui/FeedScreen.kt` | `PostItem` uses the X-styled post plus the information strip |

## Task breakdown and model assignment

| # | Task | Model | Why this model | Depends on |
|---|---|---|---|---|
| 1 | Investigate X's logged-in page on the tablet: structure of a post (name, avatar, badge, counts, quote, images), and what "Show more" does. Then fix the extraction contract (field names and types) and the full-text method in this document | **Fable 5.2** (orchestrator) | The requirement is ambiguous until the page is examined; the result decides the design of tasks 2 and 3; it needs the tablet in this session | — |
| 2 | Extractor script and reader: new fields, shortened-post handling | **Sonnet** | Standard implementation against the contract from task 1; moderately complex and cannot be tested by the subagent, so I verify it on the device | 1 |
| 3 | Database columns and migration, pipeline storage and re-interpretation, quoted post in the prompt, with unit tests for the prompt | **Sonnet** | Several connected edits with a data-safety risk (migration); needs care but no open design questions | 1 |
| 4 | Pure text functions (segments, time, counts) and their unit tests | **Sonnet** | Pattern-matching logic with many edge cases (punctuation after a cashtag, links, non-Latin text); beyond what Haiku handles reliably | Contract only |
| 5 | X-styled post composable, image grid, quote card, action row, icons; wire into the Feed | **Sonnet** | Ordinary but sizeable Compose work to a precise visual specification | 4's function signatures |
| 6 | Review all diffs, build, run all unit tests, fix integration issues | **Fable 5.2** (orchestrator) | Integration and review stay with the orchestrator | 2–5 |
| 7 | Verify on the emulator and the tablet, including a side-by-side comparison with the same post in the X app | **Fable 5.2** (orchestrator) | Needs the devices and judgment on visual match | 6 |

- Tasks 2, 3, 4 and 5 run in parallel after task 1; they touch different files and share only the contract.
- No Haiku task: nothing here is both trivial and separable.
- Escalation: a task that fails review goes back once with the specific defects, then one tier up.

### Acceptance criteria

- **Task 1:** this document states which full-text method is used and lists the exact fields the extractor returns, each confirmed against a real logged-in post.
- **Task 2:** on the tablet, the known shortened @doctorcatx post is stored with its complete text including "80-81K" and "72.5K"; no stored text ends in "Show more" or "Mostrar más"; name, avatar, verified and counts are filled for new posts.
- **Task 3:** installing over the current build keeps all existing accounts, posts and signals; a post whose text grows returns to "waiting" and gets a new signal; the DeepSeek prompt contains the quoted post under its own label.
- **Task 4:** tests cover cashtags, hashtags, mentions, links, trailing punctuation, adjacent segments, text with none of them, each time range, and counts below 1,000, in thousands and in millions.
- **Task 5:** every row of the "Post layout" table is implemented; posts without the new data use the fallback; the information strip holds everything the app adds.
- **Tasks 6–7:** no new build warnings; all unit tests pass (38 existing plus the new ones); the verification below passes.

## Verification

- **Unit tests** (`.\gradlew test`): text segments, time format, count format, prompt with a quoted post.
- **Tablet, data safety:** note the number of posts and signals before installing; confirm the same numbers after.
- **Tablet, full text:** run "Check now"; open the shortened @doctorcatx post and confirm the complete text; confirm it was re-interpreted and that its levels reach the Matrix.
- **Tablet, appearance:** put the same post in the Feed and in the X app (dark theme) next to each other and compare header, text colouring, image arrangement, quote card and action row.
- **Emulator:** confirm posts collected before the change draw with the fallback and nothing crashes.

## Task 1 findings (2026-11-12, partial: paused for a user decision)

### The reader on the tablet is not logged in to X

- The Accounts tab shows "X: not logged in", and the page has no login cookie. Every post collected on the tablet so far came from X's public, logged-out profile page.
- **X throttles that page.** After five checks in about fifteen minutes during this investigation, @doctorcatx's page returned X's error screen ("This page is down… Retry") instead of posts. Normal use is one check per ten minutes, but this shows the logged-out route can fail.
- The logged-in page has never been seen, so its structure is unknown. It may differ from the logged-out one.

### Structure of a post on the logged-out page (confirmed)

| Field | Where it is on the page |
|---|---|
| Context line ("Pinned", "… reposted") | First block of the post: an icon and a short text, in the page's language |
| Avatar | `img` with `alt="@Handle"`, address under `pbs.twimg.com/profile_images` |
| Display name | Text of the first link to `/<Handle>` in the header |
| Verified badge | A `span` with `role="button"` and `aria-haspopup="dialog"` beside the name (its label is translated, so it is found by position) |
| Time | The post id (already used); the visible date is translated |
| Text | `div[dir="auto"]`; a shortened post has a "Show more" `button` inside it |
| Images | `a[data-engagement-action="media"] img`, each with `width` and `height`, so proportions are known |
| Quoted post | `div[data-slot="embedded-tweet-frame"]` containing its own nested `article` (avatar, name, handle, date, images, text) |
| Counts | `div[data-engagement-action="reply" / "retweet" / "like" / "generic"]`, each with the number as X displays it ("516", "134 mil") |

- **Bug found in the current extractor:** it treats the nested `article` of a quoted post as a separate post. Those end up stored as skipped reposts. Task 2 must ignore nested articles.
- **Counts are stored as X displays them** (text such as "134 mil"), not as numbers: the full numbers are only available inside translated labels. The `…Count` columns in the design are therefore text.

### Not yet known

- **What pressing "Show more" does** (expand in place, or navigate to the post). The test was cut short by X's throttling.
