package com.nitestand.standbyclock.sensors

/**
 * Physical orientation of the phone, derived from gravity — independent of the UI's
 * rotation setting.
 *
 * Device axes (Android sensor coordinate system, natural portrait): +x points to the right
 * edge, +y to the top edge, +z out of the screen.
 */
enum class DeviceOrientation {
    /** Upright or upside-down portrait. */
    PORTRAIT,

    /** Landscape with the top edge of the phone pointing left (right edge up). */
    LANDSCAPE_LEFT,

    /** Landscape with the top edge of the phone pointing right (left edge up). */
    LANDSCAPE_RIGHT,

    /** Lying (nearly) flat on a table, face up or face down. */
    FLAT,

    /** No reading yet. */
    UNKNOWN,

    /** The device has no accelerometer/gravity sensor; orientation cannot be checked. */
    UNAVAILABLE;

    val isLandscape: Boolean get() = this == LANDSCAPE_LEFT || this == LANDSCAPE_RIGHT
}

/** How tolerant landscape detection is. Exposed in settings as "Orientation sensitivity". */
enum class OrientationSensitivity(val config: OrientationConfig) {
    STRICT(OrientationConfig(landscapeToleranceDeg = 20f)),
    NORMAL(OrientationConfig(landscapeToleranceDeg = 30f)),
    RELAXED(OrientationConfig(landscapeToleranceDeg = 40f)),
}

data class OrientationConfig(
    /** Max deviation of the roll angle from exactly ±90° that still enters landscape. */
    val landscapeToleranceDeg: Float = 30f,
    /** Extra deviation tolerated before leaving landscape (prevents flicker). */
    val hysteresisDeg: Float = 12f,
    /** Screen-plane elevation above which the phone is considered flat. */
    val flatEnterDeg: Float = 65f,
    /** Elevation below which a flat phone is considered upright again. */
    val flatExitDeg: Float = 55f,
    /** A new orientation must be observed continuously this long before it is reported. */
    val settleMillis: Long = 700,
)
