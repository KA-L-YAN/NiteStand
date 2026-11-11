package com.nitestand.standbyclock.lockscreen

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager

/** Something that stops (or may stop) Android from opening StandBy automatically. */
enum class LaunchIssue(val blocking: Boolean) {
    /** POST_NOTIFICATIONS denied: no launch notification can be posted at all. */
    NOTIFICATIONS_OFF(true),

    /** Android 14+ "Full-screen notifications" special access denied: Android strips the launch. */
    FULL_SCREEN_DENIED(true),

    /** The launch channel was lowered below "high" (user or OEM): no full-screen launch. */
    CHANNEL_DEMOTED(true),

    /** Do Not Disturb may suppress full-screen launches. */
    DO_NOT_DISTURB(false),

    /** Battery optimisation may stop the watcher in the background (OxygenOS does this). */
    BATTERY_RESTRICTED(false),
}

/** Snapshot of the launch prerequisites. [issues] is pure and unit tested. */
data class LaunchReadiness(
    val notificationsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
    /** Importance of the launch channel, or null if it does not exist yet (it is created HIGH). */
    val channelImportance: Int?,
    val doNotDisturb: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val issues: List<LaunchIssue>
        get() = buildList {
            if (!notificationsAllowed) add(LaunchIssue.NOTIFICATIONS_OFF)
            if (!fullScreenAllowed) add(LaunchIssue.FULL_SCREEN_DENIED)
            if (channelImportance != null && channelImportance < NotificationManager.IMPORTANCE_HIGH) add(LaunchIssue.CHANNEL_DEMOTED)
            if (doNotDisturb) add(LaunchIssue.DO_NOT_DISTURB)
            if (!batteryUnrestricted) add(LaunchIssue.BATTERY_RESTRICTED)
        }

    val blockingIssue: LaunchIssue? get() = issues.firstOrNull { it.blocking }

    val canUseFullScreenLaunch: Boolean get() = blockingIssue == null

    companion object {
        fun read(context: Context): LaunchReadiness {
            val nm = context.getSystemService(NotificationManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            return LaunchReadiness(
                notificationsAllowed = StandByNotifications.canPostNotifications(context) && nm?.areNotificationsEnabled() != false,
                fullScreenAllowed = StandByNotifications.canUseFullScreenIntent(context),
                channelImportance = nm?.getNotificationChannel(StandByNotifications.CHANNEL_LAUNCH)?.importance,
                doNotDisturb = nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL,
                batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            )
        }
    }
}
