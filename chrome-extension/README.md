# MyLifePal Chrome Extension

This folder contains the Chrome extension version of MyLifePal. It is a dependency-free Manifest V3 extension that can be loaded unpacked during development.

## Load Unpacked

1. Open `chrome://extensions`.
2. Enable Developer mode.
3. Choose **Load unpacked**.
4. Select this `chrome-extension` folder.

## Included

- Toolbar popup with Today, Timer, Mood, Habits, and Rewards views.
- Offline habit state in `chrome.storage.local`.
- Atomic habit fields: cue, tiny action, identity, reward, attribute, reminder time.
- Tomato Timer with background alarm completion.
- Daily habit reminder alarms and Chrome notifications.
- Mood check-ins with energy, stress, and notes.
- Coins, XP, gems, rewards, inventory, and companion growth.
- Color theme presets and custom hex colors.
- JSON export/import, including partial import support for the Android backup shape.

## Privacy

The extension stores data locally in the browser. It does not request host permissions and does not read web pages.
