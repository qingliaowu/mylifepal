# MyLifePal

MyLifePal is an offline-first Android habit RPG inspired by gamified productivity apps and atomic habit loops. It turns daily habits into tiny identity votes, Timecap-style activity tracking, an adaptive daily coach, daily reminders, emotion check-ins, companion monster growth, XP, coins, gems, reward quests, attributes, achievements, loot drops, chest openings, and Tomato Timer focus sessions.

Pixel Watch support is included through a separate Wear OS companion app. The watch app focuses on the small-screen core loop: see the next tiny action, complete it with one tap, run a Tomato Timer, record a quick emotion check-in, and keep XP, coins, and streaks moving from the wrist.

Apple Watch support is included through a native SwiftUI watchOS companion scaffold. It uses the same small-screen loop, Tomato Timer, and quick emotion check-in as the Pixel Watch app and stores watch progress offline while future phone-watch sync is planned.

An iOS release is planned for a future version. The app keeps its habit, reward, and progress data model simple and JSON-shaped so the core loop can be carried to a native iOS app later without redesigning the product from scratch. See `ROADMAP.md`.

## Build

Open the folder in Android Studio, or run the SDK-tooling helper script that was used to produce the verified debug APK:

```sh
./build-debug-apk.sh
```

The debug APKs are written to:

- Phone: `app/build/outputs/apk/debug/app-debug.apk`
- Pixel Watch / Wear OS: `wear/build/outputs/apk/debug/wear-debug.apk`

Build the Apple Watch simulator app with:

```sh
./build-apple-watch-simulator.sh
```

The watchOS simulator app is written under `build/xcode/Build/Products/Debug-watchsimulator/MyLifePalWatch.app`.

The Gradle project files are included as well, so Android Studio can import and run the app normally.

## Life Game Rewards

The Rewards tab now works like a light life RPG:

- Character panel with title, level, strongest attribute, coins, gems, loot, daily quests, and reward claims.
- Growable companion monster with levels, bond, evolution stages, care actions, and a custom name.
- Daily quest board with claimable XP, coins, gems, and loot drops.
- Adventure chest that costs gems and drops common, rare, or epic loot.
- Crafting bench for converting gems and coins into focus potions, rest passes, and premium loot rolls.
- Real-life reward shop for custom rewards paid with earned coins.
- Inventory history for claimed rewards, quest loot, and chest loot.
- Data vault for portable JSON file backup, file restore, and clipboard fallback.

## Daily Coach

The Today tab includes a MyLifePal Coach card that chooses one best next move:

- Reads habit progress, mood status, timer state, quest rewards, reminders, and companion bond.
- Suggests the next useful action instead of making the user decide from scratch.
- Adapts for low energy or high stress by recommending the smallest rescue version.
- Starts a Tomato Timer, opens mood check-in, opens quests, starts a tiny habit, or bonds with the companion.
- Shows a daily score made from habits, focus, mood, quests, reminders, and companion care.

## HCI And UX Quality

The UI follows practical HCI principles:

- Recognition over recall through visible cues, next actions, progress, and reward context on each card.
- Consistent 48dp touch targets across primary actions.
- Clear feedback after habit, timer, reward, backup, reminder, and companion actions.
- Error prevention with required-field validation and confirmation before destructive deletes.
- Accessibility support through content descriptions, selected tab state, headings, and non-announced spacer views.
- Progressive disclosure: Today recommends one next move while deeper controls live in Habits, Rewards, Mood, Timer, and Progress.

## Timecap-Style Activity Tracking

Habit Studio now covers the core Timecap activity types:

- Completion activities for simple daily done/not-done habits.
- Quantity counters with `+1` and `-1` controls for counts like glasses of water, pages, cigarettes, or workouts.
- Time tracker activities with start/stop and quick `+5m` tracking.
- Goal or limit mode, so users can build good habits or cap bad habits.
- Daily, weekly, monthly, yearly, and certain-weekday period metadata.
- Per-habit icon/emoji, target value, unit, attribute, reminder, cue, tiny action, identity, and reward.
- Activity reports for completion habits, counters, timers, success percentage, tracked time, tracked quantity, limits hit, and streaks.
- Android home screen widget with habit progress, next action, coins, and companion level.

## Reminder System

The Habit Studio includes daily habit reminders:

- Each habit can enable or disable a daily reminder.
- Reminder times are stored with the habit and included in backup files.
- Android notification permission is requested when a reminder is enabled.
- Reminders reschedule after habit edits, completions, backup restore, reboot, and app update.
- Reminder notifications open MyLifePal and show the cue, tiny action, and reward.

## Companion Monster

The Life Game tab includes a growable monster companion:

- Habits, Tomato Timer focus sessions, mood check-ins, quest claims, and real-life reward claims grant companion XP.
- The companion evolves from Hatchling to Bloomling, Guardian, and Mythic forms as it levels.
- Users can feed, train, bond with, and rename the companion.
- Companion XP, bond, name, and care history are included in backups.
- Companion growth unlocks achievements and appears in inventory history when it levels up.

## Backup And Restore

The Data vault keeps users in control of their progress without requiring an account:

- Save a portable `.json` backup through Android's system document picker.
- Import a previously saved backup file through Android's system file picker.
- Copy the same versioned backup JSON to the clipboard when file access is not convenient.
- Paste backup JSON to restore manually.
- Backups include habits, Timecap-style tracking settings and progress, reminder settings, companion monster progress, atomic habit prompts, rewards, quests, timer progress, moods, coins, gems, loot, inventory, achievements, and attribute XP.

## Emotion Tracker

The Mood tab supports daily emotional check-ins:

- Mood, energy, stress, and optional notes.
- 7-day mood trend with average mood, energy, and stress.
- Recent feeling history.
- Daily quest integration for self-awareness rewards.
- Quick Low / Okay / Good check-ins on Pixel Watch and Apple Watch.

## Benchmark: LifeUp Trial

The public LifeUp trial repository describes a highly customizable gamified to-do and habit app with attributes, shop rewards, achievements, Pomodoro, feelings, loot boxes, crafting, offline backups, widgets, themes, statistics, and basic to-do tooling.

MyLifePal is designed to compete with the best parts of LifeUp, Timecap, Habitica, Finch, Streaks, and Fabulous while staying simpler at the surface:

- Phone, Pixel Watch, and Apple Watch experiences from the start.
- Timecap-style completion, quantity, and time tracking with goals, limits, periods, reports, reminders, widget, and backup.
- Adaptive daily coach that turns many systems into one next action.
- Atomic habit design built into each habit: cue, tiny action, identity, and reward.
- Daily reminder scheduling for atomic habit cues.
- Companion monster growth tied to real habit behavior.
- Emotion-aware habit loop with mood, energy, stress, notes, and trends.
- Life-game rewards with quests, gems, loot, chests, crafting, achievements, and real-life rewards.
- Tomato Timer rewards that feed XP, coins, and progress.
- Offline-first JSON-shaped state with file backup, file restore, and copy/paste fallback.
- Future iOS path planned around the same portable data model.
