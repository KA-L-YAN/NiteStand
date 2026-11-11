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
    private val fixedTime = ZonedDateTime.of(2026, 11, 11, 22, 42, 37, 0, ZoneId.systemDefault())

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

    private val content = androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
    private var contentSet = false

    private fun capture(name: String, body: @Composable () -> Unit) {
        content.value = body
        if (!contentSet) {
            compose.setContent { StandByClockTheme { content.value?.invoke() } }
            contentSet = true
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun standBy(name: String, settings: StandBySettings, night: Boolean = false) = capture(name) {
        val state = StandByUiState(activation(StandByState.ACTIVE), settings.copy(burnInProtection = false), night)
        // key() gives every capture a fresh composition (no gesture hint carried over).
        androidx.compose.runtime.key(name) {
            StandByScreen(state, sources, {}, {}, systemReduceMotion = true) {}
        }
    }

    /** OnePlus 12R landscape: 2780 × 1264 px at ~450 dpi ≈ 988 × 449 dp. */
    private fun renderAllFaces(prefix: String, base: StandBySettings, night: Boolean) {
        for (face in ClockTheme.entries) {
            standBy("${prefix}_${face.name.lowercase()}", base.copy(clockTheme = face), night)
        }
    }

    @Test
    @Config(qualifiers = "w988dp-h449dp-land-xhdpi")
    fun allFacesDay24h() = renderAllFaces(
        "face_day",
        StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWENTY_FOUR_HOUR, worldPrimaryCity = "hyderabad"),
        night = false,
    )

    @Test
    @Config(qualifiers = "w988dp-h449dp-land-xhdpi")
    fun allFacesNight() = renderAllFaces(
        "face_night",
        StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWENTY_FOUR_HOUR, worldPrimaryCity = "hyderabad"),
        night = true,
    )

    @Test
    @Config(qualifiers = "w988dp-h449dp-land-xhdpi")
    fun twelveHourWithSeconds() {
        val s = StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWELVE_HOUR, showSeconds = true)
        for (face in listOf(ClockTheme.DIGITAL_LARGE, ClockTheme.DIGITAL_COLOR, ClockTheme.MINIMAL, ClockTheme.SERIF, ClockTheme.ANALOG, ClockTheme.ANALOG_ELEGANT, ClockTheme.LINE)) {
            standBy("face_12h_${face.name.lowercase()}", s.copy(clockTheme = face))
        }
        standBy("face_12h_noampm_digital_large", s.copy(clockTheme = ClockTheme.DIGITAL_LARGE, showAmPm = false, showSeconds = false))
    }

    @Test
    @Config(qualifiers = "w988dp-h449dp-land-xhdpi")
    fun colorsAndPanel() {
        val s = StandBySettings(hourFormat = com.nitestand.standbyclock.clock.HourFormat.TWENTY_FOUR_HOUR)
        standBy("color_large_orange", s.copy(faceColors = mapOf(ClockTheme.DIGITAL_LARGE to com.nitestand.standbyclock.clock.ClockPalette.SOFT_ORANGE)))
        standBy("color_gradient_blue", s.copy(clockTheme = ClockTheme.DIGITAL_COLOR, faceColors = mapOf(ClockTheme.DIGITAL_COLOR to com.nitestand.standbyclock.clock.ClockPalette.BLUE)))
        standBy("color_bubble_green", s.copy(clockTheme = ClockTheme.BUBBLE, faceColors = mapOf(ClockTheme.BUBBLE to com.nitestand.standbyclock.clock.ClockPalette.GREEN)))
        standBy("panel_large", s.copy(showPanel = true))
    }

    @Test
    @Config(qualifiers = "w449dp-h988dp-port-xhdpi")
    fun portraitFaces() {
        for (face in listOf(ClockTheme.DIGITAL_LARGE, ClockTheme.ANALOG, ClockTheme.CALENDAR, ClockTheme.WORLD)) {
            standBy("portrait_${face.name.lowercase()}", StandBySettings(clockTheme = face))
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h1800dp-port-xhdpi")
    fun gallery() = capture("gallery") {
        com.nitestand.standbyclock.ui.gallery.ClockGalleryScreen(
            settings = StandBySettings(clockTheme = ClockTheme.DIGITAL_LARGE),
            sources = sources,
            onUpdate = {},
            onBack = {},
            onPreviewFullScreen = {},
        )
    }

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
