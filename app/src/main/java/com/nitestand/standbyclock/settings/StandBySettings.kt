package com.nitestand.standbyclock.settings

import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.standby.ActivationMode

/** v1 accent colour. Superseded by per-face [ClockPalette]s; kept so old settings still parse. */
enum class AccentColor { WHITE, WARM, RED, BLUE, MINT }

/** Every user preference. Defaults define the out-of-the-box experience. */
data class StandBySettings(
    // General
    val standByEnabled: Boolean = true,
    val autoStart: Boolean = true,
    val showOnLockScreen: Boolean = true,
    // Activation
    val activationMode: ActivationMode = ActivationMode.CHARGING_AND_LANDSCAPE,
    val orientationSensitivity: OrientationSensitivity = OrientationSensitivity.NORMAL,
    /** Opt-in foreground service that starts StandBy on the lock screen (see LockScreenWatcherService). */
    val lockScreenWatcher: Boolean = false,
    /** On battery, keep the screen on this long before letting the system timeout apply; 0 = until exit. */
    val batteryTimeoutMinutes: Int = 30,
    // Clock
    val clockTheme: ClockTheme = ClockTheme.DEFAULT,
    val hourFormat: HourFormat = HourFormat.SYSTEM,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    /** 12-hour mode only: show the AM/PM marker. */
    val showAmPm: Boolean = true,
    val dateStyle: DateStyle = DateStyle.LONG,
    /** Colour chosen per clock face; faces without an entry use their default palette. */
    val faceColors: Map<ClockTheme, ClockPalette> = emptyMap(),
    /** World clock: primary city key ([com.nitestand.standbyclock.clock.WorldCities.LOCAL] = phone zone). */
    val worldPrimaryCity: String = "",
    /** World clock: secondary city key, or [com.nitestand.standbyclock.clock.WorldCities.NONE]. */
    val worldSecondaryCity: String = "london",
    // Widgets — off by default: the StandBy screen is just the clock.
    val showPanel: Boolean = false,
    /** Second StandBy page (swipe sideways) with date, battery, alarm and weather as floating type. */
    val showInfoPage: Boolean = true,
    val showDateWidget: Boolean = true,
    val showBatteryWidget: Boolean = true,
    val showAlarmWidget: Boolean = true,
    val showWeatherWidget: Boolean = false,
    // Appearance
    val brightness: BrightnessLevel = BrightnessLevel.AUTO,
    val nightMode: NightMode = NightMode.SCHEDULED,
    val nightStartMinutes: Int = 22 * 60,
    val nightEndMinutes: Int = 7 * 60,
    val accent: AccentColor = AccentColor.WHITE,
    val reduceMotion: Boolean = false,
    // Burn-in protection
    val burnInProtection: Boolean = true,
    val burnInIntervalSeconds: Int = 45,
) {
    val requireCharging: Boolean get() = activationMode.requiresCharging
    val requireLandscape: Boolean get() = activationMode.requiresLandscape

    companion object {
        val BURN_IN_INTERVALS = listOf(30, 45, 60, 120)
        val BATTERY_TIMEOUTS = listOf(5, 15, 30, 60, 0)
    }
}
