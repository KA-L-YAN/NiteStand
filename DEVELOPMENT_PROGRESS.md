# DEVELOPMENT_PROGRESS

Project: NiteStand — StandBy Clock (Android)
Phase: v2.3.0 — real weather (Open-Meteo)

## v2.3.0 — real weather
- Replaced the sample-data weather with Open-Meteo (no API key): OpenMeteoClient (HttpURLConnection),
  OpenMeteoParser (org.json, WMO code mapping), OpenMeteoWeatherProvider (30-min cache reused across
  StandBy sessions, 10-min retry, last report kept ≤ 3 h, coarse last-known location).
- Location: city search (Open-Meteo geocoding; default, no permission) or approximate location
  (ACCESS_COARSE_LOCATION, requested only when chosen). Coordinates rounded to 2 decimals.
- New permissions: INTERNET, ACCESS_COARSE_LOCATION (optional). Diagnostics has a Weather section.
- Not verified: live API calls (Open-Meteo hosts are blocked in the build container); parsing is
  tested against fixtures in the documented response format. Needs a check on the phone.

## v2.2.1 — actual root cause (second OnePlus 12R trace)
- 2.2.0 trace: screen OFF, keyguard locked, landscape, full-screen access granted, channel HIGH —
  yet no launch. Cause (verified in androidx.core 1.19.1 bytecode): the launch notification used
  NotificationCompat.Builder.setSilent(true), which puts it in group "silent" with
  GROUP_ALERT_SUMMARY; SystemUI never launches a full-screen intent whose alerts are suppressed by
  its group (NO_FSI_SUPPRESSIVE_GROUP_ALERT_BEHAVIOR) and shows a plain notification instead.
  Introduced in 2.1.0. Fix: removed setSilent (channel is already silent); regression test
  LaunchNotificationTest; the trace now logs the notification's group/alert state.
- Device confirmation: CONFIRMED by the user on the OnePlus 12R (OxygenOS 16.0.5.1301) —
  automatic StandBy launch on lock works as expected with 2.2.1.

## v2.2.0 — findings from the first real OnePlus 12R trace (OxygenOS 16.0.5.1301, Android 16)
- With every prerequisite granted ("Verdict: Ready"), a full-screen intent posted while the AWAKE
  lock screen showed was presented as a notification (no launch); direct start was also blocked.
- The screen-off path never ran in landscape: the phone was FLAT / PORTRAIT at the instant of
  screen-off (users lock first, then place the phone), and a failed awake-lock-screen attempt had
  disarmed the trigger 12 s earlier (armed=false at 17:04:38).
- Fix: no launches from the awake lock screen (arming bookkeeping only); after every screen-off,
  watch the orientation for 12 s (settle 0.5 s, partial wake lock ≤ 18 s, released when done) and
  launch via the non-interactive full-screen intent; trace labels for fallback taps.
- Still to confirm on the device: that the non-interactive full-screen intent launches StandBy.

## v2.1.0 — automatic launch fix (root cause and fix)
- User report (real OnePlus 12R, 2.0.0): locking in landscape showed a "tap to show StandBy"
  notification instead of StandBy.
- Root cause (from the code; device behaviour unverified here):
  1. The watcher only fired with the screen ON + keyguard locked, i.e. not when the user locks the
     phone (screen OFF) but later on wake — the interactive-lock-screen FSI case SystemUI may
     present as a heads-up.
  2. The FSI notification's own text was an instruction ("Tap to show the StandBy clock"), so any
     declined FSI (permission denied on Android 14+, demoted channel, OEM policy) looked like the
     designed behaviour.
  3. No verification that the activity resumed, no failure classification, no trace.
  4. Startup: waiting UI flashed while orientation was UNKNOWN; system bars visible until ACTIVE.
- Fix: ActivationTrigger state machine (IDLE → ACTIVATION_DETECTED → LAUNCH_REQUESTED →
  STANDBY_ACTIVE | LAUNCH_FAILED → FALLBACK_NOTIFICATION; once per placement; re-arm on unlock /
  portrait / unplug), screen-off activation with one-shot orientation sample under a short wake
  lock, direct start → FSI (fresh HIGH channel v2, PendingIntent creator BAL opt-in) → resume
  verification → single classified fallback; LaunchReadiness with in-settings fixes; ActivationTrace
  in Diagnostics; black hold instead of waiting UI; immersive from the first frame; entrance
  re-timed (blur-condense, Wake easing); tabular digits (no colon/neighbour movement); warm
  night path.
- Physical-device result: NOT verified in this environment (no device). Test 15 in
  ONEPLUS_TESTING.md is the acceptance test.

## Current status
- Overall: v2.0.0 implemented and verified in the build environment (JVM tests, Robolectric
  screenshot review incl. transition frames). NOT yet seen on the physical device; frame rate and
  CPU not measurable here (ONEPLUS_TESTING.md test 14 measures them).
- v2.0.0 work: see docs/REDESIGN_PLAN.md. Stage composition (entrance/exit, burn-in drift, pages),
  TimeText v2 (per-glyph transitions, drawn colon, constant scale, proportional figures, no
  first-frame flash), ClockFace interface + registry, 3 new faces (Large Hands, Thin Line, Orbit),
  animated style (colour + night environment with 3-level hierarchy), motion tokens, floating info
  page, custom window animations, grouped settings, Night brightness level.
- Previous (v1.2.0):
- Last completed task: clock-face gallery (15 faces), palette colour system, central formatting
  (AM/PM, date styles), smooth night transition, brightness ramp, settings migration v2.
- Current task: none.
- Next task: user runs ONEPLUS_TESTING.md test 15 (automatic launch) + scenarios A–F and sends the
  Diagnostics report; then tests 13–14 (incl. gfxinfo frame stats); then tune
  durations/sizes from real-panel feedback. Previously: test 13 on the OnePlus 12R (photos in a dark room) and
  reports any face that needs size/brightness/spacing tuning on the real panel.

## v1.2.0 clock-face work
- Plan followed: domain (ClockTheme/ClockPalette/ClockFaceCatalog/WorldCities, settings + migration)
  → shared renderer (TimeText, fonts) → faces → style/night → gallery → tests → visual review → docs.
- Faces: StandBy (default), Color, Minimal, Rounded Bold, World, Analog, Analog Minimal, Analog Modern,
  Analog Elegant, Bubble, Serif, Line, Calendar, Retro, Ambient.
- Fonts (OFL 1.1, bundled): Nunito, Outfit, Playfair Display — licences in assets/licenses.
- Settings migration v2: CLASSIC (old default) → DIGITAL_LARGE, side panel off, non-white v1 accent
  carried over as the StandBy face colour. Other face choices kept. Old enum names still parse.
- Activation, watcher, dream, tile, diagnostics untouched apart from the brightness ramp.

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
- Release APK 2.3.0 (versionCode 8, R8, v2-signed, same certificate as earlier versions): SUCCESS
- Unit + screenshot tests: PASSING (see last report; screenshots in app/build/screenshots)
- Lint: 0 errors, 0 warnings
- Android tests: compile SUCCESS, not run

## Environment notes
- Android SDK is downloaded from dl.google.com (cloud environment "NiteStand" allows it).
- Maven Central sometimes returns HTTP 429; re-run the Gradle command.
- Robolectric android-all jar: download from
  https://maven-central.storage-download.googleapis.com/maven2/org/robolectric/android-all-instrumented/15-robolectric-13954326-i7/
  and pass -Probolectric.dependency.dir=<dir>.
- Release signing key lives outside the repo (/root/.standby-signing in the build container only).

Last verified commit: cff3300 (branch ccr-6cf7a3e0-k2qpji)
