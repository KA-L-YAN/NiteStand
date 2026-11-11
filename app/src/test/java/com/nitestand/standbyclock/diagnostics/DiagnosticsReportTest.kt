package com.nitestand.standbyclock.diagnostics

import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.charging.PowerSource
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.sensors.OrientationReading
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.ActivationMode
import com.nitestand.standbyclock.standby.StandByState
import com.nitestand.standbyclock.standby.StandBySurface
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val snapshot = DiagnosticsSnapshot(
        androidRelease = "16", sdkInt = 36, manufacturer = "OnePlus", brand = "OnePlus", model = "CPH2611",
        device = "OP5D3BL1", buildDisplay = "CPH2611_16.0.0.100", screenSaverSettingsPage = Answer.NO,
        screenSaverEnabled = Answer.UNKNOWN, screenSaverIsStandBy = Answer.UNKNOWN, screenSaverOnCharge = Answer.UNKNOWN,
        screenSaverOnDock = Answer.UNKNOWN, keyguardLocked = false, deviceSecure = true, interactive = true,
        showWhenLockedApi = true, turnScreenOnApi = true, notificationsAllowed = true, fullScreenIntentAllowed = false,
        ignoringBatteryOptimizations = false, screenTimeoutSeconds = 30,
    )

    @Test
    fun `report contains device, live state and events`() {
        val sections = DiagnosticsReport.sections(
            snapshot,
            BatterySnapshot(64, ChargingStatus.NOT_CHARGING, PowerSource.NONE),
            OrientationReading(DeviceOrientation.LANDSCAPE_LEFT, 88f, 12f, "Gravity"),
            StandBySurface.ACTIVITY,
            StandByState.ACTIVE,
            watcherRunning = true,
            settings = StandBySettings(activationMode = ActivationMode.LANDSCAPE_ONLY, lockScreenWatcher = true),
            versionName = "1.1.0",
        )
        val text = DiagnosticsReport.format(sections, listOf("12:00:00  Watcher service started"))
        for (expected in listOf(
            "Android: 16 (API 36)", "Model: CPH2611", "Settings page present: No",
            "Screen saver turned on: Unknown", "Full-screen intents allowed: No",
            "Charging: NOT_CHARGING · 64% · NONE", "LANDSCAPE_LEFT (roll 88°, tilt 12°)",
            "StandBy state (last): ACTIVE", "Activation mode: LANDSCAPE_ONLY", "Watcher service started",
        )) {
            assertTrue("missing '$expected' in\n$text", text.contains(expected))
        }
    }
}
