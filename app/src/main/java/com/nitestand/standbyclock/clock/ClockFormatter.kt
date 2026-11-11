package com.nitestand.standbyclock.clock

import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/** Pre-formatted pieces of the current time. */
data class ClockText(
    val hours: String,
    val minutes: String,
    val seconds: String?,
    /** Separator used by the locale between hours and minutes (":" for most, "." for some). */
    val separator: String,
    /** AM/PM marker in 12-hour mode, null in 24-hour mode. */
    val amPm: String?,
    /** Compact date such as "MON 14". */
    val shortDate: String,
    /** Long date such as "Tuesday, November 11" (locale-ordered on Android). */
    val longDate: String,
    /** Short date such as "11 Nov" / "Nov 11". */
    val mediumDate: String,
    /** Spoken form for TalkBack, e.g. "5:53 PM, Monday, November 14". */
    val accessibilityLabel: String,
) {
    /** "05:53" / "5:53" (without seconds and AM/PM). */
    val hoursAndMinutes: String get() = "$hours$separator$minutes"
}

/**
 * Locale-aware formatting for the StandBy clock. Uses java.time (available from API 26) so
 * it can be unit tested on the JVM.
 */
/** Date patterns; on Android these come from DateFormat.getBestDateTimePattern for the locale. */
data class DatePatterns(val long: String = "EEEE, MMMM d", val medium: String = "d MMM")

/**
 * Central time formatting for every clock face: 12/24-hour, seconds, AM/PM visibility and the
 * date styles are decided here once, never inside individual faces.
 */
class ClockFormatter(
    private val locale: Locale,
    private val use24Hour: Boolean,
    private val showSeconds: Boolean,
    private val showAmPm: Boolean = true,
    datePatterns: DatePatterns = DatePatterns(),
) {
    private val decimalStyle = DecimalStyle.of(locale)
    private fun pattern(p: String) = DateTimeFormatter.ofPattern(p, locale).withDecimalStyle(decimalStyle)

    private val hourFormatter = pattern(if (use24Hour) "HH" else "h")
    private val minuteFormatter = pattern("mm")
    private val secondFormatter = pattern("ss")
    private val amPmFormatter = pattern("a")
    private val shortDateFormatter = pattern("EEE d")
    private val spokenDateFormatter = pattern("EEEE, MMMM d")
    private val longDateFormatter = pattern(datePatterns.long)
    private val mediumDateFormatter = pattern(datePatterns.medium)
    private val separator = timeSeparator(locale)

    fun format(time: ZonedDateTime): ClockText {
        val hours = hourFormatter.format(time)
        val minutes = minuteFormatter.format(time)
        val marker = if (use24Hour) null else amPmFormatter.format(time)
        val amPm = marker?.takeIf { showAmPm }
        val spokenTime = buildString {
            append(hours).append(separator).append(minutes)
            // TalkBack always hears AM/PM in 12-hour mode, even when it is hidden visually.
            if (marker != null) append(' ').append(marker)
        }
        return ClockText(
            hours = hours,
            minutes = minutes,
            seconds = if (showSeconds) secondFormatter.format(time) else null,
            separator = separator,
            amPm = amPm,
            shortDate = shortDateFormatter.format(time).replace(".", "").uppercase(locale),
            longDate = longDateFormatter.format(time),
            mediumDate = mediumDateFormatter.format(time).replace(".", ""),
            accessibilityLabel = "$spokenTime, ${spokenDateFormatter.format(time)}",
        )
    }

    /** Formats an arbitrary time (e.g. an alarm) as "6:30 AM" / "06:30". */
    fun formatTime(time: ZonedDateTime): String {
        val base = hourFormatter.format(time) + separator + minuteFormatter.format(time)
        return if (use24Hour) base else "$base ${amPmFormatter.format(time)}"
    }

    companion object {
        /** Extracts the hour/minute separator from the locale's short time pattern. */
        fun timeSeparator(locale: Locale): String {
            val pattern = runCatching {
                DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
            }.getOrNull() ?: return ":"
            val hourIndex = pattern.indexOfFirst { it == 'H' || it == 'h' || it == 'k' || it == 'K' }
            val minuteIndex = pattern.indexOf('m')
            if (hourIndex < 0 || minuteIndex <= hourIndex) return ":"
            val between = pattern.substring(hourIndex, minuteIndex).trimStart('H', 'h', 'k', 'K').replace("'", "")
            return between.ifBlank { ":" }
        }
    }
}
