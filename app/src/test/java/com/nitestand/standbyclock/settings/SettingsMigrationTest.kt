package com.nitestand.standbyclock.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {

    private val theme = stringPreferencesKey("clock_theme")
    private val panel = booleanPreferencesKey("show_panel")
    private val accent = stringPreferencesKey("accent")
    private val schema = intPreferencesKey("schema_version")

    @Test
    fun `v1 default face becomes the StandBy face and panel turns off`() = runTest {
        val v1 = mutablePreferencesOf(theme to "CLASSIC", panel to true, accent to "BLUE")
        assertTrue(SettingsMigrationV2.shouldMigrate(v1))
        val migrated = SettingsMigrationV2.migrate(v1)
        val s = SettingsRepository.read(migrated)
        assertEquals(ClockTheme.DIGITAL_LARGE, s.clockTheme)
        assertFalse(s.showPanel)
        assertEquals(ClockPalette.BLUE, s.faceColors[ClockTheme.DIGITAL_LARGE])
        assertFalse(SettingsMigrationV2.shouldMigrate(migrated))
    }

    @Test
    fun `a face the user picked in v1 is kept`() = runTest {
        val migrated = SettingsMigrationV2.migrate(mutablePreferencesOf(theme to "RETRO", accent to "WHITE"))
        val s = SettingsRepository.read(migrated)
        assertEquals(ClockTheme.RETRO, s.clockTheme)
        assertEquals(emptyMap<ClockTheme, ClockPalette>(), s.faceColors)
    }

    @Test
    fun `fresh install migrates to defaults and is not migrated twice`() = runTest {
        assertTrue(SettingsMigrationV2.shouldMigrate(emptyPreferences()))
        val migrated = SettingsMigrationV2.migrate(emptyPreferences())
        assertEquals(2, migrated[schema])
        assertEquals(ClockTheme.DIGITAL_LARGE, SettingsRepository.read(migrated).clockTheme)
    }

    @Test
    fun `writing settings records the current schema`() = runTest {
        val prefs = emptyPreferences().toMutablePreferences()
        SettingsRepository.write(prefs, StandBySettings())
        assertFalse(SettingsMigrationV2.shouldMigrate(prefs))
    }
}
