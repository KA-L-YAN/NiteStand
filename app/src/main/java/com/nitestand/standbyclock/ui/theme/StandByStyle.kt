package com.nitestand.standbyclock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.settings.AccentColor

/** Colours of the StandBy display. The background is always true black for OLED panels. */
@Immutable
data class StandByStyle(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val ghost: Color,
    /** LED colour for the retro seven-segment face. */
    val retro: Color = accent,
    val background: Color = Color.Black,
    val night: Boolean = false,
    val reduceMotion: Boolean = false,
) {
    companion object {
        private val NightRed = Color(0xFFD0352B)

        fun create(accent: AccentColor, theme: ClockTheme, night: Boolean, reduceMotion: Boolean): StandByStyle {
            if (night) {
                // Night mode: monochrome red, low intensity, preserves dark adaptation.
                return StandByStyle(
                    primary = NightRed.copy(alpha = if (theme == ClockTheme.AMBIENT) 0.45f else 0.85f),
                    secondary = NightRed.copy(alpha = 0.55f),
                    accent = NightRed,
                    ghost = NightRed.copy(alpha = 0.05f),
                    retro = NightRed,
                    night = true,
                    reduceMotion = reduceMotion,
                )
            }
            if (theme == ClockTheme.AMBIENT) {
                return StandByStyle(
                    primary = Color(0xFF6B6B6B),
                    secondary = Color(0xFF4A4A4A),
                    accent = Color(0xFF7A6A55),
                    ghost = Color(0x0FFFFFFF),
                    retro = Color(0xFF7A3A33),
                    reduceMotion = reduceMotion,
                )
            }
            val (primary, accentColor) = when (accent) {
                AccentColor.WHITE -> Color(0xFFF5F5F7) to Color(0xFFFF9F0A)
                AccentColor.WARM -> Color(0xFFFFE3C2) to Color(0xFFFFB454)
                AccentColor.RED -> Color(0xFFFF7A6E) to Color(0xFFFF453A)
                AccentColor.BLUE -> Color(0xFFD6E8FF) to Color(0xFF64A8FF)
                AccentColor.MINT -> Color(0xFFD2F8E2) to Color(0xFF34D399)
            }
            return StandByStyle(
                primary = primary,
                secondary = Color(0xFF9A9AA0),
                accent = accentColor,
                ghost = primary.copy(alpha = 0.07f),
                retro = if (accent == AccentColor.WHITE) Color(0xFFFF4A3D) else accentColor,
                reduceMotion = reduceMotion,
            )
        }
    }
}
