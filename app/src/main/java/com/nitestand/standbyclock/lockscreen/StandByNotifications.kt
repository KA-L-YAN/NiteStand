package com.nitestand.standbyclock.lockscreen

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nitestand.standbyclock.MainActivity
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.standby.StandByActivity

/**
 * Notifications used only by the opt-in lock-screen watcher:
 * - a silent ongoing notification required for the foreground service,
 * - a full-screen-intent notification: the mechanism by which Android itself starts StandBy over
 *   the lock screen (alarm-clock style) — the user normally never sees it, and
 * - a quiet fallback that explains *why* when Android declined the automatic start.
 */
object StandByNotifications {
    const val CHANNEL_SERVICE = "watcher"

    /**
     * Full-screen launch channel (HIGH importance is required for full-screen intents). Versioned:
     * a channel's importance cannot be raised by the app once the user or the OEM has lowered it,
     * so v2 starts fresh; the v1 "launch" channel is deleted.
     */
    const val CHANNEL_LAUNCH = "standby_launch_v2"
    private const val CHANNEL_LAUNCH_V1 = "launch"

    /** Quiet channel for the fallback explanation (only when an automatic start was blocked). */
    const val CHANNEL_FALLBACK = "standby_fallback"
    const val SERVICE_NOTIFICATION_ID = 1
    const val LAUNCH_NOTIFICATION_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(CHANNEL_LAUNCH_V1)
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
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FALLBACK, context.getString(R.string.channel_fallback), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_fallback_description)
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

    /** The intent that opens StandBy for an automatic activation. */
    fun launchIntent(context: Context): Intent =
        StandByActivity.intent(context, force = false, fromLockScreen = true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    /**
     * PendingIntent for the full-screen intent. On Android 14+ the creator explicitly allows the
     * background activity start it was created for (the system sends it on our behalf).
     */
    private fun launchPendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else {
                @Suppress("DEPRECATION")
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            options.setPendingIntentCreatorBackgroundActivityStartMode(mode)
            PendingIntent.getActivity(context, 11, launchIntent(context), flags, options.toBundle())
        } else {
            PendingIntent.getActivity(context, 11, launchIntent(context), flags)
        }
    }

    /**
     * Posts the full-screen-intent notification. When Android honours it (screen off, or lock
     * screen showing, and the app has full-screen access) the system starts StandBy immediately and
     * the notification is removed by the activity; its text is only a placeholder.
     * @return true if the notification was posted
     */
    fun postFullScreenLaunch(context: Context): Boolean {
        if (!canPostNotifications(context)) return false
        val pending = launchPendingIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_tile_standby)
            .setContentTitle(context.getString(R.string.launch_notification_title))
            .setContentText(context.getString(R.string.launch_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(LAUNCH_NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            EventLog.log("Launch notification rejected: ${e.message}")
            false
        }
    }

    /**
     * Replaces the launch notification with a quiet explanation when StandBy could not be opened
     * automatically. Posted at most once per activation (the state machine guarantees this).
     */
    fun postFallback(context: Context, reason: LaunchIssue?): Boolean {
        if (!canPostNotifications(context)) return false
        val open = PendingIntent.getActivity(
            context, 12, launchIntent(context).putExtra(StandByActivity.EXTRA_FROM_FALLBACK, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(
            when (reason) {
                LaunchIssue.FULL_SCREEN_DENIED -> R.string.fallback_reason_fsi
                LaunchIssue.CHANNEL_DEMOTED -> R.string.fallback_reason_channel
                LaunchIssue.DO_NOT_DISTURB -> R.string.fallback_reason_dnd
                LaunchIssue.NOTIFICATIONS_OFF -> R.string.fallback_reason_notifications
                else -> R.string.fallback_reason_unknown
            },
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_tile_standby)
            .setContentTitle(context.getString(R.string.fallback_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
        if (reason == LaunchIssue.FULL_SCREEN_DENIED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val settings = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.addAction(
                0,
                context.getString(R.string.fallback_action_allow),
                PendingIntent.getActivity(context, 13, settings, PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return try {
            NotificationManagerCompat.from(context).notify(LAUNCH_NOTIFICATION_ID, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancelLaunch(context: Context) {
        NotificationManagerCompat.from(context).cancel(LAUNCH_NOTIFICATION_ID)
    }

    fun launchChannelImportance(context: Context): Int? =
        context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_LAUNCH)?.importance
}
