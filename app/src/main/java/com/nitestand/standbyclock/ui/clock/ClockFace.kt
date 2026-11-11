package com.nitestand.standbyclock.ui.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.clock.ClockFaceSpec
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.ClockFaceData

/** Everything a face needs to render one frame: time data (formatted centrally) and the animated style. */
@Immutable
data class ClockState(val data: ClockFaceData, val style: StandByStyle)

/**
 * A clock face: an independent visual composition. Faces share the time provider, formatting,
 * colour system, night state and motion tokens, but own their layout and drawing completely.
 * Add a face by implementing this, adding a [ClockTheme] entry and a spec in [ClockFaceCatalog].
 */
interface ClockFace {
    val id: ClockTheme
    val spec: ClockFaceSpec get() = ClockFaceCatalog.spec(id)

    @Composable
    fun Render(state: ClockState, modifier: Modifier)
}

private class FaceOf(override val id: ClockTheme, private val content: @Composable (ClockFaceData, StandByStyle) -> Unit) : ClockFace {
    @Composable
    override fun Render(state: ClockState, modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) { content(state.data, state.style) }
    }
}

object ClockFaces {
    val all: List<ClockFace> = listOf(
        FaceOf(ClockTheme.DIGITAL_LARGE) { d, s -> DigitalLargeFace(d, s) },
        FaceOf(ClockTheme.CLASSIC) { d, s -> RoundedBoldFace(d, s) },
        FaceOf(ClockTheme.BUBBLE) { d, s -> BubbleFace(d, s) },
        FaceOf(ClockTheme.DIGITAL_COLOR) { d, s -> DigitalColorFace(d, s) },
        FaceOf(ClockTheme.MINIMAL) { d, s -> MinimalFace(d, s) },
        FaceOf(ClockTheme.WORLD) { d, s -> WorldFace(d, s) },
        FaceOf(ClockTheme.CALENDAR) { d, s -> CalendarFace(d, s) },
        FaceOf(ClockTheme.ANALOG) { d, s -> AnalogClassicFace(d, s) },
        FaceOf(ClockTheme.ANALOG_MINIMAL) { d, s -> AnalogMinimalFace(d, s) },
        FaceOf(ClockTheme.ANALOG_MODERN) { d, s -> AnalogModernFace(d, s) },
        FaceOf(ClockTheme.ANALOG_ELEGANT) { d, s -> AnalogElegantFace(d, s) },
        FaceOf(ClockTheme.ANALOG_LARGE) { d, s -> AnalogLargeHandsFace(d, s) },
        FaceOf(ClockTheme.ANALOG_LINE) { d, s -> AnalogThinLineFace(d, s) },
        FaceOf(ClockTheme.RETRO) { d, s -> RetroClock(d, s) },
        FaceOf(ClockTheme.GEOMETRIC) { d, s -> OrbitFace(d, s) },
        FaceOf(ClockTheme.SERIF) { d, s -> SerifFace(d, s) },
        FaceOf(ClockTheme.LINE) { d, s -> LineFace(d, s) },
        FaceOf(ClockTheme.AMBIENT) { d, s -> AmbientFace(d, s) },
    )

    private val byId = all.associateBy { it.id }

    fun of(id: ClockTheme): ClockFace = byId.getValue(id)
}

/** Renders [ClockFaceData.theme] with TalkBack reading the full time and date. */
@Composable
fun ClockFaceView(data: ClockFaceData, style: StandByStyle, modifier: Modifier = Modifier) {
    ClockFaces.of(data.theme).Render(
        ClockState(data, style),
        modifier.semantics(mergeDescendants = true) { contentDescription = data.text.accessibilityLabel }.then(Modifier.fillMaxSize()),
    )
}
