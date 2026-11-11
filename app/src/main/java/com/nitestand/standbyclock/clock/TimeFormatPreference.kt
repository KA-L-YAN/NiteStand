package com.nitestand.standbyclock.clock

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/** Resolves the effective 12/24-hour choice, honouring the system setting when asked to. */
class TimeFormatPreference(private val context: Context) {

    fun is24Hour(format: HourFormat): Boolean = when (format) {
        HourFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        HourFormat.TWELVE_HOUR -> false
        HourFormat.TWENTY_FOUR_HOUR -> true
    }

    fun locale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    fun datePatterns(): DatePatterns {
        val locale = locale()
        return DatePatterns(
            long = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"),
            medium = DateFormat.getBestDateTimePattern(locale, "dMMM"),
        )
    }

    fun formatter(format: HourFormat, showSeconds: Boolean, showAmPm: Boolean = true): ClockFormatter =
        ClockFormatter(locale(), is24Hour(format), showSeconds, showAmPm, datePatterns())
}
