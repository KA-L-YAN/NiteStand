package com.nitestand.standbyclock.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ClockFormatterTest {

    // Monday 14 September 2026, 17:53:07
    private val evening = ZonedDateTime.of(2026, 9, 14, 17, 53, 7, 0, ZoneId.of("UTC"))
    private val morning = ZonedDateTime.of(2026, 9, 14, 5, 3, 0, 0, ZoneId.of("UTC"))

    @Test
    fun `24 hour format pads hours`() {
        val text = ClockFormatter(Locale.UK, use24Hour = true, showSeconds = false).format(morning)
        assertEquals("05:03", text.hoursAndMinutes)
        assertNull(text.amPm)
        assertNull(text.seconds)
    }

    @Test
    fun `12 hour format has marker and no leading zero`() {
        val text = ClockFormatter(Locale.US, use24Hour = false, showSeconds = false).format(evening)
        assertEquals("5:53", text.hoursAndMinutes)
        assertEquals("PM", text.amPm)
    }

    @Test
    fun `seconds only when enabled`() {
        assertEquals("07", ClockFormatter(Locale.US, true, showSeconds = true).format(evening).seconds)
    }

    @Test
    fun `short date is upper case weekday and day`() {
        assertEquals("MON 14", ClockFormatter(Locale.US, true, false).format(evening).shortDate)
    }

    @Test
    fun `short date is localised`() {
        val german = ClockFormatter(Locale.GERMANY, true, false).format(evening).shortDate
        assertEquals("MO 14", german)
    }

    @Test
    fun `accessibility label contains spoken time and full date`() {
        val label = ClockFormatter(Locale.US, false, false).format(evening).accessibilityLabel
        assertTrue(label, label.startsWith("5:53 PM"))
        assertTrue(label, label.contains("Monday"))
        assertTrue(label, label.contains("September 14"))
    }

    @Test
    fun `formatTime for alarms`() {
        assertEquals("6:30 AM", ClockFormatter(Locale.US, false, false).formatTime(morning.withHour(6).withMinute(30)))
        assertEquals("06:30", ClockFormatter(Locale.UK, true, false).formatTime(morning.withHour(6).withMinute(30)))
    }

    @Test
    fun `time separator follows locale`() {
        assertEquals(":", ClockFormatter.timeSeparator(Locale.US))
        assertEquals(":", ClockFormatter.timeSeparator(Locale.GERMANY))
        assertEquals(".", ClockFormatter.timeSeparator(Locale.forLanguageTag("fi-FI")))
    }

    @Test
    fun `midnight and noon in 12 hour mode`() {
        val f = ClockFormatter(Locale.US, false, false)
        assertEquals("12:00", f.format(morning.withHour(0).withMinute(0)).hoursAndMinutes)
        assertEquals("12:00", f.format(morning.withHour(12).withMinute(0)).hoursAndMinutes)
    }

    @Test
    fun `theme cycling wraps around`() {
        assertEquals(ClockTheme.MINIMAL, ClockTheme.CLASSIC.cycle(1))
        assertEquals(ClockTheme.AMBIENT, ClockTheme.CLASSIC.cycle(-1))
        assertEquals(ClockTheme.CLASSIC, ClockTheme.AMBIENT.cycle(1))
    }
}
