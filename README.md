# Passatempo

A small games tool for the Light Phone III with ten games: Snake, Brick Breaker, Pong, Tic-Tac-Toe, Connect Four, Sudoku, Word Search, Blackjack, Klondike, and Dice. Each game has a daily limit. Formerly called "Games," renamed Passatempo (Portuguese/Italian for "pastime").

## Features

- **Snake**: swipe to steer. Unlimited attempts within a 15-minute daily budget.
- **Brick Breaker**: tap the left or right half of the screen to move the paddle a fixed distance for precise control, or switch to drag control in Settings so the paddle follows your finger. The ball waits for your first input, so opening the game doesn't spend your budget. Shares Snake's 15-minute daily budget.
- **Pong**: solo against a deliberately imperfect AI paddle, so it's beatable. Same tap or drag control as Brick Breaker (one Settings option controls both), same 15-minute budget, and same wait for your first input. No win condition; the score climbs on both sides until time runs out.
- **Tic-Tac-Toe**: two players, pass the phone. 20-minute daily budget, since two people share the clock.
- **Connect Four**: pass the phone, White vs. Black. 20-minute daily budget.
- **Sudoku**: 3 puzzles a day. Backing out mid-puzzle resumes where you left off without costing an attempt. Stuck? The trash icon in the top bar discards the puzzle (after a confirmation) and starts a new one, which counts as one of the day's attempts. Long-press the clear (X) button to reset the same puzzle to its original clues, free.
- **Word Search**: same daily limit and resume behavior as Sudoku. 1,499 words across 53 categories (animals, food, world geography, global holidays, notable people of color, sports, music, space, mythology, furniture, art styles, home decor, flora, and more). 8 words per puzzle in a 4-column list, with no immediate repeats. Placement favors positions that cross other words, so puzzles form an interlocking grid.
- **Blackjack**: single deck, no betting, no splits. Dealer hits soft 16 and below and stands on hard or soft 17+. After you hit 21, stand, or double down, the dealer's cards are revealed one at a time with a short pause. Tracks today's wins, losses, and pushes, reset daily. 10-minute daily budget.
- **Klondike**: draw one, unlimited redeals. Tap a card to send it to its obvious spot (a foundation first, then the leftmost legal column) or drag it. Hint suggests a move and can tell you when a deal can't be won. Saves after every move. 30-minute daily budget.
- **Dice**: tap to roll, animated. 10 throws a day.
- **Settings**: invert colors (dark/light), choose tap or drag paddle control, and show or hide each game on the home screen.

All progress and daily limits are stored on the phone. Nothing leaves the device.

## Screenshots

<p align="center">
  <img src="screenshots/home.png" width="180" alt="Home screen">
  <img src="screenshots/snake.png" width="180" alt="Snake">
  <img src="screenshots/brick-breaker.png" width="180" alt="Brick Breaker">
  <img src="screenshots/pong.png" width="180" alt="Pong">
  <img src="screenshots/tic-tac-toe.png" width="180" alt="Tic-Tac-Toe">
  <img src="screenshots/connect-four.png" width="180" alt="Connect Four">
  <img src="screenshots/sudoku.png" width="180" alt="Sudoku">
  <img src="screenshots/word-search.png" width="180" alt="Word Search">
  <img src="screenshots/dice.png" width="180" alt="Dice">
</p>

## Installation

### From Releases

Download `games.apk` from the [latest release](https://github.com/tyshi00/Passatempo/releases/tag/latest) and install it over ADB:

```
adb install games.apk
```

A new build is published to this release on every push to `main`. To get updates automatically, add this repo's URL to [Obtainium](https://obtainium.imranr.dev/). This is self-signed, informal distribution, separate from Light's official Tool Library approval process.

### From Source

Requires Android Studio and the Android SDK with the API 34/36 platform installed. No GitHub token is needed.

1. Clone this repo.
2. Create a `local.properties` file in the repo root with:
   ```
   sdk.dir=/path/to/your/Android/Sdk
   ```
3. Open the repo root in Android Studio and let Gradle sync.
4. Pick the `tool` run configuration, target an emulator or a Light Phone III in developer mode, and run.

For emulator testing, an AVD around 1080x1240 (API 34, no Google Play Services) is closest to the real screen. To run the actual LightOS emulator shell instead, see [`docs/system_app`](docs/system_app).

This repo's local `sdk` and `plugin` copies differ from the stock Light SDK:

- **App icon:** the `plugin` manifest generator was patched to add `android:icon`/`android:roundIcon`. The icon (a game controller with a clock face, nodding to "pastime") is in `tool/src/main/res/`.
- **Splash screen:** the ~1 second minimum splash in `sdk:client`'s `LightActivity` was removed, so the plain black splash shows only until content is ready.
- **Keyboard removed:** the private `com.thelightphone.lp3keyboard:ui` dependency and the `sdk:ui` keyboard files (`LightTextInputEditor`, `LightEmbeddedLp3Keyboard`, `LightKeyboardManager`) were deleted, since no game uses text input. This is why no GitHub token is needed. Light's `examples/ui-demo` and `examples/weather` still reference the removed editor and won't compile, but they aren't part of any build here.

## Credits

Built on the [Light SDK](https://github.com/lightphone/light-sdk) by The Light Phone (MIT). The original copyright notice is kept in [LICENSE](LICENSE), and the SDK's own README is kept in [README.light-sdk.md](README.light-sdk.md).

Klondike's game logic, solver, save state encoding, and win animation were originally created by gi-os for [LightSolitaire](https://github.com/gi-os/LightSolitaire) (MIT). Used with permission. See [NOTICE.md](NOTICE.md) for full attribution and the changes made for Passatempo.

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Unofficial, independent open-source project — not affiliated with or endorsed by The Light Phone, Inc. Light Phone and Light OS are trademarks of The Light Phone, Inc.
