package com.nitestand.standbyclock.lockscreen

import android.app.NotificationManager
import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.ActivationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationTriggerTest {

    /** Unlocked phone in use, horizontal, no charger, Landscape-only mode. */
    private val inUse = WatcherInputs(
        standByEnabled = true,
        watcherEnabled = true,
        mode = ActivationMode.LANDSCAPE_ONLY,
        screenOn = true,
        keyguardLocked = false,
        charging = ChargingStatus.NOT_CHARGING,
        orientation = DeviceOrientation.UNKNOWN,
        standByShowing = false,
    )
    private val lockedLandscape = inUse.copy(screenOn = false, keyguardLocked = true, orientation = DeviceOrientation.LANDSCAPE_LEFT)

    @Test
    fun `scenario A and B - locking the phone in landscape activates via screen off`() {
        val t = ActivationTrigger()
        t.update(inUse)
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
        assertEquals(ActivationPhase.ACTIVATION_DETECTED, t.phase)
        assertFalse(t.armed)
    }

    @Test
    fun `locking in portrait does not activate, and re-arms`() {
        val t = ActivationTrigger()
        assertNull(t.onScreenOff(lockedLandscape.copy(orientation = DeviceOrientation.PORTRAIT)))
        assertTrue(t.armed)
    }

    @Test
    fun `lock first, then put it on the stand - readings during the screen-off window activate`() {
        // OnePlus 12R trace: locked while held in portrait, then placed horizontally.
        val t = ActivationTrigger()
        assertNull(t.onScreenOff(lockedLandscape.copy(orientation = DeviceOrientation.PORTRAIT)))
        assertNull(t.onScreenOff(lockedLandscape.copy(orientation = DeviceOrientation.FLAT)))
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
    }

    @Test
    fun `scenario C - the awake lock screen never launches and does not consume the activation`() {
        // OxygenOS 16 turns a full-screen intent on the awake lock screen into a notification.
        val t = ActivationTrigger()
        t.update(lockedLandscape.copy(screenOn = true))
        assertTrue(t.armed)
        assertEquals(ActivationPhase.IDLE, t.phase)
        // When that lock screen times out (screen off), StandBy starts.
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
    }

    @Test
    fun `unknown orientation never activates`() {
        val t = ActivationTrigger()
        assertNull(t.onScreenOff(lockedLandscape.copy(orientation = DeviceOrientation.UNKNOWN)))
        assertTrue(t.armed)
    }

    @Test
    fun `no loop - StandBy battery timeout or power button turning the screen off does not relaunch`() {
        val t = ActivationTrigger()
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
        t.onLaunchRequested()
        t.update(lockedLandscape.copy(screenOn = true, standByShowing = true, orientation = DeviceOrientation.UNKNOWN))
        assertEquals(ActivationPhase.STANDBY_ACTIVE, t.phase)
        assertNull(t.onScreenOff(lockedLandscape.copy(standByShowing = true)))
        assertNull(t.onScreenOff(lockedLandscape))
        t.update(lockedLandscape.copy(screenOn = true))
        assertNull(t.onScreenOff(lockedLandscape))
    }

    @Test
    fun `no loop - exiting StandBy does not relaunch until the phone is picked up`() {
        val t = ActivationTrigger()
        t.onScreenOff(lockedLandscape)
        t.update(lockedLandscape.copy(screenOn = true, standByShowing = true))
        t.update(lockedLandscape.copy(screenOn = true))
        assertNull(t.onScreenOff(lockedLandscape))
        // Picked up: portrait seen on the awake lock screen, or during a screen-off window.
        t.update(lockedLandscape.copy(screenOn = true, orientation = DeviceOrientation.PORTRAIT))
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
    }

    @Test
    fun `no notification spam - a failed launch stays failed until re-armed`() {
        val t = ActivationTrigger()
        t.onScreenOff(lockedLandscape)
        t.onLaunchRequested()
        t.onLaunchFailed()
        t.onFallbackPosted()
        assertEquals(ActivationPhase.FALLBACK_NOTIFICATION, t.phase)
        repeat(5) {
            t.update(lockedLandscape.copy(screenOn = true))
            assertNull(t.onScreenOff(lockedLandscape))
        }
    }

    @Test
    fun `unlocking re-arms for the next lock`() {
        val t = ActivationTrigger()
        t.onScreenOff(lockedLandscape)
        t.onUnlocked()
        assertTrue(t.armed)
        assertEquals(ActivationPhase.IDLE, t.phase)
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(lockedLandscape))
    }

    @Test
    fun `charging modes need power, and unplugging re-arms`() {
        val t = ActivationTrigger()
        val charging = lockedLandscape.copy(mode = ActivationMode.CHARGING_AND_LANDSCAPE)
        assertNull(t.onScreenOff(charging))
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(charging.copy(charging = ChargingStatus.CHARGING)))
        assertNull(t.onScreenOff(charging.copy(charging = ChargingStatus.CHARGING)))
        t.update(charging.copy(screenOn = true))
        assertTrue(t.armed)
    }

    @Test
    fun `charging only mode needs no orientation`() {
        val t = ActivationTrigger()
        val chargingOnly = lockedLandscape.copy(mode = ActivationMode.CHARGING_ONLY, charging = ChargingStatus.FULL, orientation = DeviceOrientation.UNKNOWN)
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(chargingOnly))
    }

    @Test
    fun `manual modes and disabled settings never activate`() {
        for (mode in listOf(ActivationMode.MANUAL, ActivationMode.SCREEN_SAVER, ActivationMode.QUICK_SETTINGS)) {
            assertNull(mode.name, ActivationTrigger().onScreenOff(lockedLandscape.copy(mode = mode)))
        }
        assertNull(ActivationTrigger().onScreenOff(lockedLandscape.copy(watcherEnabled = false)))
    }

    @Test
    fun `readiness classifies blocking and advisory issues`() {
        val ok = LaunchReadiness(true, true, NotificationManager.IMPORTANCE_HIGH, false, true)
        assertTrue(ok.canUseFullScreenLaunch)
        assertTrue(ok.issues.isEmpty())
        val noFsi = ok.copy(fullScreenAllowed = false, batteryUnrestricted = false)
        assertEquals(LaunchIssue.FULL_SCREEN_DENIED, noFsi.blockingIssue)
        assertEquals(listOf(LaunchIssue.FULL_SCREEN_DENIED, LaunchIssue.BATTERY_RESTRICTED), noFsi.issues)
        assertEquals(LaunchIssue.CHANNEL_DEMOTED, ok.copy(channelImportance = NotificationManager.IMPORTANCE_DEFAULT).blockingIssue)
        assertEquals(LaunchIssue.NOTIFICATIONS_OFF, ok.copy(notificationsAllowed = false).blockingIssue)
        assertNull(ok.copy(doNotDisturb = true).blockingIssue)
        assertTrue(ok.copy(channelImportance = null).canUseFullScreenLaunch)
    }
}
