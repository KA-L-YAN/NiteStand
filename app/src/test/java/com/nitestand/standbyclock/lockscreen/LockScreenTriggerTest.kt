package com.nitestand.standbyclock.lockscreen

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.ActivationMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenTriggerTest {

    private val awakeLockedLandscape = WatcherInputs(
        standByEnabled = true,
        watcherEnabled = true,
        mode = ActivationMode.LANDSCAPE_ONLY,
        screenOn = true,
        keyguardLocked = true,
        charging = ChargingStatus.NOT_CHARGING,
        orientation = DeviceOrientation.LANDSCAPE_LEFT,
        standByShowing = false,
    )

    @Test
    fun `landscape only fires without a charger`() {
        assertTrue(LockScreenTrigger().update(awakeLockedLandscape))
    }

    @Test
    fun `fires once, not repeatedly`() {
        val t = LockScreenTrigger()
        assertTrue(t.update(awakeLockedLandscape))
        assertFalse(t.update(awakeLockedLandscape))
        assertFalse(t.update(awakeLockedLandscape.copy(orientation = DeviceOrientation.LANDSCAPE_RIGHT)))
    }

    @Test
    fun `portrait to landscape on the lock screen fires`() {
        val t = LockScreenTrigger()
        assertFalse(t.update(awakeLockedLandscape.copy(orientation = DeviceOrientation.PORTRAIT)))
        assertTrue(t.update(awakeLockedLandscape))
    }

    @Test
    fun `exiting standby does not re-launch until picked up`() {
        val t = LockScreenTrigger()
        assertTrue(t.update(awakeLockedLandscape))
        // StandBy is showing; the watcher's sensor is paused, so orientation is unknown.
        assertFalse(t.update(awakeLockedLandscape.copy(standByShowing = true, orientation = DeviceOrientation.UNKNOWN)))
        // User double-taps out; still locked, awake and horizontal.
        assertFalse(t.update(awakeLockedLandscape))
        // Picked up into portrait and put back → fires again.
        assertFalse(t.update(awakeLockedLandscape.copy(orientation = DeviceOrientation.PORTRAIT)))
        assertTrue(t.update(awakeLockedLandscape))
    }

    @Test
    fun `screen off re-arms`() {
        val t = LockScreenTrigger()
        assertTrue(t.update(awakeLockedLandscape))
        assertFalse(t.update(awakeLockedLandscape.copy(screenOn = false)))
        assertTrue(t.update(awakeLockedLandscape))
    }

    @Test
    fun `never fires when unlocked or screen off`() {
        assertFalse(LockScreenTrigger().update(awakeLockedLandscape.copy(keyguardLocked = false)))
        assertFalse(LockScreenTrigger().update(awakeLockedLandscape.copy(screenOn = false)))
    }

    @Test
    fun `charging modes need power`() {
        val inputs = awakeLockedLandscape.copy(mode = ActivationMode.CHARGING_AND_LANDSCAPE)
        val t = LockScreenTrigger()
        assertFalse(t.update(inputs))
        assertTrue(t.update(inputs.copy(charging = ChargingStatus.CHARGING)))
        val chargingOnly = LockScreenTrigger()
        assertTrue(chargingOnly.update(inputs.copy(mode = ActivationMode.CHARGING_ONLY, orientation = DeviceOrientation.PORTRAIT, charging = ChargingStatus.FULL)))
    }

    @Test
    fun `non automatic modes and disabled settings never fire`() {
        for (mode in listOf(ActivationMode.MANUAL, ActivationMode.SCREEN_SAVER, ActivationMode.QUICK_SETTINGS)) {
            assertFalse(mode.name, LockScreenTrigger().update(awakeLockedLandscape.copy(mode = mode)))
        }
        assertFalse(LockScreenTrigger().update(awakeLockedLandscape.copy(watcherEnabled = false)))
        assertFalse(LockScreenTrigger().update(awakeLockedLandscape.copy(standByEnabled = false)))
    }

    @Test
    fun `already showing standby is not launched again`() {
        assertFalse(LockScreenTrigger().update(awakeLockedLandscape.copy(standByShowing = true)))
    }

    @Test
    fun `sensor is only needed while awake on the lock screen`() {
        assertTrue(awakeLockedLandscape.needsOrientationSensor)
        assertFalse(awakeLockedLandscape.copy(screenOn = false).needsOrientationSensor)
        assertFalse(awakeLockedLandscape.copy(keyguardLocked = false).needsOrientationSensor)
        assertFalse(awakeLockedLandscape.copy(standByShowing = true).needsOrientationSensor)
        assertFalse(awakeLockedLandscape.copy(mode = ActivationMode.CHARGING_ONLY).needsOrientationSensor)
    }
}
