# MyLifePal Mac

This folder contains the native macOS version of MyLifePal. It is a SwiftUI desktop app with the same local-first life-game habit loop as the Android and Chrome versions.

## Build

From the repository root:

```sh
./build-macos-app.sh
```

The debug app is written to:

```text
build/macos/Build/Products/Debug/MyLifePalMac.app
```

## Included

- Sidebar desktop layout for Today, Timer, Mood, Habits, Rewards, Progress, and Appearance.
- Offline JSON state in Application Support.
- Atomic habit fields: cue, tiny action, identity, reward, attribute, reminder time.
- Local macOS notifications for habit reminders and timer completion.
- Tomato Timer with focus/break modes.
- Mood, energy, stress, and notes.
- Coins, XP, gems, reward shop, inventory, companion growth, and achievements.
- Theme presets and custom colors.
- Portable JSON export/import, with compatibility for the Android backup shape.
