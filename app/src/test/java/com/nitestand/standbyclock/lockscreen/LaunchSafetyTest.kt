package com.nitestand.standbyclock.lockscreen

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.ActivationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSafetyTest {

    @Test
    fun `StandBy arriving confirms the launch`() {
        assertEquals(LaunchVerdict.CONFIRMED, LaunchWatch.verdict(true, 300, true, 500, 4_000))
    }

    @Test
    fun `StandBy's own wake is not mistaken for the user`() {
        // Screen just turned on (by our launch), activity not resumed yet.
        assertEquals(LaunchVerdict.WAITING, LaunchWatch.verdict(false, 400, true, 900, 4_000))
    }

    @Test
    fun `the user waking or unlocking the phone cancels the launch`() {
        assertEquals(LaunchVerdict.USER_WOKE_PHONE, LaunchWatch.verdict(false, 1_300, true, 2_000, 4_000))
        assertEquals(LaunchVerdict.USER_WOKE_PHONE, LaunchWatch.verdict(false, 100, false, 600, 4_000))
    }

    @Test
    fun `screen off and nothing arriving times out`() {
        assertEquals(LaunchVerdict.WAITING, LaunchWatch.verdict(false, null, true, 3_900, 4_000))
        assertEquals(LaunchVerdict.TIMED_OUT, LaunchWatch.verdict(false, null, true, 4_000, 4_000))
    }

    @Test
    fun `a still phone on a stand counts after the hold time`() {
        val d = StillnessDetector(holdMs = 1_500, maxWobbleDeg = 2f)
        var t = 0L
        repeat(8) { // samples at 0 … 1400 ms
            assertFalse(d.add(t, true, 88f + (it % 2) * 0.3f, 20f))
            t += 200
        }
        assertTrue(d.add(t, true, 88.2f, 20.1f)) // t = 1600 ms: 1.6 s still
    }

    @Test
    fun `a phone held sideways in a hand never counts`() {
        val d = StillnessDetector(holdMs = 1_500, maxWobbleDeg = 2f)
        val roll = listOf(84f, 88f, 91f, 86f, 89f, 85f, 92f, 87f, 90f, 84f, 88f, 93f, 86f, 90f, 85f)
        roll.forEachIndexed { i, r -> assertFalse(d.add(i * 200L, true, r, 25f + (i % 3))) }
    }

    @Test
    fun `leaving landscape resets the hold`() {
        val d = StillnessDetector(holdMs = 1_000, maxWobbleDeg = 2f)
        d.add(0, true, 90f, 20f)
        d.add(600, true, 90f, 20f)
        assertFalse(d.add(800, false, 0f, 20f))
        assertFalse(d.add(1_000, true, 90f, 20f))
        assertTrue(d.add(2_100, true, 90.5f, 20f))
    }

    @Test
    fun `a cancelled launch stays armed for the next real lock`() {
        val t = ActivationTrigger()
        val locked = WatcherInputs(true, true, ActivationMode.LANDSCAPE_ONLY, false, true, ChargingStatus.NOT_CHARGING, DeviceOrientation.LANDSCAPE_LEFT, false)
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(locked))
        t.onLaunchRequested()
        t.onLaunchCancelled()
        assertTrue(t.armed)
        assertEquals(ActivationPhase.IDLE, t.phase)
        assertEquals(ActivationSource.SCREEN_OFF, t.onScreenOff(locked))
    }
}
