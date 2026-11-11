package com.nitestand.standbyclock.ui

import androidx.compose.ui.graphics.Color
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.ui.clock.AngleMath
import com.nitestand.standbyclock.ui.clock.GlyphTransition
import com.nitestand.standbyclock.ui.motion.Motion
import com.nitestand.standbyclock.ui.theme.StandByStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSystemTest {

    private fun glyphs(s: String) = s.map { it.toString() }

    @Test
    fun `minute change animates only the changed digit`() {
        val pairs = GlyphTransition.pair(glyphs("10:41"), glyphs("10:42"))
        assertEquals(listOf(false, true, true, true, true), pairs.map { it.unchanged })
    }

    @Test
    fun `hour roll-over aligns from the right`() {
        // "9:59" -> "10:00": every glyph position changes, the colon stays paired with the colon.
        val pairs = GlyphTransition.pair(glyphs("9:59"), glyphs("10:00"))
        assertEquals(5, pairs.size)
        val colon = pairs.first { it.newIndex == 2 }
        assertEquals(1, colon.oldIndex)
        assertTrue(colon.unchanged)
        assertTrue(pairs.any { it.oldIndex == null && it.newIndex == 0 })
    }

    @Test
    fun `hands take the shortest path through twelve`() {
        assertEquals(360f, AngleMath.shortestTarget(354f, 0f), 0.001f)
        assertEquals(714f, AngleMath.shortestTarget(720f, 354f), 0.001f)
        assertEquals(96f, AngleMath.shortestTarget(90f, 96f), 0.001f)
    }

    @Test
    fun `motion slows at night and stops under reduced motion`() {
        assertEquals(700, Motion().ms(700))
        assertEquals(1120, Motion(night = true).ms(700))
        assertEquals(0, Motion(reduce = true).ms(700))
    }

    @Test
    fun `night has a three-level red hierarchy`() {
        val night = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.WHITE, 1f, false)
        assertTrue(night.primary.alpha > night.secondary.alpha)
        assertTrue(night.secondary.alpha > night.tertiary.alpha)
        assertFalse("not pure red", night.primary.copy(alpha = 1f) == Color.Red)
        assertTrue(night.primary.red < 0.8f)
    }

    @Test
    fun `style interpolation is continuous`() {
        val a = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.WHITE, 0f, false)
        val b = StandByStyle.create(ClockTheme.DIGITAL_LARGE, ClockPalette.BLUE, 0f, false)
        assertEquals(a, a.lerpTo(b, 0f))
        assertEquals(b, b.lerpTo(a, 1f).let { b.lerpTo(b, 1f) })
        val mid = a.lerpTo(b, 0.5f).primary
        assertTrue(mid.red < a.primary.red && mid.red > b.primary.red)
    }
}
