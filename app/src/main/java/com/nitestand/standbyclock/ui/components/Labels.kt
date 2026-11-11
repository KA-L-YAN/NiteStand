package com.nitestand.standbyclock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.settings.AccentColor
import com.nitestand.standbyclock.standby.ActivationMode

@Composable
fun ClockTheme.label(): String = stringResource(
    when (this) {
        ClockTheme.CLASSIC -> R.string.theme_classic
        ClockTheme.MINIMAL -> R.string.theme_minimal
        ClockTheme.RETRO -> R.string.theme_retro
        ClockTheme.ANALOG -> R.string.theme_analog
        ClockTheme.AMBIENT -> R.string.theme_ambient
    },
)

@Composable
fun HourFormat.label(): String = stringResource(
    when (this) {
        HourFormat.SYSTEM -> R.string.hour_format_system
        HourFormat.TWELVE_HOUR -> R.string.hour_format_12
        HourFormat.TWENTY_FOUR_HOUR -> R.string.hour_format_24
    },
)

@Composable
fun ActivationMode.label(): String = stringResource(
    when (this) {
        ActivationMode.CHARGING_AND_LANDSCAPE -> R.string.mode_charging_landscape
        ActivationMode.CHARGING_ONLY -> R.string.mode_charging_only
        ActivationMode.LANDSCAPE_ONLY -> R.string.mode_landscape_only
        ActivationMode.MANUAL -> R.string.mode_manual
    },
)

@Composable
fun OrientationSensitivity.label(): String = stringResource(
    when (this) {
        OrientationSensitivity.STRICT -> R.string.sensitivity_strict
        OrientationSensitivity.NORMAL -> R.string.sensitivity_normal
        OrientationSensitivity.RELAXED -> R.string.sensitivity_relaxed
    },
)

@Composable
fun BrightnessLevel.label(): String = stringResource(
    when (this) {
        BrightnessLevel.AUTO -> R.string.brightness_auto
        BrightnessLevel.LOW -> R.string.brightness_low
        BrightnessLevel.MEDIUM -> R.string.brightness_medium
        BrightnessLevel.HIGH -> R.string.brightness_high
    },
)

@Composable
fun NightMode.label(): String = stringResource(
    when (this) {
        NightMode.OFF -> R.string.night_off
        NightMode.ON -> R.string.night_on
        NightMode.SCHEDULED -> R.string.night_scheduled
    },
)

@Composable
fun AccentColor.label(): String = stringResource(
    when (this) {
        AccentColor.WHITE -> R.string.accent_white
        AccentColor.WARM -> R.string.accent_warm
        AccentColor.RED -> R.string.accent_red
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.MINT -> R.string.accent_mint
    },
)

@Composable
fun BatterySnapshot?.statusLabel(): String {
    if (this == null) return stringResource(R.string.battery_unknown)
    val status = stringResource(
        when (status) {
            ChargingStatus.CHARGING -> R.string.battery_charging
            ChargingStatus.FULL -> R.string.battery_full
            ChargingStatus.NOT_CHARGING -> R.string.battery_not_charging
        },
    )
    return levelPercent?.let { "$status · $it%" } ?: status
}
