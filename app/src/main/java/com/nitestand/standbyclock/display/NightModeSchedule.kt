package com.nitestand.standbyclock.display

import java.time.LocalTime

enum class NightMode { OFF, ON, SCHEDULED }

object NightModeSchedule {

    /**
     * @param startMinutes minutes after midnight when night mode begins
     * @param endMinutes minutes after midnight when it ends; may be earlier than start (crosses midnight)
     */
    fun isNight(mode: NightMode, startMinutes: Int, endMinutes: Int, time: LocalTime): Boolean = when (mode) {
        NightMode.OFF -> false
        NightMode.ON -> true
        NightMode.SCHEDULED -> {
            val now = time.hour * 60 + time.minute
            when {
                startMinutes == endMinutes -> false
                startMinutes < endMinutes -> now in startMinutes until endMinutes
                else -> now >= startMinutes || now < endMinutes
            }
        }
    }
}
