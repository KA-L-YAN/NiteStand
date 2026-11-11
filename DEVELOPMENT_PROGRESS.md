# DEVELOPMENT_PROGRESS

Project: NiteStand — StandBy Clock (Android)
Phase: Testing & verification

## Completed
- Android project (AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.20, compile/target SDK 37, min SDK 26)
- Charging detection (sticky ACTION_BATTERY_CHANGED + POWER_CONNECTED/DISCONNECTED)
- Orientation detection (gravity / accelerometer, threshold + hysteresis + settle time)
- StandBy activation state machine
- Clock formatting / ticker, 5 clock themes
- Widgets (clock, date, battery, next alarm, weather placeholder)
- StandBy activity (Mode A), DreamService (Mode B), Quick Settings tile
- Setup + Material 3 settings screens, DataStore settings
- Brightness, night mode, burn-in protection

## Currently working on
- Unit tests + instrumentation tests

## Next
- README, release signing, CI workflow, release APK

## Build
- Debug APK: SUCCESS
- Unit tests: not yet written
