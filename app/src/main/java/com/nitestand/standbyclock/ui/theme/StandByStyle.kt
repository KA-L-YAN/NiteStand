package com.nitestand.standbyclock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.settings.StandBySettings

/**
 * Colours of the StandBy display, derived from the face's [ClockPalette] and the night-mode
 * fraction (0 = day, 1 = night). Intermediate fractions give the smooth night transition.
 * The background is always true black for OLED panels.
 */
@Immutable
data class StandByStyle(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val ghost: Color,
    /** LED colour for the retro seven-segment face. */
    val retro: Color = accent,
    /** Two-stop gradient for expressive faces. */
    val gradient: List<Color> = listOf(primary, primary),
    /** Analog hand colour. */
    val hands: Color = primary,
    val background: Color = Color.Black,
    val nightFraction: Float = 0f,
    val reduceMotion: Boolean = false,
) {
    val night: Boolean get() = nightFraction >= 0.5f

    companion object {
        /** StandBy night red: deep, slightly desaturated, never pure white. */
        val NightRed = Color(0xFFC8322A)
        private val Gray = Color(0xFF8E8E93)
        private val HandWhite = Color(0xFFF2F2F7)

        fun forSettings(
            settings: StandBySettings,
            theme: ClockTheme = settings.clockTheme,
            nightFraction: Float = 0f,
            reduceMotion: Boolean = false,
        ): StandByStyle = create(theme, ClockFaceCatalog.paletteFor(settings, theme), nightFraction, reduceMotion)

        fun create(theme: ClockTheme, palette: ClockPalette, nightFraction: Float, reduceMotion: Boolean): StandByStyle {
            val day = day(theme, palette)
            val f = nightFraction.coerceIn(0f, 1f)
            if (f == 0f) return day.copy(reduceMotion = reduceMotion)
            val night = night(theme)
            return StandByStyle(
                primary = lerp(day.primary, night.primary, f),
                secondary = lerp(day.secondary, night.secondary, f),
                accent = lerp(day.accent, night.accent, f),
                ghost = lerp(day.ghost, night.ghost, f),
                retro = lerp(day.retro, night.retro, f),
                gradient = day.gradient.zip(night.gradient) { a, b -> lerp(a, b, f) },
                hands = lerp(day.hands, night.hands, f),
                nightFraction = f,
                reduceMotion = reduceMotion,
            )
        }

        private fun day(theme: ClockTheme, palette: ClockPalette): StandByStyle {
            if (ClockFaceCatalog.spec(theme).fixedDim) {
                return StandByStyle(
                    primary = Color(0xFF6B6B70),
                    secondary = Color(0xFF48484C),
                    accent = Color(0xFF7A6A55),
                    ghost = Color(0x0FFFFFFF),
                    retro = Color(0xFF7A3A33),
                    gradient = listOf(Color(0xFF6B6B70), Color(0xFF55555A)),
                    hands = Color(0xFF6B6B70),
                )
            }
            val primary = Color(palette.primary)
            val accent = Color(palette.accent)
            return StandByStyle(
                primary = primary,
                secondary = Gray,
                // Neutral palettes get the classic StandBy orange accent.
                accent = if (palette.isNeutral) Color(0xFFFF9F0A) else accent,
                ghost = primary.copy(alpha = 0.07f),
                retro = primary,
                gradient = listOf(Color(palette.gradientStart), Color(palette.gradientEnd)),
                hands = if (palette.isNeutral) primary else HandWhite,
            )
        }

        private fun night(theme: ClockTheme): StandByStyle {
            val dim = if (ClockFaceCatalog.spec(theme).fixedDim) 0.45f else 0.82f
            return StandByStyle(
                primary = NightRed.copy(alpha = dim),
                secondary = NightRed.copy(alpha = 0.45f),
                accent = NightRed.copy(alpha = 0.9f),
                ghost = NightRed.copy(alpha = 0.05f),
                retro = NightRed.copy(alpha = dim),
                gradient = listOf(NightRed.copy(alpha = dim), Color(0xFF9E2620).copy(alpha = dim)),
                hands = NightRed.copy(alpha = dim),
                nightFraction = 1f,
            )
        }
    }
}
