package com.nitestand.standbyclock.clock

import com.nitestand.standbyclock.settings.StandBySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockFaceCatalogTest {

    @Test
    fun `every face has exactly one spec with a valid default colour`() {
        assertEquals(ClockTheme.entries.toSet(), ClockFaceCatalog.faces.map { it.id }.toSet())
        assertEquals(ClockTheme.entries.size, ClockFaceCatalog.faces.size)
        ClockFaceCatalog.faces.filter { it.palettes.isNotEmpty() }.forEach {
            assertTrue(it.id.name, it.defaultPalette in it.palettes)
        }
    }

    @Test
    fun `collection is complete`() {
        // Digital: large minimal, large rounded, colorful, gradient, minimal, world, date-focused.
        assertTrue(ClockFaceCatalog.byCategory(FaceCategory.DIGITAL).size >= 7)
        // Analog: classic, minimal, modern, elegant, large hands, thin line.
        assertTrue(ClockFaceCatalog.byCategory(FaceCategory.ANALOG).size >= 6)
        // Decorative: retro, geometric, typography-focused (+ hairline, ambient).
        assertTrue(ClockFaceCatalog.byCategory(FaceCategory.DECORATIVE).size >= 3)
        // Gallery order follows the enum (swipe order) within each category.
        val ordered = FaceCategory.entries.flatMap { c -> ClockFaceCatalog.byCategory(c).map { it.id } }
        assertEquals(ClockTheme.entries.toList(), ordered)
        assertEquals(ClockTheme.DIGITAL_LARGE, ClockTheme.DEFAULT)
        assertEquals(ClockTheme.DIGITAL_LARGE, StandBySettings().clockTheme)
    }

    @Test
    fun `colour selection respects what a face supports`() {
        val pink = StandBySettings(faceColors = mapOf(ClockTheme.DIGITAL_LARGE to ClockPalette.PINK, ClockTheme.ANALOG_MINIMAL to ClockPalette.PINK))
        assertEquals(ClockPalette.PINK, ClockFaceCatalog.paletteFor(pink, ClockTheme.DIGITAL_LARGE))
        // Analog Minimal only offers white tones: an unsupported choice falls back to the default.
        assertEquals(ClockPalette.WHITE, ClockFaceCatalog.paletteFor(pink, ClockTheme.ANALOG_MINIMAL))
        assertEquals(ClockPalette.PURPLE, ClockFaceCatalog.paletteFor(StandBySettings(), ClockTheme.DIGITAL_COLOR))
    }

    @Test
    fun `colour face only offers colours, not white`() {
        val colorFace = ClockFaceCatalog.spec(ClockTheme.DIGITAL_COLOR)
        assertFalse(ClockPalette.WHITE in colorFace.palettes)
        assertEquals(9, ClockFaceCatalog.spec(ClockTheme.DIGITAL_LARGE).palettes.size)
        assertTrue(ClockFaceCatalog.spec(ClockTheme.AMBIENT).fixedDim)
    }

    @Test
    fun `primary StandBy face stays uncluttered`() {
        val spec = ClockFaceCatalog.spec(ClockTheme.DIGITAL_LARGE)
        assertFalse(spec.supportsDate)
    }

    @Test
    fun `world cities resolve to valid zones`() {
        WorldCities.all.forEach { it.zone }
        assertEquals("Asia/Kolkata", WorldCities.find("hyderabad")!!.zoneId)
        assertEquals("Kolkata", WorldCities.labelForZone(java.time.ZoneId.of("Asia/Kolkata")))
        assertEquals("Los Angeles", WorldCities.labelForZone(java.time.ZoneId.of("America/Los_Angeles")))
    }
}
