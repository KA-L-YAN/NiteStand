package com.nitestand.standbyclock.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens for the StandBy surface. Every animation picks a base duration and an easing from
 * here, so the whole experience moves with one voice:
 * - night mode slows everything down (×1.6) for a calmer environment
 * - reduced motion (app setting or system animator scale 0) turns animations into instant changes
 */
@Immutable
data class Motion(val reduce: Boolean = false, val night: Boolean = false) {
    private val factor: Float get() = if (night) 1.6f else 1f

    /** Scaled duration in ms; 0 when motion is reduced. */
    fun ms(base: Int): Int = if (reduce) 0 else (base * factor).toInt()

    companion object {
        /** Entrances: fast start, long soft landing. */
        val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

        /** StandBy entrance: a slow, even start ("the display is waking") and a long, soft landing. */
        val Wake: Easing = CubicBezierEasing(0.32f, 0f, 0.12f, 1f)

        /** General state changes. */
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Exits: gentle start, quick finish. */
        val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        /** Symmetric, for slow ambient movement (burn-in drift). */
        val EaseInOut: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

        const val GLYPH_MS = 700
        const val SECONDS_MS = 220
        const val HAND_MS = 320
        const val FACE_MS = 650
        const val COLOR_MS = 450
        const val NIGHT_MS = 2_500
        const val ENTER_MS = 1_150
        const val ENTER_DELAY_MS = 90
        const val EXIT_MS = 380
        const val BURN_IN_MS = 8_000
    }
}

val LocalMotion = staticCompositionLocalOf { Motion() }

/**
 * Opacity of secondary content (dates, captions, widgets) during the entrance choreography.
 * A lambda so it is read in the draw/layer phase without recomposition.
 */
val LocalSecondaryAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }
