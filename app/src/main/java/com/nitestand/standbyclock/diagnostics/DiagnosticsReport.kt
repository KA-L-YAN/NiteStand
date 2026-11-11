package com.nitestand.standbyclock.diagnostics

import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.sensors.OrientationReading
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.StandByState
import com.nitestand.standbyclock.standby.StandBySurface
import java.util.Locale

data class DiagnosticsSection(val title: String, val rows: List<Pair<String, String>>)

/**
 * Builds the diagnostics shown on screen and copied to the clipboard. Kept as plain English text
 * (not localised) so a copied report reads the same for whoever helps debug it.
 */
object DiagnosticsReport {

    fun sections(
        snapshot: DiagnosticsSnapshot,
        battery: BatterySnapshot?,
        orientation: OrientationReading?,
        showing: StandBySurface?,
        lastState: StandByState?,
        watcherRunning: Boolean,
        settings: StandBySettings,
        versionName: String,
    ): List<DiagnosticsSection> = listOf(
        DiagnosticsSection(
            "Device",
            listOf(
                "Android" to "${snapshot.androidRelease} (API ${snapshot.sdkInt})",
                "Manufacturer" to "${snapshot.manufacturer} / ${snapshot.brand}",
                "Model" to "${snapshot.model} (${snapshot.device})",
                "Build" to snapshot.buildDisplay,
                "NiteStand version" to versionName,
            ),
        ),
        DiagnosticsSection(
            "Screen saver (Android Dream)",
            listOf(
                "Settings page present" to snapshot.screenSaverSettingsPage.text(),
                "Screen saver turned on" to snapshot.screenSaverEnabled.text(),
                "StandBy Clock selected" to snapshot.screenSaverIsStandBy.text(),
                "Starts while charging" to snapshot.screenSaverOnCharge.text(),
                "Starts while docked" to snapshot.screenSaverOnDock.text(),
            ),
        ),
        DiagnosticsSection(
            "Lock screen",
            listOf(
                "Keyguard locked now" to snapshot.keyguardLocked.yesNo(),
                "Secure lock (PIN/pattern/biometric)" to snapshot.deviceSecure.yesNo(),
                "Screen interactive" to snapshot.interactive.yesNo(),
                "Can show over lock screen" to if (snapshot.showWhenLockedApi) "Yes (setShowWhenLocked)" else "Yes (legacy window flag)",
                "Can turn screen on when launched" to if (snapshot.turnScreenOnApi) "Yes (setTurnScreenOn)" else "Yes (legacy window flag)",
                "Notifications allowed" to snapshot.notificationsAllowed.yesNo(),
                "Full-screen intents allowed" to snapshot.fullScreenIntentAllowed.yesNo(),
                "Battery optimisation exempt" to snapshot.ignoringBatteryOptimizations.yesNo(),
                "System screen timeout" to (snapshot.screenTimeoutSeconds?.let { "$it s" } ?: "Unknown"),
            ),
        ),
        DiagnosticsSection(
            "Live state",
            listOf(
                "Charging" to (battery?.let { "${it.status} · ${it.levelPercent ?: "?"}% · ${it.source}" } ?: "Unknown"),
                "Physical orientation" to (
                    orientation?.let {
                        String.format(Locale.US, "%s (roll %.0f°, tilt %.0f°)", it.orientation, it.rollDeg, it.elevationDeg)
                    } ?: "Measuring…"
                    ),
                "Orientation sensor" to (orientation?.sensorName ?: "—"),
                "StandBy showing" to (showing?.name ?: "No"),
                "StandBy state (last)" to (lastState?.name ?: "Not running"),
                "Lock-screen watcher running" to watcherRunning.yesNo(),
            ),
        ),
        DiagnosticsSection(
            "Settings",
            listOf(
                "Enabled" to settings.standByEnabled.yesNo(),
                "Activation mode" to settings.activationMode.name,
                "Lock-screen auto start" to settings.lockScreenWatcher.yesNo(),
                "Auto start while open" to settings.autoStart.yesNo(),
                "Show on lock screen" to settings.showOnLockScreen.yesNo(),
                "Battery timeout" to if (settings.batteryTimeoutMinutes == 0) "Until exit" else "${settings.batteryTimeoutMinutes} min",
                "Orientation sensitivity" to settings.orientationSensitivity.name,
            ),
        ),
    )

    fun format(sections: List<DiagnosticsSection>, events: List<String>): String = buildString {
        appendLine("NiteStand diagnostics")
        sections.forEach { section ->
            appendLine()
            appendLine("[${section.title}]")
            section.rows.forEach { (k, v) -> appendLine("$k: $v") }
        }
        appendLine()
        appendLine("[Recent events]")
        if (events.isEmpty()) appendLine("(none)") else events.forEach(::appendLine)
    }

    private fun Boolean.yesNo() = if (this) "Yes" else "No"

    private fun Answer.text() = when (this) {
        Answer.YES -> "Yes"
        Answer.NO -> "No"
        Answer.UNKNOWN -> "Unknown (not readable by apps)"
    }
}
