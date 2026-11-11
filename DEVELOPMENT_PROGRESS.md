# DEVELOPMENT_PROGRESS

Project: NiteStand — StandBy Clock (Android)
Phase: v1.1.0 — OnePlus 12R / no-charger StandBy

## Current status
- Overall: v1.1.0 implemented and verified in the build environment; awaiting real-device results.
- Last completed task: no-charger StandBy (tile, opt-in lock-screen watcher, battery timeout),
  new activation modes, diagnostics screen, ONEPLUS_TESTING.md.
- Current task: none.
- Next task: user runs ONEPLUS_TESTING.md on the OnePlus 12R and reports results + Diagnostics
  "Copy report". Fix whatever OxygenOS behaviour the results reveal (especially test 12).

## Findings: what Android / OxygenOS allows without a charger
- Screen saver (DreamService): the system starts it only while charging/docked; running on battery
  needs the OEM framework flag config_dreamsEnabledOnBattery (not app-controllable). Not a
  no-charger solution.
- Background rotation detection + self-launch: blocked (no sensor events for idle apps since
  Android 9; background activity starts blocked since Android 10).
- Closest legitimate solutions implemented:
  1. "NiteStand" Quick Settings tile: one tap, no charger, active/inactive state, tap again to close.
  2. Opt-in "Lock-screen auto start": foreground service (specialUse) + full-screen-intent
     notification; sensor only while the locked phone is awake; edge-triggered.
  3. Battery screen-on timeout (default 30 min) so no-charger StandBy does not drain the battery.
- Hard limit: nothing can react while the screen is off (no wake lock by design); the user wakes
  the phone and StandBy follows.

## Completed (cumulative)
- v1.0.0: project, charging/orientation detection, state machine, StandBy activity, DreamService,
  5 clock themes, widgets, settings (DataStore), brightness/night/burn-in, tests, README, CI.
- v1.1.0:
  - Activation modes: Charging + landscape, Charging only, Landscape only, Manual, Screen saver,
    Quick Settings tile — each with a description in Settings
  - NiteStand tile (forced start, ACTIVE state via StandBySession, close from tile)
  - LockScreenTrigger (pure, tested), LockScreenWatcherService, StandByNotifications, BootReceiver
  - setTurnScreenOn for watcher launches; battery timeout for keep-screen-on
  - Diagnostics screen (device, screen saver, keyguard, permissions, live charging/orientation
    angles/StandBy state, watcher status, event log, copy report) + logcat tag "NiteStand"
  - ONEPLUS_TESTING.md (12 device tests with expected results and logs to capture)

## Known issues / limitations
- Not tested on a physical device (no device/emulator in the build container).
- Instrumentation tests compiled, not run.
- Whether OxygenOS on the 12R ships a screen saver page, lets QS tiles run on the lock screen,
  and honours full-screen intents for this sideloaded app is unknown until ONEPLUS_TESTING.md is run.
- New permissions (only used by the opt-in watcher): POST_NOTIFICATIONS, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_SPECIAL_USE, USE_FULL_SCREEN_INTENT, RECEIVE_BOOT_COMPLETED.

## Build
- Debug APK: SUCCESS
- Release APK 1.1.0 (versionCode 2, R8, v2-signed, same certificate as 1.0.0): SUCCESS
- Unit + screenshot tests: PASSING (99/99)
- Lint: 0 errors, 0 warnings
- Android tests: compile SUCCESS, not run

## Environment notes
- Android SDK is downloaded from dl.google.com (cloud environment "NiteStand" allows it).
- Maven Central sometimes returns HTTP 429; re-run the Gradle command.
- Robolectric android-all jar: download from
  https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7/
  and pass -Probolectric.dependency.dir=<dir>.
- Release signing key lives outside the repo (/root/.standby-signing in the build container only).

Last verified commit: see git log on branch ccr-6cf7a3e0-k2qpji
