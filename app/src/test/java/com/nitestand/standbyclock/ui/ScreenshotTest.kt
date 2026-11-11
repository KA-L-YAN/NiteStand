package com.nitestand.standbyclock.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.nitestand.standbyclock.StandByClockApp
import com.nitestand.standbyclock.alarm.NextAlarm
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.charging.PowerSource
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.ClockTicker
import com.nitestand.standbyclock.clock.TimeFormatPreference
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.standby.ActivationSnapshot
import com.nitestand.standbyclock.standby.StandByState
import com.nitestand.standbyclock.standby.StandByUiState
import com.nitestand.standbyclock.ui.settings.SettingsScreen
import com.nitestand.standbyclock.ui.setup.SetupScreen
import com.nitestand.standbyclock.ui.standby.StandByScreen
import com.nitestand.standbyclock.ui.standby.WaitingContent
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import com.nitestand.standbyclock.weather.LocalPlaceholderWeatherProvider
import com.nitestand.standbyclock.widgets.WidgetDataSources
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders the real Compose UI on the JVM (Robolectric native graphics) and writes PNGs to
 * app/build/screenshots so layouts can be reviewed without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = StandByClockApp::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<StandByClockApp>()
    private val fixedTime = ZonedDateTime.of(2026, 9, 14, 5, 53, 7, 0, ZoneId.systemDefault())

    private val sources = WidgetDataSources(
        battery = flowOf(BatterySnapshot(85, ChargingStatus.CHARGING, PowerSource.AC)),
        nextAlarm = flowOf(NextAlarm(fixedTime.plusMinutes(37).plusDays(1).toInstant())),
        weather = LocalPlaceholderWeatherProvider(),
        ticker = ClockTicker(null) { fixedTime.toInstant().toEpochMilli() },
        timeFormat = TimeFormatPreference(context),
    )

    private fun activation(state: StandByState, landscape: Boolean = true) = ActivationSnapshot(
        state = state,
        mode = ActivationMode.CHARGING_AND_LANDSCAPE,
        enabled = true,
        powerRequirementMet = true,
        orientationRequirementMet = landscape,
        charging = ChargingStatus.CHARGING,
        orientation = if (landscape) DeviceOrientation.LANDSCAPE_LEFT else DeviceOrientation.PORTRAIT,
        forced = false,
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { StandByClockTheme { content() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun standBy(name: String, settings: StandBySettings, night: Boolean = false) = capture(name) {
        val state = StandByUiState(activation(StandByState.ACTIVE), settings, night)
        StandByScreen(state, sources, {}, {}, systemReduceMotion = true) {}
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun classic() = standBy("standby_classic", StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWENTY_FOUR_HOUR))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun classicWithWeatherAnd12h() = standBy(
        "standby_classic_12h_weather",
        StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWELVE_HOUR, showWeatherWidget = true, showSeconds = true),
    )

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun minimalNoPanel() = standBy("standby_minimal_nopanel", StandBySettings(clockTheme = ClockTheme.MINIMAL, showPanel = false))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun retro() = standBy("standby_retro", StandBySettings(clockTheme = ClockTheme.RETRO))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun retroNoPanelSeconds() = standBy("standby_retro_full", StandBySettings(clockTheme = ClockTheme.RETRO, showPanel = false, showSeconds = true))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun analog() = standBy("standby_analog", StandBySettings(clockTheme = ClockTheme.ANALOG, showSeconds = true))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun ambient() = standBy("standby_ambient", StandBySettings(clockTheme = ClockTheme.AMBIENT))

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun night() = standBy("standby_night", StandBySettings(), night = true)

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun waiting() = capture("standby_waiting") {
        val state = StandByUiState(activation(StandByState.WAITING_FOR_LANDSCAPE, landscape = false), StandBySettings(), false)
        StandByScreen(state, sources, {}, {}, true) { WaitingContent(state, sources, {}, {}) }
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-port-xhdpi")
    fun setup() = capture("setup") {
        SetupScreen(StandBySettings(), BatterySnapshot(85, ChargingStatus.CHARGING, PowerSource.AC), sources, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-port-xhdpi")
    fun settings() = capture("settings") {
        SettingsScreen(StandBySettings(), hasOrientationSensor = true, onUpdate = {}, onBack = {}, onOpenScreenSaverSettings = {})
    }

    @Test
    @Config(qualifiers = "w400dp-h1400dp-port-xhdpi")
    fun diagnostics() = capture("diagnostics") {
        val snapshot = com.nitestand.standbyclock.diagnostics.DeviceDiagnostics.snapshot(context)
        val sections = com.nitestand.standbyclock.diagnostics.DiagnosticsReport.sections(
            snapshot,
            BatterySnapshot(64, ChargingStatus.NOT_CHARGING, PowerSource.NONE),
            com.nitestand.standbyclock.sensors.OrientationReading(DeviceOrientation.LANDSCAPE_LEFT, 88f, 12f, "Gravity sensor"),
            null,
            StandByState.WAITING_FOR_LANDSCAPE,
            watcherRunning = true,
            settings = StandBySettings(activationMode = ActivationMode.LANDSCAPE_ONLY, lockScreenWatcher = true),
            versionName = "1.1.0",
        )
        com.nitestand.standbyclock.ui.diagnostics.DiagnosticsScreen(
            sections,
            listOf("22:41:03  Watcher: SCREEN_ON → screenOn=true locked=true", "22:41:04  Watcher: conditions met → launching StandBy"),
            com.nitestand.standbyclock.ui.diagnostics.DiagnosticsActions({}, {}, {}, {}, {}, {}),
        )
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-port-xhdpi")
    fun settingsActivation() = capture("settings_activation") {
        SettingsScreen(
            StandBySettings(activationMode = ActivationMode.LANDSCAPE_ONLY, lockScreenWatcher = true),
            hasOrientationSensor = true, onUpdate = {}, onBack = {}, onOpenScreenSaverSettings = {},
        )
    }
}
