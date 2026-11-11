package com.nitestand.standbyclock.clock

import com.nitestand.standbyclock.settings.StandBySettings

enum class FaceCategory { DIGITAL, ANALOG, EXPRESSIVE }

/**
 * Behaviour of one clock face. Rendering lives in the UI layer; everything a face *supports* is
 * declared here so settings, the gallery and the renderer agree without duplicating rules.
 */
data class ClockFaceSpec(
    val id: ClockTheme,
    val category: FaceCategory,
    val palettes: List<ClockPalette>,
    val defaultPalette: ClockPalette,
    val supportsDate: Boolean,
    val supportsSeconds: Boolean = true,
    val supportsAmPm: Boolean = true,
    /** Whether the optional side panel (date / battery / alarm widgets) may appear next to it. */
    val allowsPanel: Boolean = true,
    val usesWorldClock: Boolean = false,
    /** Fixed, very dim appearance: no colour choice. */
    val fixedDim: Boolean = false,
) {
    init {
        require(palettes.isEmpty() || defaultPalette in palettes)
    }
}

object ClockFaceCatalog {
    private val all = ClockPalette.entries.toList()
    private val colorful = all - ClockPalette.WHITE - ClockPalette.WARM_WHITE

    val faces: List<ClockFaceSpec> = listOf(
        ClockFaceSpec(ClockTheme.DIGITAL_LARGE, FaceCategory.DIGITAL, all, ClockPalette.WHITE, supportsDate = false),
        ClockFaceSpec(ClockTheme.DIGITAL_COLOR, FaceCategory.DIGITAL, colorful, ClockPalette.PURPLE, supportsDate = true),
        ClockFaceSpec(
            ClockTheme.MINIMAL, FaceCategory.DIGITAL,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.SOFT_ORANGE, ClockPalette.BLUE),
            ClockPalette.WHITE, supportsDate = true,
        ),
        ClockFaceSpec(ClockTheme.CLASSIC, FaceCategory.DIGITAL, all, ClockPalette.WHITE, supportsDate = true),
        ClockFaceSpec(
            ClockTheme.WORLD, FaceCategory.DIGITAL,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.SOFT_ORANGE, ClockPalette.BLUE, ClockPalette.GREEN),
            ClockPalette.SOFT_ORANGE, supportsDate = true, usesWorldClock = true, allowsPanel = false,
        ),
        ClockFaceSpec(
            ClockTheme.ANALOG, FaceCategory.ANALOG,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.SOFT_ORANGE, ClockPalette.BLUE, ClockPalette.GREEN, ClockPalette.PINK),
            ClockPalette.WHITE, supportsDate = true, supportsAmPm = false,
        ),
        ClockFaceSpec(
            ClockTheme.ANALOG_MINIMAL, FaceCategory.ANALOG,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE),
            ClockPalette.WHITE, supportsDate = false, supportsAmPm = false,
        ),
        ClockFaceSpec(ClockTheme.ANALOG_MODERN, FaceCategory.ANALOG, colorful, ClockPalette.SOFT_ORANGE, supportsDate = true, supportsAmPm = false),
        ClockFaceSpec(
            ClockTheme.ANALOG_ELEGANT, FaceCategory.ANALOG,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.SOFT_ORANGE),
            ClockPalette.WARM_WHITE, supportsDate = true, supportsAmPm = false,
        ),
        ClockFaceSpec(ClockTheme.BUBBLE, FaceCategory.EXPRESSIVE, colorful, ClockPalette.PINK, supportsDate = false, supportsSeconds = false),
        ClockFaceSpec(
            ClockTheme.SERIF, FaceCategory.EXPRESSIVE,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.SOFT_ORANGE, ClockPalette.RED),
            ClockPalette.WARM_WHITE, supportsDate = true,
        ),
        ClockFaceSpec(
            ClockTheme.LINE, FaceCategory.EXPRESSIVE,
            listOf(ClockPalette.WHITE, ClockPalette.WARM_WHITE, ClockPalette.BLUE, ClockPalette.GREEN, ClockPalette.SOFT_ORANGE),
            ClockPalette.WHITE, supportsDate = true,
        ),
        ClockFaceSpec(
            ClockTheme.CALENDAR, FaceCategory.EXPRESSIVE,
            listOf(ClockPalette.RED, ClockPalette.SOFT_ORANGE, ClockPalette.BLUE, ClockPalette.WHITE),
            ClockPalette.RED, supportsDate = true, allowsPanel = false,
        ),
        ClockFaceSpec(
            ClockTheme.RETRO, FaceCategory.EXPRESSIVE,
            listOf(ClockPalette.RED, ClockPalette.SOFT_ORANGE, ClockPalette.YELLOW, ClockPalette.GREEN, ClockPalette.BLUE),
            ClockPalette.RED, supportsDate = true,
        ),
        ClockFaceSpec(
            ClockTheme.AMBIENT, FaceCategory.EXPRESSIVE, emptyList(), ClockPalette.WHITE,
            supportsDate = true, allowsPanel = false, fixedDim = true,
        ),
    )

    private val byId = faces.associateBy { it.id }

    fun spec(id: ClockTheme): ClockFaceSpec = byId.getValue(id)

    /** The palette to use for [id]: the user's choice if the face supports it, else the face default. */
    fun paletteFor(settings: StandBySettings, id: ClockTheme = settings.clockTheme): ClockPalette {
        val spec = spec(id)
        return settings.faceColors[id]?.takeIf { it in spec.palettes } ?: spec.defaultPalette
    }

    fun byCategory(category: FaceCategory): List<ClockFaceSpec> = faces.filter { it.category == category }
}
