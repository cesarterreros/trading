# BTC Signal Monitor (Galaxy Tab S10+)

Android app that turns X posts you add by hand (screenshots or copied text) into a buy/sell decision matrix for Bitcoin.

**Decision aid only.** Opinions from X accounts have no proven edge. The app records every signal and its later result so each account's hit rate can be checked.

## How it works

1. You share a post from the X app into this app: a screenshot, the post's text, or the post's link.
2. Text in screenshots is recognised on the tablet (Google ML Kit, offline).
3. DeepSeek turns the text into a structured signal: direction, entry, stop, targets, support, resistance, timeframe, conviction.
4. The matrix combines the latest signal of each account with live market data (Binance price, 50/200-day averages, funding rate, Fear & Greed).

The app never loads X pages itself, so it stays within X's terms of service. It is not a monitor: the matrix only reflects the posts you add.

## Build and install

1. Install Android Studio and let its first-run wizard download the Android SDK.
2. Open this folder (`monitoring`) in Android Studio and wait for the Gradle sync.
3. On the tablet: Settings → About tablet → Software information → tap "Build number" 7 times, then Developer options → USB debugging. Connect by USB.
4. Press Run in Android Studio.

Unit tests: `.\gradlew test` from this folder (needs the Android SDK from step 1).

## First use

1. **Settings:** paste the DeepSeek API key (from platform.deepseek.com) and press Save.
2. **Accounts:** add the X handles you follow. Accounts are also added automatically when you save a post for a new handle.
3. **X app:** open a post, expand it ("Show more"), and share it to BTC Signal Monitor (see below).
4. **Matrix:** the verdict updates once the post is interpreted.

## Adding a post

- **Share a screenshot:** from the screenshot toolbar or Gallery, Share → BTC Signal Monitor. Several images can be shared at once.
- **Share text or the link:** in the X app, select the post text → Share, or use the post's Share button.
- **Inside the app:** Feed → Add post, then paste text and pick images.
- Everything shared while the Add post screen is open joins the same draft, so you can share the link first and screenshots after.
- If the shared text contains the post's link, the app takes the account, the exact post time and the "Open on X" link from it without loading the page.
- Tips:
  - Expand the post before capturing.
  - For charts, open the image full screen in X and capture that.
  - Crop out replies when possible.

## Verdict rules (editable in Settings)

- Each account's latest signal scores direction × conviction × weight × time decay (half-life: scalp 1 day, swing 5 days, position 20 days).
- The consensus score runs from −100 to +100 across all enabled accounts.
- Buy or Sell needs: score beyond ±25, at least 2 accounts on that side, and median reward:risk of 2 or more where known. Otherwise the verdict is Wait, with the reason shown.
- Strong buy/sell needs a score beyond ±60 and 75% agreement.
- An account's weight is your manual weight, scaled by its measured hit rate once it has 10 resolved signals.

## Limits

- No automatic collection and no alerts for new posts: the matrix is only as current as the posts you add.
- Levels drawn on a chart without a printed number are not read.
- Chart labels inside a small screenshot are read less reliably than in a full-screen capture.
- Without a shared link, the post time is approximate (chosen on the Add post screen).
- The DeepSeek budget is a daily token cap, not a currency amount.
