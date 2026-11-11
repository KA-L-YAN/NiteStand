package com.nitestand.standbyclock.sensors

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Turns gravity samples into a debounced [DeviceOrientation].
 *
 * Two mechanisms keep the result stable on a wobbly stand:
 * - Hysteresis: entering landscape needs |roll| within 90° ± tolerance, but once in landscape
 *   the phone stays there until it leaves 90° ± (tolerance + hysteresis). Flat detection
 *   uses separate enter/exit elevations the same way.
 * - Settling: a candidate orientation must persist for [OrientationConfig.settleMillis]
 *   before it replaces the reported one.
 *
 * Pure Kotlin; not thread-safe (use from the sensor callback thread only).
 */
class OrientationClassifier(private val config: OrientationConfig = OrientationConfig()) {

    var current: DeviceOrientation = DeviceOrientation.UNKNOWN
        private set

    private var candidate: DeviceOrientation? = null
    private var candidateSince: Long = 0L

    /**
     * @param x gravity along the device x axis (m/s²)
     * @param y gravity along the device y axis
     * @param z gravity along the device z axis
     * @param timestampMillis monotonic time of the sample
     * @return the (possibly unchanged) reported orientation
     */
    fun update(x: Float, y: Float, z: Float, timestampMillis: Long): DeviceOrientation {
        val raw = classifyRaw(x, y, z) ?: return current
        if (current == DeviceOrientation.UNKNOWN) {
            // First reliable reading: report immediately so the UI can react without delay.
            current = raw
            candidate = null
            return current
        }
        if (raw == current) {
            candidate = null
            return current
        }
        if (raw != candidate) {
            candidate = raw
            candidateSince = timestampMillis
        }
        if (timestampMillis - candidateSince >= config.settleMillis) {
            current = raw
            candidate = null
        }
        return current
    }

    fun reset() {
        current = DeviceOrientation.UNKNOWN
        candidate = null
    }

    /** Classifies one sample using hysteresis relative to [current]; null if the sample is unusable. */
    internal fun classifyRaw(x: Float, y: Float, z: Float): DeviceOrientation? {
        val magnitude = sqrt(x * x + y * y + z * z)
        // Ignore samples while the phone is being shaken or in free fall.
        if (magnitude < MIN_G || magnitude > MAX_G) return null

        val elevationDeg = Math.toDegrees(asin((abs(z) / magnitude).coerceIn(0f, 1f).toDouble())).toFloat()
        val flatThreshold = if (current == DeviceOrientation.FLAT) config.flatExitDeg else config.flatEnterDeg
        if (elevationDeg > flatThreshold) return DeviceOrientation.FLAT

        // Roll within the screen plane: 0° upright portrait, +90° right edge up, -90° left edge up.
        val rollDeg = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        val deviationFromLandscape = abs(abs(rollDeg) - 90f)
        val tolerance = if (current.isLandscape) {
            config.landscapeToleranceDeg + config.hysteresisDeg
        } else {
            config.landscapeToleranceDeg
        }
        return when {
            deviationFromLandscape <= tolerance ->
                if (rollDeg > 0f) DeviceOrientation.LANDSCAPE_LEFT else DeviceOrientation.LANDSCAPE_RIGHT
            else -> DeviceOrientation.PORTRAIT
        }
    }

    companion object {
        /** Roll (0° = upright portrait, ±90° = landscape) and elevation (0° = vertical, 90° = flat). */
        fun angles(x: Float, y: Float, z: Float): Pair<Float, Float> {
            val magnitude = sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001f)
            val roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
            val elevation = Math.toDegrees(asin((abs(z) / magnitude).coerceIn(0f, 1f).toDouble())).toFloat()
            return roll to elevation
        }

        private const val EARTH_G = 9.80665f
        private const val MIN_G = EARTH_G * 0.6f
        private const val MAX_G = EARTH_G * 1.4f
    }
}
