package com.nitestand.standbyclock.lockscreen

import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import com.nitestand.standbyclock.StandByClockApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the 2.1.0/2.2.0 launch failure on a OnePlus 12R: the full-screen launch
 * notification must not be in a group that suppresses its alerts, or SystemUI will never launch it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = StandByClockApp::class)
class LaunchNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<StandByClockApp>()

    @Test
    fun `launch notification can be shown full screen`() {
        StandByNotifications.createChannels(context)
        val n = StandByNotifications.buildFullScreenLaunch(context)
        assertNotNull("full-screen intent attached", n.fullScreenIntent)
        assertNull("no group (AndroidX setSilent puts it in group silent)", n.group)
        assertEquals(Notification.GROUP_ALERT_ALL, n.groupAlertBehavior)
        assertFalse(StandByNotifications.groupSuppressesAlerts(n))
        assertEquals(StandByNotifications.CHANNEL_LAUNCH, n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
    }

    @Test
    fun `detector recognises the suppressing configuration`() {
        val silenced = androidx.core.app.NotificationCompat.Builder(context, StandByNotifications.CHANNEL_LAUNCH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setSilent(true)
            .build()
        assertEquals("silent", silenced.group)
        assert(StandByNotifications.groupSuppressesAlerts(silenced))
    }

    @Test
    fun `launch channel is high importance and quiet`() {
        StandByNotifications.createChannels(context)
        val channel = context.getSystemService(android.app.NotificationManager::class.java)
            .getNotificationChannel(StandByNotifications.CHANNEL_LAUNCH)
        assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }
}
