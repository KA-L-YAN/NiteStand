package com.nitestand.standbyclock.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme

/**
 * v1 → v2 (clock-face gallery):
 * - the old default face CLASSIC becomes the new default StandBy face DIGITAL_LARGE
 *   (any other face the user picked is kept);
 * - the StandBy screen becomes clock-only (side panel off; it can be re-enabled in Settings);
 * - a non-white v1 accent colour carries over as the DIGITAL_LARGE colour.
 */
object SettingsMigrationV2 : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[SettingsRepository.SCHEMA_VERSION] ?: 1) < 2

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val theme = prefs[SettingsRepository.CLOCK_THEME]
        if (theme == null || theme == ClockTheme.CLASSIC.name) {
            prefs[SettingsRepository.CLOCK_THEME] = ClockTheme.DIGITAL_LARGE.name
        }
        prefs[SettingsRepository.SHOW_PANEL] = false
        val carriedColor = when (prefs[SettingsRepository.ACCENT]) {
            AccentColor.WARM.name -> ClockPalette.WARM_WHITE
            AccentColor.RED.name -> ClockPalette.RED
            AccentColor.BLUE.name -> ClockPalette.BLUE
            AccentColor.MINT.name -> ClockPalette.GREEN
            else -> null
        }
        if (carriedColor != null) {
            prefs[SettingsRepository.faceColorKey(ClockTheme.DIGITAL_LARGE)] = carriedColor.name
        }
        prefs[SettingsRepository.SCHEMA_VERSION] = 2
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
