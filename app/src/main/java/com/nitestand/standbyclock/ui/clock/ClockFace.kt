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

/** Renders the clock in the design selected in [ClockFaceData.theme]. */
@Composable
fun ClockFace(data: ClockFaceData, style: StandByStyle, modifier: Modifier = Modifier) {
    Box(
        modifier.semantics(mergeDescendants = true) { contentDescription = data.text.accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = data.theme,
            animationSpec = if (style.reduceMotion) snap() else tween(durationMillis = 600),
            label = "clockTheme",
        ) { theme ->
            when (theme) {
                ClockTheme.CLASSIC -> ClassicClock(data, style)
                ClockTheme.MINIMAL -> MinimalClock(data, style)
                ClockTheme.RETRO -> RetroClock(data, style)
                ClockTheme.ANALOG -> AnalogClock(data, style)
                ClockTheme.AMBIENT -> AmbientClock(data, style)
            }
        }
    }
}
