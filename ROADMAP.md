# MyLifePal Roadmap

## Future iOS Release

MyLifePal should support iOS after the Android MVP proves the core habit loop. The first iOS version should feel native, simple, and familiar while keeping feature parity with the Android habit system.

### Keep Portable

- Habit fields: name, icon/emoji, cue, tiny action, identity vote, reward, attribute, tracking type, goal/limit mode, target value, unit, period, quantity progress, time progress, timer running state, XP, coins, streak, best streak, completion count, last completed date, reminder enabled state, and reminder time.
- Reward fields: title, coin cost, and claimed count.
- Progress model: total XP, coins, gems, attribute XP, inventory, chests opened, quest claims, and last chest date.
- Companion monster model: name, growth XP, bond, last care date, level, and evolution stage.
- Emotion model: dated mood, energy, stress, and optional note entries.
- Theme model: palette name plus primary, accent, and light background colors.
- Daily completion rule: one completion per habit per calendar day.
- Atomic habit flow: cue, craving/identity, tiny action, reward.
- Reminder model: local daily habit notifications that skip completed habits and reschedule after restore, reboot, and app update.
- Timecap-style activity model: completion, quantity counter, and time tracker habits with goal or limit behavior.
- Daily coach model: choose one next action from habit progress, mood, focus, quests, reminders, and companion state.
- Tomato Timer model: focus sessions, break sessions, running state, remaining time, and dated daily stats.
- Life game reward model: daily quests, claimable rewards, gems, chest costs, loot rarity, crafting recipes, and inventory history.
- Companion growth events from habits, focus sessions, mood check-ins, quest claims, reward claims, feeding, training, and bonding.

### iOS MVP

- Today view with next atomic habit prompt.
- Adaptive daily coach with one recommended next action.
- Habit studio for creating and editing habits.
- Timecap-style activity tracking with completion, quantity, time, goal, limit, period, reports, and home screen widget support.
- Daily local reminders for atomic habit cues.
- Reward shop and inventory.
- Life game reward loop with daily quests, gems, loot, and adventure chests.
- Companion monster growth with care actions and evolution stages.
- Emotion tracker with mood, energy, stress, notes, and 7-day trends.
- Progress screen with level, attributes, streaks, and achievements.
- Appearance controls with readable presets and custom colors.
- Tomato Timer for focus sessions and break rhythm.
- Offline-first storage using the same JSON-shaped model as Android.
- Data vault for portable file export, file restore, clipboard fallback, and future cloud sync.
- HCI baseline: 48dp controls, visible feedback, accessible labels/headings, destructive-action confirmation, and low-cognitive-load next steps.

### Later iOS Enhancements

- Widgets for the next tiny action.
- Apple Health integrations for movement and mindfulness habits.
- iCloud backup and restore.
- Shortcuts actions for quick habit completion.
- Watch companion for one-tap completions.
- Mood widgets and mindful check-in reminders.

### Product Principle

The iOS release should not add complexity just to match platform expectations. It should preserve the main promise: tiny habits should be easier to start than to avoid.

## Mac / macOS

The Mac app should be the command center for deeper review and planning while preserving the low-friction daily loop.

### Current Mac MVP

- Native SwiftUI macOS app under `macos/`.
- Sidebar workflow for Today, Tomato Timer, Mood, Habits, Rewards, Progress, and Appearance.
- Offline JSON state in Application Support.
- Atomic habit creation with cue, tiny action, identity, reward, attribute, and reminder time.
- Local macOS notifications for habit reminders and timer completion.
- Tomato Timer with focus, short break, and long break modes.
- Mood, energy, stress, and notes.
- XP, coins, gems, reward shop, inventory, achievements, and companion growth.
- Theme presets, custom colors, portable JSON export/import, and Android backup-shape import.

### Later Mac Enhancements

- Menu bar quick-complete companion.
- Calendar timeline for streaks, moods, focus, and rewards.
- Cloud sync with explicit user-controlled accounts.
- Rich charts for Timecap-style activity analytics.
- Keyboard-first command palette and Shortcuts/App Intents.

## Chrome Extension

The Chrome extension should keep the same promise in the browser toolbar: fast capture, low-friction completion, and local-first data ownership.

### Current Extension MVP

- Manifest V3 extension under `chrome-extension/`.
- Toolbar popup with Today, Tomato Timer, Mood, Habits, and Rewards views.
- `chrome.storage.local` offline state.
- Daily habit reminders through `chrome.alarms` and `chrome.notifications`.
- Background timer completion for Tomato Timer sessions.
- Color presets, custom hex colors, JSON export/import, rewards, coins, XP, gems, and companion growth.
- No host permissions and no web page reading.

### Later Extension Enhancements

- Optional side panel for a larger planning surface.
- Optional new-tab dashboard.
- Cross-device sync through a user-controlled cloud provider.
- Shareable browser quick actions for adding a habit from selected text.
- Chrome Web Store listing assets, screenshots, and privacy disclosure.

## Pixel Watch / Wear OS

Pixel Watch support is part of the Android track through a dedicated Wear OS app. The watch app should stay deliberately small: one-tap completion, the next tiny action, Tomato Timer, streak feedback, XP, and coins.

### Current Wear OS MVP

- Standalone Wear OS package: `com.mylifepal.watch`.
- Watch-first Today view for tiny action completion.
- 25-minute Tomato Timer with start, pause, reset, and completion rewards.
- Quick Low / Okay / Good emotion check-in.
- Offline XP, coins, streaks, and daily completion state.
- Separate installable APK at `wear/build/outputs/apk/debug/wear-debug.apk`.

### Future Phone-Watch Sync

- Sync selected phone habits to the watch.
- Let watch completions update phone progress.
- Keep conflicts simple by treating completion as a dated event.
- Show companion level and next tiny action on the watch once phone sync exists.
- Mirror phone reminder notifications to Pixel Watch where the user allows it.
- Add optional watch complications for the next tiny action, reminder time, and today count.

## Apple Watch / watchOS

Apple Watch support is part of the Apple track through a native SwiftUI watchOS companion. It should match the Pixel Watch philosophy: wrist-first, low-friction, and useful in under ten seconds.

### Current watchOS MVP

- Native SwiftUI watchOS project under `apple-watch/`.
- Watch-first Today view for tiny action completion.
- 25-minute Tomato Timer with start, pause, reset, and completion rewards.
- Quick Low / Okay / Good emotion check-in.
- Offline XP, coins, streaks, and daily completion state using `UserDefaults`.
- Simulator build helper: `./build-apple-watch-simulator.sh`.

### Future iPhone-Watch Sync

- Share the same JSON-shaped habit model with the future iPhone app.
- Sync selected habits from iPhone to Apple Watch.
- Let watch completions create dated completion events for iPhone progress.
- Mirror companion level and growth feedback from iPhone to Apple Watch.
- Mirror or schedule approved habit reminders on Apple Watch.
- Add complications, Smart Stack widgets, and Shortcuts/App Intents after the core loop is stable.
