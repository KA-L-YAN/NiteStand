package com.nitestand.standbyclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF3D2A12),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFC9C5D0),
    onSecondary = Color(0xFF1F1D24),
    secondaryContainer = Color(0xFF2A2830),
    onSecondaryContainer = Color(0xFFE6E1EC),
    tertiary = Color(0xFF8AB4FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEBF0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEBF0),
    surfaceVariant = Color(0xFF1C1B20),
    onSurfaceVariant = Color(0xFFB9B5C0),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0E11),
    surfaceContainer = Color(0xFF141418),
    surfaceContainerHigh = Color(0xFF1B1B20),
    surfaceContainerHighest = Color(0xFF242429),
    outline = Color(0xFF5A5760),
    outlineVariant = Color(0xFF2E2D33),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

/** Dark Material 3 theme for the app's setup and settings screens. */
@Composable
fun StandByClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
}

/** Shared text style for clock digits: tabular figures so digits never shift. */
val ClockDigits = TextStyle(fontFeatureSettings = "tnum")
