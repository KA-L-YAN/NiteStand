package com.nitestand.standbyclock.clock

/**
 * Clock faces, in gallery and swipe order. Persisted by name: the original v1 names (CLASSIC,
 * MINIMAL, RETRO, ANALOG, AMBIENT) are kept so existing settings stay valid.
 */
enum class ClockTheme {
    // Digital
    DIGITAL_LARGE, DIGITAL_COLOR, MINIMAL, CLASSIC, WORLD,

    // Analog
    ANALOG, ANALOG_MINIMAL, ANALOG_MODERN, ANALOG_ELEGANT,

    // Expressive
    BUBBLE, SERIF, LINE, CALENDAR, RETRO, AMBIENT;

    fun cycle(delta: Int): ClockTheme {
        val all = entries
        return all[Math.floorMod(ordinal + delta, all.size)]
    }

    companion object {
        val DEFAULT = DIGITAL_LARGE
    }
}

enum class HourFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/** How faces that show a date print it. */
enum class DateStyle { LONG, SHORT }
