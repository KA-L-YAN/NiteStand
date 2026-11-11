package com.nitestand.standbyclock.settings

import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.standby.ActivationMode

/** Accent colour family used by the clock faces. */
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
    // Clock
    val clockTheme: ClockTheme = ClockTheme.CLASSIC,
    val hourFormat: HourFormat = HourFormat.SYSTEM,
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    // Widgets
    val showPanel: Boolean = true,
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
    }
}
