package com.nitestand.standbyclock.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationClassifierTest {

    private val g = 9.81f

    /** Gravity for a phone rolled [rollDeg] in the screen plane and leaned back [tiltDeg]. */
    private fun gravity(rollDeg: Double, tiltDeg: Double = 15.0): Triple<Float, Float, Float> {
        val r = Math.toRadians(rollDeg)
        val t = Math.toRadians(tiltDeg)
        val inPlane = g * cos(t)
        return Triple((inPlane * sin(r)).toFloat(), (inPlane * cos(r)).toFloat(), (g * sin(t)).toFloat())
    }

    private fun OrientationClassifier.feed(rollDeg: Double, at: Long, tiltDeg: Double = 15.0): DeviceOrientation {
        val (x, y, z) = gravity(rollDeg, tiltDeg)
        return update(x, y, z, at)
    }

    @Test
    fun `upright phone is portrait`() {
        assertEquals(DeviceOrientation.PORTRAIT, OrientationClassifier().feed(0.0, 0))
    }

    @Test
    fun `both landscape directions are detected`() {
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, OrientationClassifier().feed(90.0, 0))
        assertEquals(DeviceOrientation.LANDSCAPE_RIGHT, OrientationClassifier().feed(-90.0, 0))
    }

    @Test
    fun `flat phone is FLAT`() {
        assertEquals(DeviceOrientation.FLAT, OrientationClassifier().feed(90.0, 0, tiltDeg = 80.0))
    }

    @Test
    fun `portrait to landscape requires settle time`() {
        val c = OrientationClassifier(OrientationConfig(settleMillis = 500))
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(0.0, 0))
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(90.0, 100))
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(90.0, 400))
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(90.0, 650))
    }

    @Test
    fun `landscape to portrait transition`() {
        val c = OrientationClassifier(OrientationConfig(settleMillis = 300))
        c.feed(-90.0, 0)
        assertEquals(DeviceOrientation.LANDSCAPE_RIGHT, c.current)
        c.feed(-5.0, 1_000)
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(-5.0, 1_400))
    }

    @Test
    fun `hysteresis keeps landscape when slightly unstable`() {
        val config = OrientationConfig(landscapeToleranceDeg = 30f, hysteresisDeg = 12f, settleMillis = 0)
        val c = OrientationClassifier(config)
        c.feed(90.0, 0)
        // 52° deviates 38° from landscape: outside the entry tolerance (30°) but inside 30 + 12.
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(52.0, 10))
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(128.0, 20))
        // Beyond tolerance + hysteresis it leaves landscape.
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(40.0, 30))
        // And from portrait the same 52° is not enough to re-enter.
        assertEquals(DeviceOrientation.PORTRAIT, c.feed(52.0, 40))
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(62.0, 50))
    }

    @Test
    fun `brief wobble does not change the reported orientation`() {
        val c = OrientationClassifier(OrientationConfig(settleMillis = 700))
        c.feed(90.0, 0)
        c.feed(0.0, 100) // knocked for 200 ms
        c.feed(0.0, 300)
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(90.0, 400))
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.feed(90.0, 2_000))
    }

    @Test
    fun `rotating 180 degrees switches landscape direction`() {
        val c = OrientationClassifier(OrientationConfig(settleMillis = 200))
        c.feed(90.0, 0)
        c.feed(-90.0, 100)
        assertEquals(DeviceOrientation.LANDSCAPE_RIGHT, c.feed(-90.0, 400))
    }

    @Test
    fun `shaking samples are ignored`() {
        val c = OrientationClassifier()
        c.feed(90.0, 0)
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.update(0f, 40f, 0f, 1_000))
        assertEquals(DeviceOrientation.LANDSCAPE_LEFT, c.update(0f, 0.5f, 0f, 2_000))
    }

    @Test
    fun `stricter sensitivity rejects tilted landscape`() {
        val strict = OrientationClassifier(OrientationSensitivity.STRICT.config)
        val relaxed = OrientationClassifier(OrientationSensitivity.RELAXED.config)
        assertEquals(DeviceOrientation.PORTRAIT, strict.feed(55.0, 0))
        assertTrue(relaxed.feed(55.0, 0).isLandscape)
    }
}
