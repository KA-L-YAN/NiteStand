package com.nitestand.standbyclock.clock

/** Available clock designs, in swipe order. */
enum class ClockTheme {
    CLASSIC, MINIMAL, RETRO, ANALOG, AMBIENT;

    fun cycle(delta: Int): ClockTheme {
        val all = entries
        return all[Math.floorMod(ordinal + delta, all.size)]
    }
}

enum class HourFormat { SYSTEM, TWELVE_HOUR, TWENTY_FOUR_HOUR }
