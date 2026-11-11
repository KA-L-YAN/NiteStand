# DEVELOPMENT_PROGRESS

Project: NiteStand — StandBy Clock (Android)
Phase: v1.0.0 complete

## Current status
- Overall: feature-complete v1.0.0; debug and signed release APKs build.
- Last completed task: README, CI workflow, release build and verification.
- Current task: none.
- Next task (optional): run the instrumentation tests on a physical device or emulator
  (`./gradlew connectedDebugAndroidTest`) and do the manual checklist in README.md.

## Completed
- Android project (AGP 9.5.1 built-in Kotlin, Gradle 9.8.2, Kotlin 2.4.21, compile/target SDK 37, min SDK 26)
- Charging detection (sticky ACTION_BATTERY_CHANGED + POWER_CONNECTED/DISCONNECTED, no polling)
- Orientation detection (gravity / accelerometer, tolerance + hysteresis + settle time, both landscape directions)
- StandBy activation state machine (IDLE / CHARGING / WAITING_FOR_LANDSCAPE / READY / ACTIVE)
- StandBy activity (Mode A): immersive, sensorLandscape, keep-screen-on only while active, show-when-locked
- DreamService (Mode B) with Compose lifecycle host, 30 s idle grace
- Quick Settings tile, dynamic launcher shortcut
- 5 clock themes: Classic, Minimal, Retro (seven-segment Canvas), Analog, Ambient
- Widgets: Clock, Date, Battery, Next alarm, Weather (offline placeholder)
- Brightness (per-window), Night mode (on / off / scheduled), burn-in pixel shift
- Setup screen and Material 3 settings, persisted with DataStore
- Accessibility: TalkBack labels, custom actions, reduced motion
- 81 JVM tests (70 unit + 11 Robolectric screenshot renders, reviewed visually), Compose/activity instrumentation tests (compiled)
- README, GitHub Actions CI

## Known issues / limitations
- Instrumentation tests compiled but not executed (no emulator/KVM in the build container).
- APK not installed on a real device from the build environment (no device available).
- OEM screen-saver availability varies (see README).
- The release APK produced in the build session was signed with a throwaway key kept outside the repo;
  future releases need the maintainer's own key (see README "Release signing").

## Build
- Debug APK: SUCCESS
- Release APK (R8, signed v2): SUCCESS
- Unit + screenshot tests: PASSING (81/81)
- Lint: no errors, no warnings
- Android tests: compile SUCCESS, not run

## Environment notes
- Android SDK is downloaded from dl.google.com (cloud environment "NiteStand" allows it).
- Maven Central sometimes returns HTTP 429; simply re-run the Gradle command.
- Robolectric's android-all jar (SDK 35) is rate limited per IP on Maven Central; download it from
  https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7/
  and run tests with -Probolectric.dependency.dir=<dir>.

Last verified commit: cfc837d (branch ccr-6cf7a3e0-k2qpji)
