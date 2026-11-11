package com.nitestand.standbyclock.lockscreen

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nitestand.standbyclock.MainActivity
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.standby.StandByActivity

/**
 * Notifications used only by the opt-in lock-screen watcher:
 * - a silent ongoing notification required for the foreground service, and
 * - a full-screen-intent notification that the system turns into a StandBy launch while the
 *   phone is locked. A full-screen intent is Android's sanctioned way for an app in the
 *   background to show an activity over the lock screen (alarm/call style). If the user has not
 *   allowed full-screen intents (Android 14+), the system shows a heads-up notification instead,
 *   which the user can tap.
 */
object StandByNotifications {
    const val CHANNEL_SERVICE = "watcher"
    const val CHANNEL_LAUNCH = "launch"
    const val SERVICE_NOTIFICATION_ID = 1
    const val LAUNCH_NOTIFICATION_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.channel_watcher), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.channel_watcher_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LAUNCH, context.getString(R.string.channel_launch), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_launch_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun serviceNotification(context: Context): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_tile_standby)
            .setContentTitle(context.getString(R.string.watcher_notification_title))
            .setContentText(context.getString(R.string.watcher_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreenIntent(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            true
        }

    /** Asks the system to show StandBy over the lock screen. */
    fun launchStandBy(context: Context) {
        val intent = StandByActivity.intent(context, force = false, fromLockScreen = true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, 11, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!canPostNotifications(context)) {
            EventLog.log("Watcher: cannot launch — notification permission denied")
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_tile_standby)
            .setContentTitle(context.getString(R.string.launch_notification_title))
            .setContentText(context.getString(R.string.launch_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(LAUNCH_NOTIFICATION_ID, notification)
            EventLog.log(
                "Watcher: launch posted (full-screen intent ${if (canUseFullScreenIntent(context)) "allowed" else "NOT allowed → heads-up only"})",
            )
        } catch (e: SecurityException) {
            EventLog.log("Watcher: launch failed: ${e.message}")
        }
    }

    fun cancelLaunch(context: Context) {
        NotificationManagerCompat.from(context).cancel(LAUNCH_NOTIFICATION_ID)
    }
}
