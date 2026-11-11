package com.nitestand.standbyclock.ui.standby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.StandByUiState
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.StandByWidget
import com.nitestand.standbyclock.widgets.WidgetDataSources
import com.nitestand.standbyclock.widgets.WidgetHost
import com.nitestand.standbyclock.widgets.WidgetRegistry
import kotlinx.coroutines.delay
import kotlin.math.abs

object StandByTestTags {
    const val ROOT = "standby_root"
    const val ACTIVE = "standby_active"
    const val WAITING = "standby_waiting"
}

/**
 * The StandBy surface shared by the activity and the dream.
 *
 * Gestures: double tap or back exits, single tap reveals a short hint, horizontal swipe
 * changes the clock design. TalkBack users get "exit" as the click action and "next clock
 * style" as a custom action.
 */
@Composable
fun StandByScreen(
    state: StandByUiState,
    sources: WidgetDataSources,
    onExit: () -> Unit,
    onCycleTheme: (Int) -> Unit,
    systemReduceMotion: Boolean,
    modifier: Modifier = Modifier,
    inactiveContent: @Composable () -> Unit,
) {
    val settings = state.settings
    val active = state.activation.isActive
    val reduceMotion = settings.reduceMotion || systemReduceMotion
    // Night mode fades in/out over a few seconds instead of switching abruptly.
    val nightFraction by animateFloatAsState(
        targetValue = if (state.nightActive) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 2_500),
        label = "nightFraction",
    )
    val palette = ClockFaceCatalog.paletteFor(settings)
    val style = remember(settings.clockTheme, palette, nightFraction, reduceMotion) {
        StandByStyle.create(settings.clockTheme, palette, nightFraction, reduceMotion)
    }

    val exit by rememberUpdatedState(onExit)
    val cycle by rememberUpdatedState(onCycleTheme)
    var hintRequest by remember { mutableIntStateOf(if (active) 1 else 0) }
    val exitLabel = stringResource(R.string.a11y_exit_standby)
    val nextStyleLabel = stringResource(R.string.a11y_next_clock_style)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(StandByTestTags.ROOT)
            .then(
                if (active) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { exit() }, onTap = { hintRequest++ })
                        }
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (abs(total) > 72.dp.toPx()) cycle(if (total < 0) 1 else -1)
                                },
                                onHorizontalDrag = { _, amount -> total += amount },
                            )
                        }
                        .semantics {
                            onClick(label = exitLabel) { exit(); true }
                            customActions = listOf(CustomAccessibilityAction(nextStyleLabel) { cycle(1); true })
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Crossfade(
            targetState = active,
            animationSpec = if (reduceMotion) snap() else tween(700),
            label = "standbyActive",
        ) { isActive ->
            if (isActive) {
                ActiveStandBy(settings, style, sources, Modifier.testTag(StandByTestTags.ACTIVE))
            } else {
                Box(Modifier.fillMaxSize().testTag(StandByTestTags.WAITING)) { inactiveContent() }
            }
        }

        if (active) {
            GestureHint(hintRequest, style, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ActiveStandBy(
    settings: StandBySettings,
    style: StandByStyle,
    sources: WidgetDataSources,
    modifier: Modifier = Modifier,
) {
    val offset = rememberBurnInOffset(settings.burnInProtection, settings.burnInIntervalSeconds, style.reduceMotion)
    // Some faces (ambient, world, calendar) are intentionally clock-only.
    val panels = if (!ClockFaceCatalog.spec(settings.clockTheme).allowsPanel) emptyList() else WidgetRegistry.enabledPanelWidgets(settings)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        val landscape = maxWidth >= maxHeight
        val content = Modifier
            .fillMaxSize()
            .offset { offset }
        when {
            panels.isEmpty() ->
                WidgetHost(WidgetRegistry.clock, sources, settings, style, content)
            landscape -> Row(content, verticalAlignment = Alignment.CenterVertically) {
                WidgetHost(WidgetRegistry.clock, sources, settings, style, Modifier.weight(1.7f).fillMaxHeight())
                Spacer(Modifier.width(28.dp))
                PanelColumn(panels, sources, settings, style, Modifier.weight(1f).fillMaxHeight())
            }
            else -> Column(content, horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetHost(WidgetRegistry.clock, sources, settings, style, Modifier.weight(1.4f).fillMaxWidth())
                PanelColumn(panels, sources, settings, style, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun PanelColumn(
    widgets: List<StandByWidget<*>>,
    sources: WidgetDataSources,
    settings: StandBySettings,
    style: StandByStyle,
    modifier: Modifier,
) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        widgets.forEach { widget ->
            WidgetHost(widget, sources, settings, style, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun GestureHint(request: Int, style: StandByStyle, modifier: Modifier) {
    var visibleFor by remember { mutableIntStateOf(0) }
    LaunchedEffect(request) {
        if (request > 0) {
            visibleFor = request
            delay(3_500)
            visibleFor = 0
        }
    }
    AnimatedVisibility(
        visible = visibleFor > 0,
        enter = fadeIn(tween(if (style.reduceMotion) 0 else 400)),
        exit = fadeOut(tween(if (style.reduceMotion) 0 else 800)),
        modifier = modifier.padding(bottom = 12.dp),
    ) {
        Text(
            stringResource(R.string.standby_gesture_hint),
            color = style.secondary,
            fontSize = 14.sp,
        )
    }
}
