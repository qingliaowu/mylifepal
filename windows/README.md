# MyLifePal Windows

This folder contains the native Windows desktop version of MyLifePal. It is a dependency-light WPF app targeting `.NET 8` and Windows.

## Build

On Windows with the .NET 8 SDK installed:

```powershell
.\build-windows-app.ps1
```

Or from this folder:

```powershell
dotnet build .\MyLifePal.Windows\MyLifePal.Windows.csproj -c Debug
```

The debug app is written under:

```text
windows\MyLifePal.Windows\bin\Debug\net8.0-windows\
```

## Included

- Native WPF desktop app with sidebar navigation.
- Today, Timer, Mood, Habits, Rewards, Progress, and Appearance screens.
- Offline JSON state in `%APPDATA%\MyLifePal\mylifepal-windows-state.json`.
- Atomic habit creation with cue, tiny action, identity, reward, attribute, reminder time.
- Local reminder and timer notifications while the app is running.
- Tomato Timer with focus, short break, and long break modes.
- Mood, energy, stress, and notes.
- XP, coins, gems, reward shop, inventory, companion growth, and achievements.
- Theme presets and custom hex colors.
- Local security password with unlock-on-launch, lock-now, change, disable, and salted hash storage.
- Portable JSON export/import, including Android backup-shape import.
