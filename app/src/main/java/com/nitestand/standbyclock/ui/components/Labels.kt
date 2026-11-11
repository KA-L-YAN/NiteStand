package com.nitestand.standbyclock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.FaceCategory
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.standby.ActivationMode

@Composable
fun ClockTheme.label(): String = stringResource(
    when (this) {
        ClockTheme.DIGITAL_LARGE -> R.string.face_digital_large
        ClockTheme.DIGITAL_COLOR -> R.string.face_digital_color
        ClockTheme.MINIMAL -> R.string.face_minimal
        ClockTheme.CLASSIC -> R.string.face_classic
        ClockTheme.WORLD -> R.string.face_world
        ClockTheme.ANALOG -> R.string.face_analog
        ClockTheme.ANALOG_MINIMAL -> R.string.face_analog_minimal
        ClockTheme.ANALOG_MODERN -> R.string.face_analog_modern
        ClockTheme.ANALOG_ELEGANT -> R.string.face_analog_elegant
        ClockTheme.ANALOG_LARGE -> R.string.face_analog_large
        ClockTheme.ANALOG_LINE -> R.string.face_analog_line
        ClockTheme.GEOMETRIC -> R.string.face_geometric
        ClockTheme.BUBBLE -> R.string.face_bubble
        ClockTheme.SERIF -> R.string.face_serif
        ClockTheme.LINE -> R.string.face_line
        ClockTheme.CALENDAR -> R.string.face_calendar
        ClockTheme.RETRO -> R.string.face_retro
        ClockTheme.AMBIENT -> R.string.face_ambient
    },
)

@Composable
fun ClockTheme.description(): String = stringResource(
    when (this) {
        ClockTheme.DIGITAL_LARGE -> R.string.face_digital_large_desc
        ClockTheme.DIGITAL_COLOR -> R.string.face_digital_color_desc
        ClockTheme.MINIMAL -> R.string.face_minimal_desc
        ClockTheme.CLASSIC -> R.string.face_classic_desc
        ClockTheme.WORLD -> R.string.face_world_desc
        ClockTheme.ANALOG -> R.string.face_analog_desc
        ClockTheme.ANALOG_MINIMAL -> R.string.face_analog_minimal_desc
        ClockTheme.ANALOG_MODERN -> R.string.face_analog_modern_desc
        ClockTheme.ANALOG_ELEGANT -> R.string.face_analog_elegant_desc
        ClockTheme.ANALOG_LARGE -> R.string.face_analog_large_desc
        ClockTheme.ANALOG_LINE -> R.string.face_analog_line_desc
        ClockTheme.GEOMETRIC -> R.string.face_geometric_desc
        ClockTheme.BUBBLE -> R.string.face_bubble_desc
        ClockTheme.SERIF -> R.string.face_serif_desc
        ClockTheme.LINE -> R.string.face_line_desc
        ClockTheme.CALENDAR -> R.string.face_calendar_desc
        ClockTheme.RETRO -> R.string.face_retro_desc
        ClockTheme.AMBIENT -> R.string.face_ambient_desc
    },
)

@Composable
fun FaceCategory.label(): String = stringResource(
    when (this) {
        FaceCategory.DIGITAL -> R.string.category_digital
        FaceCategory.ANALOG -> R.string.category_analog
        FaceCategory.DECORATIVE -> R.string.category_decorative
    },
)

@Composable
fun ClockPalette.label(): String = stringResource(
    when (this) {
        ClockPalette.WHITE -> R.string.palette_white
        ClockPalette.WARM_WHITE -> R.string.palette_warm_white
        ClockPalette.SOFT_ORANGE -> R.string.palette_orange
        ClockPalette.YELLOW -> R.string.palette_yellow
        ClockPalette.GREEN -> R.string.palette_green
        ClockPalette.BLUE -> R.string.palette_blue
        ClockPalette.PURPLE -> R.string.palette_purple
        ClockPalette.PINK -> R.string.palette_pink
        ClockPalette.RED -> R.string.palette_red
    },
)

@Composable
fun DateStyle.label(): String = stringResource(if (this == DateStyle.LONG) R.string.date_style_long else R.string.date_style_short)

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
        ActivationMode.SCREEN_SAVER -> R.string.mode_screen_saver
        ActivationMode.QUICK_SETTINGS -> R.string.mode_quick_settings
    },
)

@Composable
fun ActivationMode.description(): String = stringResource(
    when (this) {
        ActivationMode.CHARGING_AND_LANDSCAPE -> R.string.mode_charging_landscape_desc
        ActivationMode.CHARGING_ONLY -> R.string.mode_charging_only_desc
        ActivationMode.LANDSCAPE_ONLY -> R.string.mode_landscape_only_desc
        ActivationMode.MANUAL -> R.string.mode_manual_desc
        ActivationMode.SCREEN_SAVER -> R.string.mode_screen_saver_desc
        ActivationMode.QUICK_SETTINGS -> R.string.mode_quick_settings_desc
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
        BrightnessLevel.NIGHT -> R.string.brightness_night
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
