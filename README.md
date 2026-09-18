# Passatempo (Light Phone III)

A small games tool for LightOS, built with the Light SDK. Ten things to do: Snake, Brick Breaker, Pong, Tic-Tac-Toe, Connect Four, Sudoku, Word Search, Blackjack, Klondike, and Dice. Formerly called "Games," renamed to Passatempo (Portuguese/Italian for "pastime").

<p align="center">
  <img src="screenshots/home.png" width="180" alt="">
  <img src="screenshots/snake.png" width="180" alt="">
  <img src="screenshots/brick-breaker.png" width="180" alt="">
  <img src="screenshots/pong.png" width="180" alt="">
  <img src="screenshots/tic-tac-toe.png" width="180" alt="">
  <img src="screenshots/connect-four.png" width="180" alt="">
  <img src="screenshots/sudoku.png" width="180" alt="">
  <img src="screenshots/word-search.png" width="180" alt="">
  <img src="screenshots/dice.png" width="180" alt="">
</p>

## What's in it

- **Snake** - swipe to steer. No per-attempt limit, but capped at 15 minutes of playtime per day.
- **Brick Breaker** - tap the left or right half of the screen to nudge the paddle by default; each tap moves it a fixed distance rather than gliding, for precise control. A Settings option switches both this and Pong to slide/drag instead, where the paddle follows your finger directly. Waits for your first input before the ball starts moving, so opening the screen doesn't spend your budget before you're ready. Shares the same 15-minute daily time budget as Snake, same reasoning: quick rounds, unlimited retries within the budget.
- **Pong** - solo against a simple AI paddle (deliberately imperfect, so it's beatable). Same tap or drag control as Brick Breaker (one Settings option controls both games), same 15-minute budget, and same behavior of waiting for your first input before anything moves. No fixed win condition. Score keeps climbing on both sides until time runs out.
- **Tic-Tac-Toe** - local pass-the-phone, two players. 20-minute daily time budget (bumped up from the original 10 since two people share the clock).
- **Connect Four** - same pass-the-phone setup as Tic-Tac-Toe, White vs. Black. 20-minute daily budget.
- **Sudoku** - 3 puzzles a day. If you back out mid-puzzle, it resumes right where you left off instead of starting over or costing you another attempt. If you're stuck, there's a trash icon in the top bar that tosses the current puzzle (with a confirm step first) and generates a new one for you right away. The new attempt counts toward your budget for the day. It doesn't refund or grant an extra puzzle attempt. Made a mess of your entries and want a clean slate on the *same* puzzle instead? Long-press the clear (X) button to wipe every entry back to the original clues, free and with no attempt spent, since it's still the same puzzle.
- **Word Search** - same daily limit and resume behavior as Sudoku. 1499 words across 53 categories (animals, food, world geography, global holidays, notable people of color, sports, music, space, mythology, furniture, art styles, home decor, flora, and more), 8 words per puzzle shown in a 4-column layout, with a no-immediate-repeat mechanism so the same words don't keep resurfacing. Placement actively favors positions that cross other words already on the grid, so puzzles read as an interlocking grid rather than a stack of parallel strips.
- **Blackjack** - single deck, no betting, no splits. Dealer hits soft 16 and below, stands on hard or soft 17+. After you hit 21, stand, or double down, the dealer's turn plays out one card at a time with a short pause between each, instead of jumping straight to the result, so you can actually watch it happen. Tracks today's wins, losses, and pushes, resetting each day alongside the daily budget. 10-minute daily budget.
- **Klondike** - draw one, unlimited redeals. Tap a card to send it where it obviously belongs (a foundation first, then the leftmost legal column) or drag it yourself. Hint suggests a move, and can tell you honestly when a deal can't be won rather than just when nothing looks playable right now. Saves after every move, so leaving and coming back drops you on the same board. 30-minute daily budget. Klondike's game logic, solver, and win animation were originally created by gi-os for LightSolitaire; see [NOTICE.md](NOTICE.md) for full attribution.
- **Dice** - tap to roll, animated. 10 throws a day.
- **Settings** - invert colors (dark/light), and a show/hide toggle for every game above on the home screen.

All progress and daily limits are stored locally on-device. Nothing leaves the phone.

## Installing

Grab the latest APK from this repo's [Releases](../../releases) page and install it directly.

This is self-signed, informal distribution, separate from Light's official Tool Library approval process. If you're using something like Obtainium to track and auto-update from this repo, point it at the repo URL and it'll pick up new releases as they're tagged.

## Building from source

Only needed if you want to modify the code yourself.

Requirements:
- Android Studio
- Android SDK with the API 34/36 platform installed

Steps:
1. Clone this repo.
2. Create a `local.properties` file in the repo root with:
   ```
   sdk.dir=/path/to/your/Android/Sdk
   ```
3. Open the repo root in Android Studio and let Gradle sync.
4. Pick the `tool` run configuration, target an emulator or a Light Phone III in developer mode, and run.

For emulator testing, an AVD around 1080x1240 (API 34, no Google Play Services) is closest to the real device's screen. See `docs/system_app` in this repo if you want to run the actual LightOS emulator shell instead of a plain Android emulator.

## Deviations from the stock SDK

This repo patches a few things in the local `sdk`/`plugin` copies, on top of the actual game code:

- **Custom app icon is wired up.** The stock SDK's manifest generator doesn't reference a launcher icon at all by default; this repo's local `plugin` copy was patched to add `android:icon`/`android:roundIcon`. The icon itself is an original, simplified game-controller silhouette with a clock face standing in for the action buttons (nodding to "pastime"), living in `tool/src/main/res/`.
- **Splash screen shows only as long as needed.** The SDK's default `LightActivity` enforces a hard ~1 second minimum splash duration; that check was removed from this repo's local `sdk:client` copy, so the splash now shows for exactly as long as it takes content to be ready, nothing more. The splash itself is just a plain black screen with no icon.
- **The SDK's keyboard component was removed.** Light's `sdk:ui` module normally depends on a private `com.thelightphone.lp3keyboard:ui` package (hosted on GitHub Packages, requiring authenticated access) for its in-app text-input keyboard (`LightTextInputEditor`, `LightEmbeddedLp3Keyboard`, `LightKeyboardManager`). None of Passatempo's screens use text input at all, so those files were deleted from this repo's local `sdk` copy and the dependency removed entirely. This means the project builds without needing any GitHub Packages credentials. Two of Light's own example apps (`examples/ui-demo`, `examples/weather`) still reference the removed keyboard editor and would fail to compile if built individually, but they're unrelated to Passatempo and aren't part of any build or CI step here.

## Releasing a new version

Just push to `main`. `.github/workflows/release.yml` builds the APK on every push and publishes it as a rolling `latest` GitHub Release with the APK attached, replacing the previous build:

```
git add .
git commit -m "Your changes"
git push
```

The workflow needs no repo secrets at all. It uses GitHub Actions' automatically-provided token, and doesn't depend on any external private packages.

If you'd rather version releases explicitly instead of always overwriting `latest`, bump `versionCode`/`versionName` in `tool/lighttool.toml` before pushing, and consider switching the workflow to trigger on version tags instead of every push to `main`.
