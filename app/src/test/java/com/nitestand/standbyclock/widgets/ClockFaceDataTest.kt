package com.nitestand.standbyclock.widgets

import com.nitestand.standbyclock.clock.ClockFormatter
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.settings.StandBySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ClockFaceDataTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 11, 22, 42, 37, 0, kolkata)
    private val formatter = ClockFormatter(Locale.US, use24Hour = true, showSeconds = true)

    private fun build(settings: StandBySettings) = ClockFaceData.build(now, settings) { formatter }

    @Test
    fun `face capabilities gate the global options`() {
        val all = StandBySettings(showDate = true, showSeconds = true)
        assertFalse(build(all.copy(clockTheme = ClockTheme.DIGITAL_LARGE)).showDate)
        assertTrue(build(all.copy(clockTheme = ClockTheme.MINIMAL)).showDate)
        assertFalse(build(all.copy(clockTheme = ClockTheme.BUBBLE)).showSeconds)
        assertTrue(build(all.copy(clockTheme = ClockTheme.ANALOG)).showSeconds)
    }

    @Test
    fun `date style is applied centrally`() {
        val long = build(StandBySettings(clockTheme = ClockTheme.MINIMAL, dateStyle = DateStyle.LONG))
        val short = build(StandBySettings(clockTheme = ClockTheme.MINIMAL, dateStyle = DateStyle.SHORT))
        assertEquals("Wednesday, November 11", long.dateText)
        assertEquals("11 Nov", short.dateText)
    }

    @Test
    fun `world clock shows primary city and secondary zone`() {
        val data = build(StandBySettings(clockTheme = ClockTheme.WORLD, worldPrimaryCity = "hyderabad", worldSecondaryCity = "london"))
        assertEquals("Hyderabad", data.primaryPlace!!.label)
        assertEquals("22:42", data.text.hoursAndMinutes)
        val london = data.secondaryPlace!!
        assertEquals("London", london.label)
        assertEquals("17:12", london.text.hoursAndMinutes)
        assertEquals(-330, london.offsetMinutes)
        assertEquals(0, london.dayOffset)
    }

    @Test
    fun `world clock can show another city as the main time`() {
        val data = build(StandBySettings(clockTheme = ClockTheme.WORLD, worldPrimaryCity = "tokyo", worldSecondaryCity = "none"))
        assertEquals("Tokyo", data.primaryPlace!!.label)
        assertEquals("02:12", data.text.hoursAndMinutes)
        assertNull(data.secondaryPlace)
        assertEquals(12, data.time.dayOfMonth)
    }

    @Test
    fun `local world clock labels the phone zone`() {
        val data = build(StandBySettings(clockTheme = ClockTheme.WORLD, worldPrimaryCity = ""))
        assertEquals("Kolkata", data.primaryPlace!!.label)
    }

    @Test
    fun `selected colour reaches the face`() {
        val data = build(StandBySettings(clockTheme = ClockTheme.RETRO, faceColors = mapOf(ClockTheme.RETRO to ClockPalette.GREEN)))
        assertEquals(ClockPalette.GREEN, data.palette)
    }
}
