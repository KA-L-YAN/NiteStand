package com.nitestand.standbyclock.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.testutil.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `defaults when nothing stored`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        assertEquals(StandBySettings(), repo.current())
    }

    @Test
    fun `update persists every field`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        val custom = StandBySettings(
            standByEnabled = false,
            autoStart = false,
            showOnLockScreen = false,
            activationMode = ActivationMode.QUICK_SETTINGS,
            lockScreenWatcher = true,
            batteryTimeoutMinutes = 15,
            clockTheme = ClockTheme.RETRO,
            hourFormat = HourFormat.TWENTY_FOUR_HOUR,
            showSeconds = true,
            showDate = false,
            showAmPm = false,
            dateStyle = com.nitestand.standbyclock.clock.DateStyle.SHORT,
            faceColors = mapOf(ClockTheme.RETRO to com.nitestand.standbyclock.clock.ClockPalette.GREEN, ClockTheme.DIGITAL_LARGE to com.nitestand.standbyclock.clock.ClockPalette.PINK),
            worldPrimaryCity = "tokyo",
            worldSecondaryCity = "none",
            showPanel = true,
            showDateWidget = false,
            showBatteryWidget = false,
            showAlarmWidget = false,
            showWeatherWidget = true,
            brightness = BrightnessLevel.HIGH,
            nightMode = NightMode.ON,
            nightStartMinutes = 21 * 60,
            nightEndMinutes = 6 * 60 + 30,
            accent = AccentColor.MINT,
            reduceMotion = true,
            burnInProtection = false,
            burnInIntervalSeconds = 120,
        )
        repo.update { custom }
        assertEquals(custom, repo.current())
    }

    @Test
    fun `transform sees current values`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        repo.update { it.copy(clockTheme = it.clockTheme.cycle(1)) }
        repo.update { it.copy(clockTheme = it.clockTheme.cycle(1)) }
        assertEquals(ClockTheme.MINIMAL, repo.current().clockTheme)
    }

    @Test
    fun `out of range values are sanitised`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        repo.update { it.copy(nightStartMinutes = 5_000, burnInIntervalSeconds = 1) }
        val s = repo.current()
        assertEquals(24 * 60 - 1, s.nightStartMinutes)
        assertEquals(15, s.burnInIntervalSeconds)
    }

    @Test
    fun `unknown enum names fall back to defaults`() = runTest {
        val store = InMemoryDataStore(mutablePreferencesOf(stringPreferencesKey("clock_theme") to "HOLOGRAM"))
        assertEquals(ClockTheme.DIGITAL_LARGE, SettingsRepository(store).current().clockTheme)
    }

    @Test
    fun `defaults are the StandBy face on a clock-only screen`() = runTest {
        val s = SettingsRepository(InMemoryDataStore()).current()
        assertEquals(ClockTheme.DIGITAL_LARGE, s.clockTheme)
        assertFalse(s.showPanel)
        assertEquals(emptyMap<ClockTheme, com.nitestand.standbyclock.clock.ClockPalette>(), s.faceColors)
    }

    @Test
    fun `clearing a face colour removes it`() = runTest {
        val repo = SettingsRepository(InMemoryDataStore())
        repo.update { it.copy(faceColors = mapOf(ClockTheme.SERIF to com.nitestand.standbyclock.clock.ClockPalette.RED)) }
        repo.update { it.copy(faceColors = emptyMap()) }
        assertEquals(emptyMap<ClockTheme, com.nitestand.standbyclock.clock.ClockPalette>(), repo.current().faceColors)
    }

    @Test
    fun `settings survive a new DataStore instance on disk`() {
        val file = tmp.root.resolve("standby_settings.preferences_pb")
        runBlocking {
            val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            SettingsRepository(PreferenceDataStoreFactory.create(scope = scope1) { file })
                .update { it.copy(clockTheme = ClockTheme.ANALOG, autoStart = false) }
            scope1.cancel()

            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reloaded = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file }).settings.first()
            scope2.cancel()
            assertEquals(ClockTheme.ANALOG, reloaded.clockTheme)
            assertFalse(reloaded.autoStart)
        }
    }
}
