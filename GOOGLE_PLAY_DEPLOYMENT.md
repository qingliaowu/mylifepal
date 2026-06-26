# Google Play Deployment Guide

Last reviewed: 2026-06-22

This guide is written for the current MyLifePal repository. A new developer should be able to follow it from a clean checkout, build a release bundle, complete Play Console setup, run testing, and submit the app for production review.

Official references used for this guide:

- Create and set up an app: https://support.google.com/googleplay/android-developer/answer/9859152
- Prepare and roll out a release: https://support.google.com/googleplay/android-developer/answer/9859348
- Sign an Android app: https://developer.android.com/studio/publish/app-signing
- Target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- New personal account testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- Data Safety form: https://support.google.com/googleplay/android-developer/answer/10787469
- App review declarations: https://support.google.com/googleplay/android-developer/answer/9859455
- Store preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Form factor and Wear OS tracks: https://support.google.com/googleplay/android-developer/answer/13295490

## Current Project Values

Phone app:

- Gradle module: `:app`
- Application ID: `com.mylifepal.app`
- Namespace: `com.mylifepal.app`
- Minimum SDK: 23
- Target SDK: 36
- Version code: 1
- Version name: `0.1.0`
- Release output: `app/build/outputs/bundle/release/app-release.aab`

Wear OS app:

- Gradle module: `:wear`
- Application ID: `com.mylifepal.watch`
- Namespace: `com.mylifepal.watch`
- Minimum SDK: 26
- Target SDK: 36
- Version code: 1
- Version name: `0.1.0`
- Release output: `wear/build/outputs/bundle/release/wear-release.aab`

Important: `com.mylifepal.watch` is a separate package name. The simplest launch path is to publish the phone app first, then publish the Wear OS app as a separate Play Console app or refactor later into a Wear-enabled app bundle if you want one shared Play listing.

## Before You Start

You need:

- A Google Play developer account.
- Access to Play Console: https://play.google.com/console
- Android Studio or a working Android SDK/JDK environment.
- A private support email address for users.
- A public privacy policy URL.
- Final app icon, feature graphic, and screenshots.
- At least 12 testers if using a new personal developer account that must satisfy closed testing requirements.

Do not commit:

- Upload keystores.
- Keystore passwords.
- `keystore.properties`.
- Downloaded release `.aab` or `.apk` files.

This repo already ignores `keystore.properties`, `release-secrets/`, `*.jks`, `*.keystore`, `*.aab`, and `*.apk`.

## One-Time Signing Setup

Google Play requires signed app bundles. This project reads release signing values from a local file named `keystore.properties`.

1. Create a private folder for release secrets:

```sh
mkdir -p release-secrets
```

2. Create an upload key:

```sh
keytool -genkeypair -v \
  -keystore release-secrets/mylifepal-upload.jks \
  -alias mylifepal-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

3. Create `keystore.properties` at the repo root:

```properties
storeFile=release-secrets/mylifepal-upload.jks
storePassword=REPLACE_WITH_STORE_PASSWORD
keyAlias=mylifepal-upload
keyPassword=REPLACE_WITH_KEY_PASSWORD
```

4. Back up the keystore and passwords in a secure password manager.

If the upload key is lost, future updates become painful. If the Play app signing key is lost or mishandled, recovery is even harder. Treat signing assets like production credentials.

## Versioning Rules

Every Play upload must have a new `versionCode`.

For the first phone release, current values are okay:

```gradle
versionCode 1
versionName "0.1.0"
```

For the next phone update:

```gradle
versionCode 2
versionName "0.1.1"
```

Do the same for `wear/build.gradle` if publishing a Wear update. Never reuse a version code that has already been uploaded to Play Console.

## Build Release Bundles

Clean and verify debug builds first:

```sh
./gradlew :app:assembleDebug :wear:assembleDebug
```

Build the phone release bundle:

```sh
./gradlew :app:bundleRelease
```

Expected output:

```text
app/build/outputs/bundle/release/app-release.aab
```

Build the Wear OS release bundle:

```sh
./gradlew :wear:bundleRelease
```

Expected output:

```text
wear/build/outputs/bundle/release/wear-release.aab
```

If `keystore.properties` is missing, Gradle may still create an unsigned release artifact. Do not upload an unsigned bundle. Confirm signing before uploading through Android Studio, Play Console upload validation, or `jarsigner`.

## Local Release Smoke Test

Before uploading, test the app on real devices where possible.

Phone checklist:

- Launches without crash.
- Today screen renders.
- Add a habit.
- Complete a habit.
- Start, pause, reset, and finish Tomato Timer.
- Record mood, energy, stress, and note.
- Claim reward or verify reward economy.
- Change theme colors.
- Create security password, lock, unlock, change password, disable password.
- Export JSON backup.
- Import JSON backup.
- Add home screen widget if available.
- Enable reminder and verify notification permission flow.
- Reboot test device if possible and verify reminders reschedule.

Wear checklist:

- Installs on a Wear OS device or emulator.
- Shows next action.
- Completes a habit.
- Starts, pauses, resets, and completes timer.
- Records quick emotion check-in.

## Create The Play Console App

1. Open Play Console.
2. Select `Home > Create app`.
3. Default language: choose the launch language.
4. App name: `MyLifePal`.
5. App or game: choose `App` for the phone app.
6. Free or paid: choose carefully. You can change free to paid only by creating a new app, so most teams start free if unsure.
7. Add support email.
8. Accept Developer Program Policies, US export laws declaration, and Play App Signing terms.
9. Select `Create app`.

Package names are permanent. Confirm `com.mylifepal.app` before first upload.

## Store Listing

Recommended app name:

```text
MyLifePal
```

Recommended short description, under 80 characters:

```text
Tiny habits, focus timer, mood checks, rewards, and companion growth.
```

Recommended full description:

```text
MyLifePal turns daily habits into a simple life game.

Build tiny atomic habits with cues, identity prompts, small actions, and rewards. Track habits with completion, quantity, and time modes. Use the Tomato Timer for focused sessions, record emotion check-ins, earn XP and coins, grow a companion, customize colors, and keep local backups of your progress.

Core features:
- Atomic habit builder with cue, tiny action, identity, reward, attribute, and reminder time.
- Timecap-style completion, quantity, and time tracking.
- Tomato Timer for focus sessions and breaks.
- Mood, energy, stress, and note check-ins.
- Life-game progress with XP, coins, gems, quests, rewards, inventory, achievements, and companion growth.
- Custom color themes.
- Local security password.
- Local JSON backup and restore.
- Home screen widget.
- Offline-first design with no account required.

MyLifePal is designed for personal productivity, reflection, and habit building. It is not a medical app and does not provide diagnosis, treatment, or professional health advice.
```

Avoid in the listing:

- Claims like `best`, `#1`, `300% better`, `guaranteed`, or `medical treatment`.
- Competitor names.
- Keyword stuffing.
- Emojis or repeated symbols in metadata.
- Any statement that contradicts Data Safety or permissions.

Recommended category:

```text
Productivity
```

Possible tags:

- Productivity
- Task management
- Habit tracking, if offered by Play Console
- Time management, if offered by Play Console
- Wellness only if the listing avoids medical claims

Use no more than five tags and choose only tags that match the actual first-run experience.

## Store Assets

Required app icon:

- 32-bit PNG with alpha.
- 512 x 512 px.
- Maximum 1024 KB.
- No misleading price/ranking/category badges.

Required feature graphic:

- JPEG or 24-bit PNG, no alpha.
- 1024 x 500 px.
- Show the app experience and brand clearly.
- Keep key content centered.
- Avoid `Best`, `#1`, `Free`, sale language, Google Play badges, and device frames.

Screenshots:

- Provide at least two screenshots across device types.
- For better promotion eligibility, provide at least four phone screenshots at minimum 1080 px resolution.
- Use real app screens.
- Recommended phone screenshots:
  - Today coach and next habit.
  - Habit Studio.
  - Tomato Timer.
  - Mood check-in.
  - Rewards and companion.
  - Progress and achievements.
  - Theme customization.
  - Backup/security screen.
- Add alt text for every image.

Wear OS screenshots, if publishing Wear:

- At least one screenshot.
- 1:1 aspect ratio.
- Minimum 384 x 384 px.
- Show only the Wear OS app interface.
- Do not use device frames, extra backgrounds, or transparent masking.

## App Content Declarations

Open `Policy and programs > App content` and complete every item that needs attention.

Privacy policy:

- Required if the app accesses sensitive permissions or data.
- Required if targeting children.
- Strongly recommended for this app regardless, because it stores mood notes, reminders, backups, and optional password hashes locally.
- Host it at a stable public URL.
- The same URL must remain accessible during review.

Privacy policy draft:

```text
MyLifePal Privacy Policy

MyLifePal is an offline-first habit and productivity app. The app stores habits, reminders, timer progress, mood check-ins, rewards, themes, backup data, and optional security password hash data locally on your device.

We do not operate a server for MyLifePal and do not sell personal data. The current app does not require an account. Backup files are created only when you choose to export them, and you control where those files are stored or shared.

The app may request notification permission to show habit reminders and timer notifications. It may use the boot completed permission to reschedule reminders after your device restarts.

If future versions add cloud sync, analytics, crash reporting, accounts, subscriptions, or third-party SDKs, this policy and the Google Play Data Safety disclosure must be updated before release.

Contact: REPLACE_WITH_SUPPORT_EMAIL
Effective date: REPLACE_WITH_DATE
```

Data Safety:

- If the current app remains offline-only, has no analytics, no ads, no crash SDK, no cloud sync, no accounts, and no server transmission, disclose that no user data is collected or shared.
- If you add analytics, crash reporting, cloud backup, login, subscriptions, ads, or any SDK that receives data, update Data Safety before uploading.
- Mention security practices accurately. Local backups are user-controlled files; do not claim server encryption if there is no server.

Ads:

- Current app: answer `No`, unless ads or ad SDKs are added.

Sign-in details / app access:

- Current app does not require an account.
- If you enable app lock by default in a future build, provide review instructions and a test password.
- If reviewers cannot reach functionality, review may fail.

Target audience:

- Recommended initial target: adults/teens, not children.
- Do not target children unless you are ready for Families policy requirements and a stronger privacy policy.

Content rating:

- Complete the questionnaire honestly.
- This app should usually rate low if it contains no violence, gambling, sexual content, user-generated public content, or purchases, but the questionnaire decides.

Permissions:

- `POST_NOTIFICATIONS`: used for reminders and timer notifications.
- `RECEIVE_BOOT_COMPLETED`: used to reschedule reminders after reboot.
- No SMS, Call Log, Contacts, Location, Camera, Microphone, or other high-risk permissions are currently used.

## Internal Testing

Use internal testing before closed testing.

1. Go to `Test and release > Testing > Internal testing`.
2. Create or select a tester email list.
3. Create a new release.
4. Upload `app-release.aab`.
5. Add release notes:

```text
Initial internal test for MyLifePal 0.1.0.

Please test habit creation, completion, timer, mood check-in, rewards, themes, backup/restore, reminders, widget, and local security password.
```

6. Save, review, and roll out to internal testing.
7. Send the opt-in link to testers.

Internal testing is fast and good for smoke testing, but new personal accounts still need closed testing before production access.

## Closed Testing

If your Play Console personal developer account was created after 2023-11-13, Google requires a closed test with at least 12 opted-in testers for 14 continuous days before production access.

Recommended tester plan:

- Recruit 15 to 25 people so you stay above 12 even if some drop out.
- Use real Android devices, not only emulators.
- Ask testers to stay opted in for the full 14 days.
- Give testers a simple script.
- Collect feedback in email, a form, or a chat group.

Tester script:

```text
Day 1:
- Install MyLifePal.
- Create one habit.
- Complete one habit.
- Record a mood check-in.
- Start and pause the Tomato Timer.

Day 2-4:
- Complete at least one habit each day.
- Try quantity or time tracking.
- Change theme colors.
- Enable a reminder.

Day 5-7:
- Use the reward shop.
- Check companion progress.
- Export a backup.
- Try the home screen widget.

Day 8-10:
- Create a security password.
- Lock and unlock the app.
- Change or disable the password.

Day 11-14:
- Keep using the daily loop.
- Report crashes, confusing screens, missing feedback, or layout issues.
```

Closed testing setup:

1. Go to `Test and release > Testing > Closed testing`.
2. Create a track, for example `Beta`.
3. Add testers by email list or Google Group.
4. Create a release.
5. Upload `app-release.aab`.
6. Add release notes.
7. Review and roll out.
8. Send opt-in link to testers.
9. Monitor `Ratings and reviews > Testing feedback`.
10. Fix issues and upload new closed test builds as needed.

Production access application:

- Go to the app dashboard.
- Select `Apply for production` after the requirements are met.
- Be ready to summarize:
  - How testers were recruited.
  - Whether testers used all major features.
  - What feedback was received.
  - What changes were made based on feedback.
  - Why the app is ready for production.

## Production Release

Do not submit production until:

- Store listing is complete.
- App Content has no blocking items.
- Privacy policy URL works.
- Data Safety is accurate.
- Content rating is complete.
- Closed test requirements are met, if required.
- Pre-launch report issues are reviewed.
- Release bundle is signed.
- `versionCode` has not been uploaded before.

Production release steps:

1. Go to `Test and release > Production`.
2. Select `Create new release`.
3. Confirm Play App Signing.
4. Upload `app-release.aab`.
5. Add release name:

```text
0.1.0
```

6. Add release notes:

```text
Initial release of MyLifePal.

- Build atomic habits with cues, tiny actions, identity prompts, and rewards.
- Track habits with completion, quantity, and time modes.
- Use Tomato Timer focus sessions.
- Record mood, energy, stress, and notes.
- Earn XP, coins, rewards, achievements, and companion growth.
- Customize colors, use local backup/restore, and protect the app with a local password.
```

7. Click `Next`.
8. Resolve warnings and errors.
9. Send for review.

Recommended rollout:

- Start with staged rollout at 5% if available.
- Watch Android vitals and reviews for 24 to 48 hours.
- Increase to 20%, then 50%, then 100% if stable.

## Wear OS / Pixel Watch Deployment

Current repo state:

- Phone package: `com.mylifepal.app`
- Wear package: `com.mylifepal.watch`

Because the package names differ, the simplest deployment is a separate Wear OS Play Console app. If you want one shared app listing later, refactor the Wear experience into a Wear-enabled app bundle before launch.

Separate Wear OS app path:

1. Create a second Play Console app named `MyLifePal for Watch` or similar.
2. Use app type `App`.
3. Complete store listing and App Content.
4. Build:

```sh
./gradlew :wear:bundleRelease
```

5. Upload:

```text
wear/build/outputs/bundle/release/wear-release.aab
```

6. Add Wear OS screenshots:
   - 1:1 ratio.
   - Minimum 384 x 384 px.
   - Actual Wear UI only.
7. Opt in to Wear OS review.
8. Test on a real Pixel Watch or Wear OS emulator before production.

Shared listing future path:

- Align app architecture with Play's Wear-enabled app bundle requirements.
- Use Play Console form factor settings.
- Go to `Test and release > Advanced settings > Form factors`.
- Add or manage Wear OS.
- Use dedicated Wear OS release tracks if required.

## Update Releases

For every update:

1. Make code changes.
2. Increase `versionCode`.
3. Update `versionName` if user-visible.
4. Build release bundle.
5. Test locally.
6. Upload to internal testing.
7. Promote to closed/open/production as appropriate.
8. Update Data Safety before release if data behavior changed.
9. Update screenshots if the UI changed significantly.
10. Add clear release notes.

## Troubleshooting

`versionCode already used`

- Increase `versionCode` in the relevant module.
- Rebuild and upload again.

`Target API level too low`

- Update `targetSdk` to the current Play requirement.
- As of this guide, the app targets SDK 36, which satisfies the current minimum for new phone apps.

`Bundle is not signed`

- Make sure `keystore.properties` exists.
- Make sure `storeFile` points to a real `.jks` file.
- Re-run `./gradlew :app:bundleRelease`.

`Wrong signing key`

- Do not create another random key for the same app unless Play Console asks for key reset/recovery.
- Use the original upload key for updates.

`Review rejected because reviewers could not access the app`

- If any feature is locked, provide Sign-in details or unlock instructions in Play Console.
- Current app lock is optional and off by default, so this should not block review.

`Data Safety rejected or inconsistent`

- Recheck every SDK and permission.
- If analytics, crash reporting, cloud sync, ads, subscriptions, or login were added, update Data Safety.
- Keep privacy policy consistent with Data Safety.

`Closed testing requirement not met`

- Confirm at least 12 testers are opted in.
- Confirm they stayed opted in for 14 continuous days.
- Keep testing active and apply again after the requirement is satisfied.

`Wear OS upload confusion`

- This repo currently uses a separate Wear package. Publish it as a separate Wear app first, or refactor before trying to use a single shared listing.

## Final Launch Checklist

Before pressing production submit:

- [ ] `applicationId` is final.
- [ ] `versionCode` is new.
- [ ] Release bundle is signed.
- [ ] App launches on real Android device.
- [ ] Habit flow works.
- [ ] Timer works.
- [ ] Mood flow works.
- [ ] Rewards and companion flow work.
- [ ] Backup export/import works.
- [ ] Security password flow works.
- [ ] Notification permission flow works.
- [ ] Widget works.
- [ ] Store listing has no risky claims.
- [ ] Screenshots match current UI.
- [ ] Privacy policy URL works.
- [ ] Data Safety matches the app exactly.
- [ ] Ads declaration is correct.
- [ ] Content rating is complete.
- [ ] Target audience is correct.
- [ ] Closed testing requirement is satisfied, if required.
- [ ] Pre-launch report has no critical issues.
- [ ] Release notes are clear.
- [ ] Staged rollout plan is ready.
