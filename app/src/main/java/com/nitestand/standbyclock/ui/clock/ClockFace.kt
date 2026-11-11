package com.nitestand.standbyclock.ui.clock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData

/** Renders [ClockFaceData.theme]; the single place that maps a face id to its renderer. */
@Composable
fun ClockFace(data: ClockFaceData, style: StandByStyle, modifier: Modifier = Modifier, animate: Boolean = true) {
    Box(
        modifier.semantics(mergeDescendants = true) { contentDescription = data.text.accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        if (!animate) {
            FaceContent(data, style)
        } else {
            Crossfade(
                targetState = data.theme,
                animationSpec = if (style.reduceMotion) snap() else tween(durationMillis = 600),
                label = "clockTheme",
            ) { theme -> FaceContent(data.copy(theme = theme), style) }
        }
    }
}

@Composable
private fun FaceContent(data: ClockFaceData, style: StandByStyle) {
    when (data.theme) {
        ClockTheme.DIGITAL_LARGE -> DigitalLargeFace(data, style)
        ClockTheme.DIGITAL_COLOR -> DigitalColorFace(data, style)
        ClockTheme.MINIMAL -> MinimalFace(data, style)
        ClockTheme.CLASSIC -> RoundedBoldFace(data, style)
        ClockTheme.WORLD -> WorldFace(data, style)
        ClockTheme.ANALOG -> AnalogClassicFace(data, style)
        ClockTheme.ANALOG_MINIMAL -> AnalogMinimalFace(data, style)
        ClockTheme.ANALOG_MODERN -> AnalogModernFace(data, style)
        ClockTheme.ANALOG_ELEGANT -> AnalogElegantFace(data, style)
        ClockTheme.BUBBLE -> BubbleFace(data, style)
        ClockTheme.SERIF -> SerifFace(data, style)
        ClockTheme.LINE -> LineFace(data, style)
        ClockTheme.CALENDAR -> CalendarFace(data, style)
        ClockTheme.RETRO -> RetroClock(data, style)
        ClockTheme.AMBIENT -> AmbientFace(data, style)
    }
}
