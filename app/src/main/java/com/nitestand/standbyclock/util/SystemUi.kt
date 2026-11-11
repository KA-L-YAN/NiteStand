package com.nitestand.standbyclock.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** True when the user disabled animations system-wide (Developer options / Accessibility). */
fun Context.isSystemReduceMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Opens the system screen-saver (Dream) settings. Some OEM builds hide that page, so fall back
 * to Display settings and finally to the Settings home page.
 */
fun Context.openScreenSaverSettings(): Boolean {
    val candidates = listOf(
        Intent(Settings.ACTION_DREAM_SETTINGS),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return intent.action == Settings.ACTION_DREAM_SETTINGS
        } catch (_: ActivityNotFoundException) {
            // try next
        } catch (_: SecurityException) {
            // try next
        }
    }
    return false
}
