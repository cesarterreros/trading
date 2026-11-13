# Specification: light "research workspace" redesign

Status: implemented and installed on the tablet 2026-11-13; landscape and interaction checks on the tablet pending (see Outcome). Code tasks were delegated to DeepSeek (through its MCP server) instead of Sonnet.

## Context

The app works, but it looks like a prototype: a dark screen, plain text rows, links as coloured words, and one layout stretched over every screen size. The brief asks for a calm, precise, light trading-research workspace, designed for the tablet first, with an accounts panel beside the feed, native post cards, a media viewer and consistent components.

This is a change of presentation and layout. What the app does stays the same: posts are added by hand, DeepSeek interprets them, the matrix combines them with market data. Nothing reads X.

## Where the brief and the app differ

| Brief | App today | What this specification does |
|---|---|---|
| Destinations: Feed, Accounts, Settings | There is also **Matrix**, the app's main screen | Keeps all four. Matrix is restyled with the rest |
| Avatar, display name and verification "from the link" | A post link holds only the handle, the post ID and the post time | Avatar is a coloured circle with the account's initial. Display name, a "verified" mark and an optional avatar picture are set by you per account in Accounts. Nothing is fetched from X |
| Accounts panel showing "the selected account's feed" | One list with a collapsible section per account | Both: pick one account to see only its posts, or "All accounts" to keep the collapsible sections, where Expand all / Collapse all apply |
| Typeface "Inter or an available equivalent" | System typeface | The tablet's system sans-serif with tabular numerals switched on. No font files are added. If the system typeface turns out not to support tabular numerals, Inter is bundled instead |
| "Replace the social embed wrapper" | A hand-added post is often an X screenshot | The card around it is native (author, time, text, signal, actions). The screenshot stays untouched inside as the source image |

## Visual direction

A light, quiet workspace: a soft grey canvas, white surfaces separated by thin borders rather than shadows, one restrained blue for actions, and colour reserved for sentiment. Dense where numbers matter (matrix, levels), roomy where text is read (posts).

### Colours

| Role | Value |
|---|---|
| Canvas | `#F5F7FA` |
| Cards, navigation, panels | `#FFFFFF` |
| Primary text | `#172033` |
| Secondary text | `#5B6B82` |
| Dividers and card borders | `#E3E8EF` |
| Primary action | `#2563EB`, white text |
| Selected row background | `#EFF4FF` |
| Bullish | text `#047857` on `#ECFDF5` |
| Bearish | text `#B42318` on `#FEF3F2` |
| Neutral | text `#475467` on `#F2F4F7` |
| Warning (waiting, errors, cautions) | text `#B54708` on `#FFFAEB` |

- Sentiment is never colour alone: every badge has its word ("Bullish") and an arrow or dash icon.
- All text pairs above meet WCAG AA contrast (4.5:1 or better).
- System status and navigation bars become light with dark icons.

### Type, spacing, shape

- **Type scale:** screen title 20 semibold; section title 16 semibold; body 15 with 22 line height; label 13 medium; caption 12.
- **Tabular numerals** for prices, levels, scores, counts, percentages and times.
- **Spacing:** multiples of 8 dp (4 dp only inside compact chips and table cells).
- **Corners:** 12 dp on cards and panels, 8 dp on images and chips.
- **Borders, not shadows:** 1 dp `#E3E8EF`. Shadow only on menus, sheets and the media viewer.
- **Touch targets:** at least 44 dp, including icon buttons and links.
- **Icons:** the outlined Material icons already in the project, plus four small ones drawn for this app (external link, verified mark, up-right and down-right arrows). No icon library is added.

## Layout

Breakpoints follow the width available to the app, not the device name.

| Width | Example | Layout |
|---|---|---|
| 1000 dp and wider | Tab S10+ landscape (about 1400 dp) | Navigation rail (80 dp) · accounts panel (320 dp) · feed |
| 600–999 dp | Tab S10+ portrait (about 876 dp) | Navigation rail · feed. Accounts move into a selector opened from the feed toolbar |
| Under 600 dp | Phone | Bottom navigation bar · feed, same account selector |

- No screen scrolls sideways as a whole. Wide tables (Matrix) scroll inside their own card when they cannot fit.
- Reading width is capped: post text and images are at most 720 dp wide, so lines stay readable on the landscape tablet.
- Everything works by touch and by mouse or keyboard. Nothing depends on hover.

### Navigation rail

Matrix, Feed, Accounts, Settings, each an icon with a label. The active destination has a blue icon and label on a pale blue pill. In the bottom bar the same four items appear.

## Feed

### Accounts panel (landscape) and account selector (portrait, phone)

The same content in two containers: a fixed panel at 1000 dp and wider, a sheet sliding up from the bottom otherwise. The selector button in the feed toolbar shows the current account's avatar and handle.

- **"All accounts"** row at the top, with the total post count.
- **Account rows:** avatar · display name or `@handle` (verified mark when set) · sentiment badge · post count · time of latest post ("4h"). "Paused" and "removed" labels as today.
- **Selected row:** pale blue background and a 3 dp blue bar on its left edge.
- **Order:** latest activity first, as today.
- **Search:** a field that filters rows by handle or display name.
- **Sentiment filter:** chips All · Bullish · Bearish · Neutral.
- **Empty filter result:** "No accounts match" with a "Clear filters" button.
- The selection is remembered between app starts.

### Feed toolbar

Above the posts, in three visually separate groups:

| Group | Content |
|---|---|
| Context (left) | Selected account's avatar, name and sentiment badge; or "All accounts". In portrait this is the button that opens the selector |
| View controls (middle) | Expand all · Collapse all, as quiet text buttons, shown only in the "All accounts" view |
| Primary action (right) | **Add post**, filled blue with a plus icon |

### Posts list

- **One account selected:** its posts as cards, newest first.
- **All accounts:** the collapsible sections from the current design, restyled; headers stay pinned while scrolling; open sections are remembered.
- "Show 20 more" stays.

## Post card

White card, 12 dp corners, 1 dp border, 16 dp padding. From top to bottom:

1. **Header:** avatar (40 dp) · display name in semibold with the verified mark when set · `@handle` · "X" · time ("4h", "3d", "Nov 9"). When the post's author differs from the tracked account: "shared under @account". On the right: **Open on X** (small external-link icon and text, secondary colour, shown only when the post ID is known) and an **overflow menu** (⋮).
2. **Status chip**, only when needed: "Interpreting…" with a small progress ring, "Waiting for interpretation", "Error" (with the reason), "Skipped".
3. **Post text:** the original text, unchanged, with line breaks kept. A post that has only screenshots shows "Text is in the screenshot below" in secondary colour instead.
4. **Author's view**, a tinted block, only when there is a signal:
   - sentiment badge · type · timeframe · conviction (as "●●○");
   - levels in aligned columns with tabular numerals: Entry, Stop, Targets, Support, Resistance;
   - the evidence quote in italics;
   - caption: "The author's stated view, extracted by DeepSeek. Not a prediction." so sentiment reads as the author's opinion.
   - "Not about BTC: ignored" replaces the block for noise posts.
5. **Media:** each image at up to 720 dp wide and 420 dp high, scaled to fit whole (never cropped), 8 dp corners, thin border. Two or more images sit in a row that scrolls inside the card. Dark chart screenshots stay as they are.
6. **Image unavailable:** a grey box labelled "Image unavailable" with the reason when known. No stand-in chart.

**Overflow menu:** "Show text read from images" / "Hide text read from images", then, separated by a divider, "Delete post" in red, with the existing confirmation dialog.

## Media viewer

- Tapping an image opens it full screen on a dark backdrop.
- Pinch to zoom up to 5×, drag to pan, double-tap to toggle between fit and 2.5×.
- With several images: swipe left or right, and a "2 / 3" counter.
- Close button (top right, 48 dp) and the Back button both close it.
- The image is shown exactly as stored.

## Matrix

Same content, restyled:

- **Verdict card:** the verdict as a large sentiment-coloured badge with its word, score, agreement and median reward:risk beneath; "Wait" reasons and cautions as a short list.
- **Market card:** BTC price, 24 h change, 50 and 200-day averages, funding, Fear & Greed, each as a labelled figure. A line "Updated 21:14" shows when the data was fetched; if it is older than 5 minutes the card says "Market data may be out of date" in the warning colour. Refresh is a quiet button here.
- **Accounts table:** in a card, header row in caption style, tabular numerals, right-aligned numbers, sentiment as badges. Scrolls sideways inside the card on narrow screens.
- **Level zones:** in a card, same table style.
- The decision-aid notice stays, as a caption.

## Accounts

- "Add account" field and button in a card at the top.
- One card per account: avatar, name, handle, sentiment badge; hit rate and won / lost / expired / open counts; weight slider; "Include in matrix" switch; overflow menu with "Edit" and "Remove account".
- **Edit account** dialog (new): display name (optional), "Verified" switch, "Choose picture" / "Remove picture" for the avatar. The note under it: "Set by you. The app does not read profiles from X."

## Settings and Add post

- **Settings:** the same options grouped in cards: DeepSeek, Alerts and display, Verdict rules. One Save button fixed at the bottom of the screen.
- **Add post:** the same fields in the new style. On widths of 1000 dp and more it appears as a centred sheet 720 dp wide over a dimmed background; otherwise full screen. "Add image" moves under the thumbnails, and the remove button on each thumbnail gets a solid dark circle so it is visible on light images.

## Empty, loading and error states

| Where | State |
|---|---|
| Feed, no accounts | Illustration-free message with the three steps (add key, share a post, read the matrix) and an "Add post" button |
| Feed, account with no posts | "No posts for @handle yet" and "Add post" |
| Matrix, market data not loaded yet | Grey placeholder bars in the market card, not zeros |
| Post being interpreted | Progress ring in its status chip |
| DeepSeek key missing | A warning banner at the top of the Feed and Matrix with an "Open Settings" button |

## Interaction states and accessibility

- Every button, row, chip and link is a real button or clickable row, so it gets a pressed ripple, a visible keyboard-focus ring (2 dp blue), a disabled style (38% opacity) and a screen-reader label.
- Selected: accounts row and navigation item as described.
- Loading: as in the table above.
- **Reduced motion:** the app's own animations (section expand, sheet, viewer) follow the system "Remove animations" setting; with it on, they switch instantly.
- Content descriptions on all icon-only buttons.

## Stored data

Accounts gain three fields: `displayName`, `verified`, `avatarPath`.

- **Migration, not reset.** The database goes from version 1 to 2 with an explicit migration that adds the three columns. The current setting that wipes the database on a version change is removed, so your accounts, posts and signals are kept.
- Two more remembered settings: the selected feed account, and the sentiment filter.

## Not included

- Fetching avatars, names or verification from X.
- A dark theme switch.
- New data, charts drawn by the app, order entry, or live price streaming.
- Redrawing or recolouring source images.
- Drag-to-resize panels.

## Components and files

| Component | File (under `app/src/main/java/com/trading/monitor/ui`) |
|---|---|
| Colours, type, spacing, shapes | `theme/Theme.kt` |
| App shell with navigation rail / bottom bar and breakpoints | `AppUi.kt` |
| Accounts panel, account row, selector sheet | `components/AccountsPanel.kt` |
| Feed toolbar | `components/FeedToolbar.kt` |
| Sentiment badge | `components/SentimentBadge.kt` |
| Avatar | `components/Avatar.kt` |
| Post card | `components/PostCard.kt` |
| Media viewer | `components/MediaViewer.kt` |
| Empty, loading and banner states | `components/States.kt` |
| App-drawn icons | `components/AppIcons.kt` |
| Screens | `FeedScreen.kt`, `MatrixScreen.kt`, `AccountsScreen.kt`, `SettingsScreen.kt`, `AddPostScreen.kt` |

Outside `ui`: `db/Db.kt` and `App.kt` (fields and migration), `FeedGrouping.kt` (filtering, initials, avatar colour; unit-tested), `Settings.kt`, `res/values/themes.xml`, `MainActivity.kt` (light system bars).

No dependency is added.

## Task breakdown and model assignment

Two waves, so the second builds on real, compiled foundations instead of on promises.

| # | Wave | Task | Model | Why this model | Depends on |
|---|---|---|---|---|---|
| 0 | — | Record this specification (this file) | **Fable 5.2** (me) | Text I already hold | — |
| 2 | 1 | Account fields and database migration; feed filtering, initials and avatar-colour functions with unit tests; new settings | **DeepSeek Flash** | Data-safety risk in the migration and edge cases in the filters, but no open design questions | — |
| 1 | 1 | Theme tokens; sentiment badge, avatar, app icons, state components; light system bars | **DeepSeek Flash** | Standard Compose work to an exact token table | 2 |
| 3 | — | Review wave 1, build, test the migration on the emulator's existing data | **Fable 5.2** (me) | The migration touches your data; integration stays with the orchestrator | 1, 2 |
| 4 | 2 | App shell, breakpoints, navigation; accounts panel and selector; feed toolbar; Feed screen | **DeepSeek Flash** | The largest layout task, but ordinary Compose against fixed contracts | 3 |
| 5 | 2 | Post card and media viewer (zoom, pan, swipe) | **DeepSeek Flash** | Gesture handling needs care; self-contained | 3 |
| 6 | 2 | Matrix and Settings screens | **DeepSeek Flash** | Restyling existing screens with the new components | 3 |
| 7 | 2 | Accounts screen with the edit dialog; Add post screen | **DeepSeek Flash** | Restyling plus one new dialog and avatar picking | 3 |
| 8 | — | Review all diffs, build, run all tests, fix integration issues | **Fable 5.2** (me) | Integration and review | 4–7 |
| 9 | — | Verify on the emulator (phone width) and the tablet (landscape and portrait), with screenshots of each layout | **Fable 5.2** (me) | Needs the devices and visual judgment | 8 |

- **One task at a time.** The DeepSeek server allows one job per workspace, so tasks run in the order 2, 1, then 5, 4, 6, 7. Each is reviewed and its recorded file changes acknowledged before the next starts.
- **Task 2 runs before task 1** because the avatar component uses the account fields and the initials and avatar-colour functions from task 2.
- **Task 5 runs before task 4** because the Feed screen uses the post card and media viewer. The contract below still fixes their signatures.
- No Haiku task: every piece needs layout or data judgment, and none is trivial and separable.
- DeepSeek writes code only and runs no builds. A task that fails review goes back once with the defects; after that I fix it myself, because DeepSeek Pro currently routes to the same model as Flash.
- DeepSeek cannot see this conversation, so each task is sent with this file's path, the files it owns and its acceptance criteria.
- The `monitoring` folder is not committed to git yet. A copy of the sources as they were before task 2 is kept outside the project, so every change can be compared with it.
- Two additions found in review: the selected feed account falls back to "All accounts" when that account no longer exists (task 2), and an account's avatar picture file is deleted when the picture is replaced or the account removed (task 7).

### Contract between tasks 4 and 5

```kotlin
// components/PostCard.kt
@Composable fun PostCard(app: App, post: Post, signal: Signal?, account: Account?, onOpenMedia: (images: List<String>, index: Int) -> Unit)
// components/MediaViewer.kt
@Composable fun MediaViewer(images: List<String>, startIndex: Int, onClose: () -> Unit)
```

### Acceptance criteria

- **Task 1:** every colour, size and shape in this document exists as a named token; no screen needs a literal colour; badge, avatar and states match their descriptions.
- **Task 2:** the migration adds the three columns without touching existing rows; filters and helper functions are covered by unit tests; the wipe-on-version-change setting is gone.
- **Task 3:** on the emulator, the data present before the update is all there after it.
- **Tasks 4–7:** each section of this document for their screens is implemented; no dark surfaces remain except the media viewer backdrop and source images; no literal colours outside the theme; every existing action still works.
- **Tasks 8–9:** no new build warnings; all unit tests pass (58 existing plus the new ones); the verification below passes.

## Verification

- **Unit tests** (`.\gradlew test`): account filtering by text and sentiment, initials, avatar colour stability, plus all existing tests.
- **Migration:** before installing on the tablet, copy its database to the PC as a backup; after installing, confirm the same accounts, post counts and signals.
- **Tablet landscape:** rail, accounts panel and feed visible together; select an account; search and filter; Expand all / Collapse all in "All accounts"; add a post; open an image, zoom and close; open the overflow menu and delete a test post; Open on X.
- **Tablet portrait:** rail and feed; open the account selector, switch account; no sideways page scrolling.
- **Emulator (phone width):** bottom bar; selector sheet; cards readable.
- **Accessibility:** contrast of every text pair checked against AA; with a keyboard attached, Tab reaches every control with a visible focus ring; with "Remove animations" on, sections and sheets switch instantly.
- **Screenshots** of landscape and portrait are added to this record's Outcome.

## Outcome (2026-11-13)

Implemented. The build has no new warnings, and all 84 unit tests pass (58 existing and 26 new).

### How the work was split

- **DeepSeek Flash wrote:**
  - the theme tokens and the light system bars;
  - the account fields, the migration and the feed helpers with their tests;
  - the badge, avatar, icon and state components;
  - the post card and the media viewer;
  - the accounts panel and the feed toolbar;
  - the Feed screen (most of it);
  - the Matrix, Settings, Accounts and Add post screens.
- **I (Fable 5.2, then Opus 5.5) did:**
  - every review;
  - the integration fixes listed below;
  - the focus-ring helper;
  - the app shell (navigation rail and bottom bar, breakpoints, media viewer placement);
  - the verification.
- **How the DeepSeek jobs went:**
  - Two jobs stopped part-way: task 2 hit its token budget, and the first post-card-plus-viewer job was cut off mid-answer. Their remaining work was resent in smaller pieces.
  - The Feed and app-shell job ran out of budget after the Feed screen. I wrote the app shell myself.
  - Requests that asked for one file at a time, written stub first and then filled with small edits, finished reliably.

### Fixes made in review

- `App.kt` did not import the migration.
- DeepSeek left two temporary scripts in the project root. They are deleted.
- The Feed screen had three compile errors.
- A blank display name showed as an empty title.
- The Matrix table dropped the conviction dots, and it lost the "Caution:" prefix on cautions.
- The Matrix screen added padding twice.
- At phone width the Matrix table's Account, Stance and Age columns were too narrow. The table is now at least 1100 dp wide and scrolls sideways inside its card.

### Verification

- **Migration (emulator):** the database before the update had 3 accounts, 28 posts, 0 signals and was at version 1. After the update it is at version 2 with the same counts. The posts are byte-identical, and the accounts' old columns are unchanged. Each account gained the three new columns: displayName empty, verified 0, avatarPath empty.
- **Layouts on the emulator:**
  - Phone width shows the bottom bar.
  - At 876 dp (tablet portrait) the navigation rail appears, and accounts are chosen from a selector.
  - At 1400 dp (tablet landscape) the rail, the accounts panel and the feed are side by side.
  - The app did not crash on any screen.
- **Screenshots:** in `assets/2026-11-13-redesign/`.

- **Migration (tablet):**
  - **Backup:** the database was copied to the PC first. It is in `monitoring/backups/2026-11-13-tablet-before-redesign/`, a folder ignored by git.
  - **Before:** version 1 with 5 accounts, 19 posts, 13 signals and 2 days of usage.
  - **After:** version 2 with the same counts. The posts and signals are identical, and the accounts' old columns are unchanged.
- **Tablet, portrait (876 dp):** the navigation rail and the "All accounts ▾" selector show, and Feed and Matrix display correctly. The Matrix Age column then wrapped "2d · position", so it was widened (table minimum 1150 dp). Screenshots: `real-tablet-feed.png`, `real-tablet-matrix.png`.

### Not yet done

- **Tablet in landscape.** The tablet was held in portrait. Landscape was checked only on the emulator, at the same width (1400 dp).
- **Interaction checks on the emulator.** Not exercised yet:
  - zoom, pan and swipe in the media viewer;
  - the account selector sheet;
  - search and the sentiment filter;
  - the edit-account dialog and avatar picking;
  - the keyboard focus ring;
  - the "Remove animations" setting.
- **Contrast.** No tool was run to measure contrast. The colour pairs come from the specification's AA-checked table.

### Noticed, not changed

- On the first launch after the emulator booted, the Feed briefly showed the "Add your DeepSeek API key" banner although a key was stored. On later launches it did not appear. This points to the Android Keystore not being ready yet, which is a timing issue in the existing key handling.

## What will be manual or not implemented

- Avatars are initials unless you choose a picture; display names and the verified mark are whatever you set.
- "Source platform" on a card is the fixed word "X".
- Market figures are fetched values with a timestamp, not a live stream.
