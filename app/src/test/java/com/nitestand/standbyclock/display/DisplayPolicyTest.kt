package com.nitestand.standbyclock.display

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import kotlin.math.abs
import kotlin.random.Random

class DisplayPolicyTest {

    @Test
    fun `auto brightness leaves the system in control`() {
        assertEquals(
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
            BrightnessPolicy.windowBrightness(BrightnessLevel.AUTO, nightActive = false),
            0f,
        )
    }

    @Test
    fun `brightness levels are ordered and night is the dimmest`() {
        val low = BrightnessPolicy.windowBrightness(BrightnessLevel.LOW, false)
        val medium = BrightnessPolicy.windowBrightness(BrightnessLevel.MEDIUM, false)
        val high = BrightnessPolicy.windowBrightness(BrightnessLevel.HIGH, false)
        assertTrue(low < medium && medium < high && high <= 1f)
        for (level in BrightnessLevel.entries) {
            assertEquals(BrightnessPolicy.NIGHT_BRIGHTNESS, BrightnessPolicy.windowBrightness(level, true), 0f)
        }
        assertTrue(BrightnessPolicy.NIGHT_BRIGHTNESS in 0.001f..low)
    }

    @Test
    fun `night schedule crossing midnight`() {
        val start = 22 * 60
        val end = 7 * 60
        fun night(h: Int, m: Int = 0) = NightModeSchedule.isNight(NightMode.SCHEDULED, start, end, LocalTime.of(h, m))
        assertTrue(night(22))
        assertTrue(night(23, 59))
        assertTrue(night(3))
        assertTrue(night(6, 59))
        assertFalse(night(7))
        assertFalse(night(12))
        assertFalse(night(21, 59))
    }

    @Test
    fun `night schedule within a day and edge cases`() {
        assertTrue(NightModeSchedule.isNight(NightMode.SCHEDULED, 60, 300, LocalTime.of(2, 0)))
        assertFalse(NightModeSchedule.isNight(NightMode.SCHEDULED, 60, 300, LocalTime.of(6, 0)))
        assertFalse(NightModeSchedule.isNight(NightMode.SCHEDULED, 60, 60, LocalTime.of(1, 0)))
        assertTrue(NightModeSchedule.isNight(NightMode.ON, 0, 0, LocalTime.NOON))
        assertFalse(NightModeSchedule.isNight(NightMode.OFF, 0, 1439, LocalTime.of(3, 0)))
    }

    @Test
    fun `burn-in shifter always moves, slowly and within bounds`() {
        val shifter = BurnInShifter(smallRadiusDp = 6, largeRadiusDp = 14, maxStepDp = 3, random = Random(42))
        var previous = shifter.current
        repeat(2_000) {
            val next = shifter.next()
            assertNotEquals("must move every interval", previous, next)
            assertTrue("x within bounds: $next", abs(next.xDp) <= 14)
            assertTrue("y within bounds: $next", abs(next.yDp) <= 14)
            assertTrue("x step small", abs(next.xDp - previous.xDp) <= 3)
            assertTrue("y step small", abs(next.yDp - previous.yDp) <= 3)
            previous = next
        }
    }

    @Test
    fun `burn-in shifter occasionally visits farther positions`() {
        val shifter = BurnInShifter(smallRadiusDp = 6, largeRadiusDp = 14, maxStepDp = 3, repositionEvery = 5, random = Random(7))
        val positions = List(1_000) { shifter.next() }
        assertTrue(positions.any { abs(it.xDp) > 6 || abs(it.yDp) > 6 })
    }
}
