# Kotlin Migration Plan

MyLifePal should move toward Kotlin as the default Android language while keeping each release buildable during the migration.

## Current State

- Root Gradle tooling enables `org.jetbrains.kotlin.android` version `2.3.21` with Android Gradle Plugin `8.13.2`.
- Phone and Wear modules both compile with the Kotlin Android plugin.
- The Pixel Watch / Wear OS activity has been migrated from Java to Kotlin at `wear/src/main/kotlin/com/mylifepal/watch/WatchActivity.kt`.
- The phone app reminder scheduler, reminder receiver, and home-screen widget provider have been migrated to Kotlin under `app/src/main/kotlin/com/mylifepal/app/`.
- The only remaining Android Java source is the large phone MVP activity at `app/src/main/java/com/mylifepal/app/MainActivity.java`. It can safely mix with Kotlin while screens are moved over incrementally.

## Why Kotlin

- Kotlin is the Android-first direction for modern app work.
- It reduces boilerplate in state models, UI event handlers, null handling, and collection logic.
- It keeps Java interoperability, so the phone app can be migrated feature by feature instead of in one risky rewrite.
- It also leaves a better future path for shared Android/iOS business logic through Kotlin Multiplatform after the native iOS release starts.

## Recommended Next Steps

1. Extract phone app state models from `app/src/main/java/com/mylifepal/app/MainActivity.java` into Kotlin data classes.
2. Move backup serialization and restore validation into Kotlin helpers.
3. Move password hashing and unlock state into a Kotlin security helper with unit tests.
4. Split the main phone UI into Kotlin screen builders for Today, Habits, Rewards, Mood, Timer, Progress, and Appearance.
5. Add Android unit tests for habit completion, streak rollover, reward claims, timer completion, backup import, reminder scheduling, widget stats, and password verification.
6. After the phone app is mostly Kotlin, evaluate Jetpack Compose for future UI work instead of continuing to grow imperative view code.
7. Before iOS parity work, identify state and rules that could become a Kotlin Multiplatform shared core.

## Guardrails

- Keep package names stable: `com.mylifepal.app` and `com.mylifepal.watch`.
- Migrate one behavior area at a time and run Gradle after each slice.
- Do not change backup JSON keys unless a versioned migration is added.
- Preserve offline-first behavior and local-only password storage.
- Avoid a full UI rewrite until the state model has tests.
