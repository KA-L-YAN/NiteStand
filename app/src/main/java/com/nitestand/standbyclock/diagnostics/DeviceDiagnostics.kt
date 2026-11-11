package com.nitestand.standbyclock.diagnostics

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.nitestand.standbyclock.lockscreen.StandByNotifications
import com.nitestand.standbyclock.standby.StandByDreamService

/** A tri-state answer: some settings are not readable by apps on every Android version / OEM. */
enum class Answer { YES, NO, UNKNOWN }

data class DiagnosticsSnapshot(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val buildDisplay: String,
    val screenSaverSettingsPage: Answer,
    val screenSaverEnabled: Answer,
    val screenSaverIsStandBy: Answer,
    val screenSaverOnCharge: Answer,
    val screenSaverOnDock: Answer,
    val keyguardLocked: Boolean,
    val deviceSecure: Boolean,
    val interactive: Boolean,
    val showWhenLockedApi: Boolean,
    val turnScreenOnApi: Boolean,
    val notificationsAllowed: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val screenTimeoutSeconds: Int?,
)

/**
 * Reads the device facts that decide what StandBy can do. Values that Android does not expose to
 * apps are reported as [Answer.UNKNOWN] rather than guessed.
 */
object DeviceDiagnostics {

    fun snapshot(context: Context): DiagnosticsSnapshot {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val dreamComponent = ComponentName(context, StandByDreamService::class.java)
        val components = secureString(context, "screensaver_components")
        return DiagnosticsSnapshot(
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            buildDisplay = Build.DISPLAY,
            screenSaverSettingsPage = answer(resolves(context, Intent(Settings.ACTION_DREAM_SETTINGS))),
            screenSaverEnabled = secureInt(context, "screensaver_enabled"),
            screenSaverIsStandBy = when (components) {
                null -> Answer.UNKNOWN
                else -> answer(components.split(',').any { ComponentName.unflattenFromString(it) == dreamComponent })
            },
            screenSaverOnCharge = secureInt(context, "screensaver_activate_on_sleep"),
            screenSaverOnDock = secureInt(context, "screensaver_activate_on_dock"),
            keyguardLocked = keyguard?.isKeyguardLocked == true,
            deviceSecure = keyguard?.isDeviceSecure == true,
            interactive = power?.isInteractive == true,
            showWhenLockedApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            turnScreenOnApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            notificationsAllowed = StandByNotifications.canPostNotifications(context),
            fullScreenIntentAllowed = StandByNotifications.canUseFullScreenIntent(context),
            ignoringBatteryOptimizations = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            screenTimeoutSeconds = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000
            }.getOrNull(),
        )
    }

    private fun answer(value: Boolean) = if (value) Answer.YES else Answer.NO

    private fun resolves(context: Context, intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    /** Hidden Settings.Secure keys: readable on many builds, blocked (SecurityException) on others. */
    private fun secureString(context: Context, key: String): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()

    private fun secureInt(context: Context, key: String): Answer =
        when (runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()?.trim()) {
            "1" -> Answer.YES
            "0" -> Answer.NO
            else -> Answer.UNKNOWN
        }
}
