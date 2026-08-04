# Attribution

## Klondike

The Klondike solitaire game (game logic, solver, save state encoding, and win animation)
was originally created by gi-os for LightSolitaire:
https://github.com/gi-os/LightSolitaire

This was verified directly against gi-os's repository. Its own README credits the
Klondike implementation to gi-os in an "Origin and credits" section, separate from its
credit to light-sdk (see below).

LightSolitaire is released under the MIT License. gi-os's repository carries forward the
license file from upstream light-sdk unchanged, rather than adding a separate notice for
Klondike specifically, so the exact copyright line below is reproduced as it actually
appears in that file, as required by the MIT License:

```
MIT License

Copyright (c) 2026 The Light Phone

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## light-sdk

This project (all games, not just Klondike) is built on light-sdk by The Light Phone:
https://github.com/lightphone/light-sdk

light-sdk is released under the MIT License, before the platform was public.

## Changes made for Passatempo

Klondike's game logic and rendering were ported into Passatempo largely unchanged. The
eight core files (game rules, moves, solver, victory animation, cards, save state
encoding) are byte for byte identical to gi-os's original, aside from being repackaged.
What changed on the way in:

- Storage: now uses Passatempo's shared `ActivePuzzleStore` and `DailyPlaytimeStore`
  instead of LightSolitaire's own single-purpose stores, so Klondike sits alongside
  every other game under one daily budget system.
- Daily time budget: 30 minutes, chosen for Passatempo, separate from LightSolitaire's
  original budget.
- Navigation: LightSolitaire's variant-picker entry screen and bottom-bar Settings
  access were removed, since Passatempo already provides both from its own home screen.
- Everything else (rules, solver, drag and drop, hints, undo, the win animation) is
  unchanged from the original.
