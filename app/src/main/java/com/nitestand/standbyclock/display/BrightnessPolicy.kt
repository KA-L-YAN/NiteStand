package com.nitestand.standbyclock.display

import android.view.WindowManager

/** StandBy brightness choice. Applied to the StandBy window only, never to system settings. */
enum class BrightnessLevel { AUTO, LOW, MEDIUM, HIGH, NIGHT }

/**
 * Maps the brightness setting to a per-window brightness override
 * (WindowManager.LayoutParams.screenBrightness). A window override needs no permission,
 * applies only while the StandBy window is visible and is released automatically when the
 * window goes away — the user's system brightness is never modified.
 */
object BrightnessPolicy {

    /** Lowest value that keeps the backlight on across devices (0 can mean "off" on some panels). */
    const val NIGHT_BRIGHTNESS = 0.01f

    fun windowBrightness(level: BrightnessLevel, nightActive: Boolean): Float = when {
        nightActive -> NIGHT_BRIGHTNESS
        else -> when (level) {
            BrightnessLevel.AUTO -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            BrightnessLevel.LOW -> 0.08f
            BrightnessLevel.MEDIUM -> 0.35f
            BrightnessLevel.HIGH -> 0.85f
            BrightnessLevel.NIGHT -> NIGHT_BRIGHTNESS
        }
    }
}
