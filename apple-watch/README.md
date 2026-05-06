# MyLifePal Apple Watch

This folder contains a native SwiftUI watchOS companion scaffold for Apple Watch.

The Apple Watch app mirrors the wrist-friendly habit loop already added for Pixel Watch:

- See the next tiny action.
- Complete a habit with one tap.
- Track XP, coins, streaks, and daily completion state offline.
- Keep the UI deliberately small and glanceable.

## Open In Xcode

Use full Xcode, not only Command Line Tools:

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project apple-watch/MyLifePalWatch.xcodeproj \
  -scheme MyLifePalWatch \
  -sdk watchsimulator26.1 \
  -configuration Debug \
  build
```

For App Store distribution, add a real Apple Developer team, bundle identifier, icons, and phone-watch sync.
