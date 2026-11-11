package com.nitestand.standbyclock.ui

import androidx.compose.ui.graphics.Color
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.ui.theme.StandByStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandByStyleTest {

    private fun Color.luma() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    @Test
    fun `day uses the palette colour on black`() {
        val style = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.BLUE, 0f, false)
        assertEquals(Color(ClockPalette.BLUE.primary), style.primary)
        assertEquals(Color.Black, style.background)
        assertFalse(style.night)
    }

    @Test
    fun `night is dim red with no white elements, for every face and colour`() {
        for (theme in ClockTheme.entries) {
            for (palette in ClockPalette.entries) {
                val night = StandByStyle.create(theme, palette, 1f, false)
                assertTrue(night.night)
                for (c in listOf(night.primary, night.secondary, night.accent, night.hands) + night.gradient) {
                    assertTrue("$theme/$palette not red: $c", c.red > c.green * 2 && c.red > c.blue * 2)
                }
                assertEquals(Color.Black, night.background)
            }
        }
    }

    @Test
    fun `night transition is gradual`() {
        val day = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.WHITE, 0f, false)
        val half = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.WHITE, 0.5f, false)
        val night = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.WHITE, 1f, false)
        val visible = { c: Color -> c.luma() * c.alpha }
        assertTrue(visible(day.primary) > visible(half.primary))
        assertTrue(visible(half.primary) > visible(night.primary))
    }

    @Test
    fun `neutral palettes keep the StandBy orange accent`() {
        val style = StandByStyle.create(ClockTheme.ANALOG, ClockPalette.WHITE, 0f, false)
        assertEquals(Color(0xFFFF9F0A), style.accent)
        val pink = StandByStyle.create(ClockTheme.ANALOG, ClockPalette.PINK, 0f, false)
        assertEquals(Color(ClockPalette.PINK.accent), pink.accent)
    }
}
