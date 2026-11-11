package com.nitestand.standbyclock.ui.standby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nitestand.standbyclock.R
import com.nitestand.standbyclock.clock.ClockFaceCatalog
import com.nitestand.standbyclock.diagnostics.ActivationTrace
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.StandByUiState
import com.nitestand.standbyclock.ui.clock.ClockFont
import com.nitestand.standbyclock.ui.clock.asFixedSp
import com.nitestand.standbyclock.ui.motion.LocalMotion
import com.nitestand.standbyclock.ui.motion.LocalSecondaryAlpha
import com.nitestand.standbyclock.ui.motion.Motion
import com.nitestand.standbyclock.ui.theme.StandByStyle
import com.nitestand.standbyclock.widgets.DateWidget
import com.nitestand.standbyclock.widgets.StandByWidget
import com.nitestand.standbyclock.widgets.WidgetDataSources
import com.nitestand.standbyclock.widgets.WidgetHost
import com.nitestand.standbyclock.widgets.WidgetRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class Surface { BLANK, ACTIVE, WAITING }

private const val INACTIVE_HOLD_MS = 800L

/** The entrance choreography as pure functions of one progress value (unit tested). */
object EntranceTimeline {
    /** Clock opacity: invisible for the first 5 % (black hold), then a soft ease in. */
    fun clockAlpha(e: Float): Float = ((e - 0.05f) / 0.75f).coerceIn(0f, 1f)

    /** A barely perceptible settle; never "pops" from a small size. */
    fun clockScale(e: Float): Float = 0.975f + 0.025f * e

    /** The clock condenses out of a soft blur; also blurs slightly on exit. */
    fun blurDp(e: Float, exit: Float): Float = (1f - e) * 14f + exit * 6f

    /** Secondary information starts once the clock has mostly settled. */
    fun secondary(e: Float): Float = ((e - 0.62f) / 0.38f).coerceIn(0f, 1f)
}

object StandByTestTags {
    const val ROOT = "standby_root"
    const val ACTIVE = "standby_active"
    const val WAITING = "standby_waiting"
}

/**
 * The ambient StandBy surface shared by the activity and the dream.
 *
 * Composition (back to front): true-black environment → stage (entrance/exit transform +
 * sub-pixel burn-in drift) → pages (clock, optional floating info page) → faint page dots.
 * There is no app chrome: no bars, cards, buttons or labels.
 *
 * Interaction: swipe up/down drags the clock and changes the face; swipe sideways moves between
 * pages; tap briefly reveals the page dots and a faint "double-tap to exit"; double tap (or back)
 * exits with a fade. TalkBack: click = exit, custom actions = next / previous face.
 *
 * @param exitRequested set by the host (back, tile) to run the same exit animation as a double tap
 * @param onExit called once the exit animation has finished
 */
@Composable
fun StandByScreen(
    state: StandByUiState,
    sources: WidgetDataSources,
    onExit: () -> Unit,
    onCycleTheme: (Int) -> Unit,
    systemReduceMotion: Boolean,
    modifier: Modifier = Modifier,
    exitRequested: Boolean = false,
    inactiveContent: @Composable () -> Unit,
) {
    val settings = state.settings
    val active = state.activation.isActive
    val motion = Motion(reduce = settings.reduceMotion || systemReduceMotion, night = state.nightActive)

    val exitAction by rememberUpdatedState(onExit)
    var exiting by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    LaunchedEffect(exiting || exitRequested) {
        if (exiting || exitRequested) {
            if (active) exitProgress.animateTo(1f, tween(motion.ms(Motion.EXIT_MS), easing = Motion.Accelerate))
            exitAction()
        }
    }

    CompositionLocalProvider(LocalMotion provides motion) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(StandByTestTags.ROOT),
        ) {
            // While the activation state is still settling (first orientation reading after launch),
            // stay black instead of flashing the waiting screen.
            var showInactive by remember { mutableStateOf(false) }
            LaunchedEffect(active) {
                showInactive = false
                if (!active) {
                    delay(INACTIVE_HOLD_MS)
                    showInactive = true
                }
            }
            val surface = when {
                active -> Surface.ACTIVE
                showInactive -> Surface.WAITING
                else -> Surface.BLANK
            }
            Crossfade(
                targetState = surface,
                animationSpec = tween(motion.ms(500), easing = Motion.Standard),
                label = "standbyActive",
            ) { target ->
                if (target == Surface.BLANK) {
                    Box(Modifier.fillMaxSize())
                } else if (target == Surface.ACTIVE) {
                    ActiveStage(
                        state = state,
                        sources = sources,
                        motion = motion,
                        exitProgress = { exitProgress.value },
                        onRequestExit = { exiting = true },
                        onCycleTheme = onCycleTheme,
                        modifier = Modifier.testTag(StandByTestTags.ACTIVE),
                    )
                } else {
                    Box(Modifier.fillMaxSize().testTag(StandByTestTags.WAITING)) { inactiveContent() }
                }
            }
        }
    }
}

@Composable
private fun ActiveStage(
    state: StandByUiState,
    sources: WidgetDataSources,
    motion: Motion,
    exitProgress: () -> Float,
    onRequestExit: () -> Unit,
    onCycleTheme: (Int) -> Unit,
    modifier: Modifier,
) {
    val settings = state.settings
    val palette = ClockFaceCatalog.paletteFor(settings)
    val targetStyle = remember(settings.clockTheme, palette, state.nightActive, motion.reduce) {
        StandByStyle.create(settings.clockTheme, palette, if (state.nightActive) 1f else 0f, motion.reduce)
    }
    val style = rememberAnimatedStyle(targetStyle, motion)
    val drift = rememberBurnInDrift(settings.burnInProtection, settings.burnInIntervalSeconds, motion)

    // Entrance — one timeline (≈1.2 s): black hold → the clock condenses out of a soft blur with a
    // barely perceptible 0.975→1 settle → secondary information follows → stable.
    val entrance = remember { Animatable(if (motion.reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        ActivationTrace.stepOnce("StandBy renderer active (clock composed)")
        entrance.animateTo(1f, tween(motion.ms(Motion.ENTER_MS), delayMillis = motion.ms(Motion.ENTER_DELAY_MS), easing = Motion.Wake))
    }
    val secondaryAlpha: () -> Float = { EntranceTimeline.secondary(entrance.value) * (1f - exitProgress()) }

    val pageCount = if (settings.showInfoPage) 2 else 1
    val pager = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    var dotsRequest by remember { mutableIntStateOf(if (pageCount > 1) 1 else 0) }
    var hintRequest by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(0) }
    val cycle by rememberUpdatedState(onCycleTheme)
    val changeFace: (Int) -> Unit = { d ->
        direction = d
        cycle(d)
    }

    val exitLabel = stringResource(R.string.a11y_exit_standby)
    val nextLabel = stringResource(R.string.a11y_next_clock_style)
    val previousLabel = stringResource(R.string.a11y_previous_clock_style)

    CompositionLocalProvider(LocalSecondaryAlpha provides secondaryAlpha) {
        Box(
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onRequestExit() },
                        onTap = {
                            dotsRequest++
                            hintRequest++
                        },
                    )
                }
                .semantics {
                    onClick(label = exitLabel) { onRequestExit(); true }
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) { changeFace(1); true },
                        CustomAccessibilityAction(previousLabel) { changeFace(-1); true },
                    )
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 28.dp, vertical = 18.dp)
                    .graphicsLayer {
                        val e = entrance.value
                        val x = exitProgress()
                        alpha = (EntranceTimeline.clockAlpha(e) * (1f - x)).coerceIn(0f, 1f)
                        val scale = EntranceTimeline.clockScale(e) * (1f - 0.02f * x)
                        scaleX = scale
                        scaleY = scale
                        // The drift is independent of every transition: a pure translation of the stage.
                        translationX = drift.dx
                        translationY = drift.dy
                        val blur = EntranceTimeline.blurDp(e, x) * density
                        renderEffect = if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            BlurEffect(blur, blur, TileMode.Decal)
                        } else {
                            null
                        }
                    },
            ) {
                if (pageCount == 1) {
                    ClockPage(settings, style, sources, motion, direction, changeFace, horizontalSwipeChangesFace = true)
                } else {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Depth transition instead of a flat slide: the outgoing page recedes and
                                    // fades while drifting only half the distance.
                                    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                    val a = abs(offset).coerceIn(0f, 1f)
                                    alpha = 1f - a
                                    val s = 1f - 0.08f * a
                                    scaleX = s
                                    scaleY = s
                                    translationX = offset * size.width * 0.5f
                                },
                        ) {
                            if (page == 0) {
                                ClockPage(settings, style, sources, motion, direction, changeFace, horizontalSwipeChangesFace = false)
                            } else {
                                InfoPage(settings, style, sources)
                            }
                        }
                    }
                }
            }

            PageDots(pageCount, pager.currentPage, style, motion, dotsRequest, hintRequest, Modifier.align(Alignment.BottomCenter))
        }
    }

    LaunchedEffect(settings.showInfoPage) { if (pager.currentPage != 0) scope.launch { pager.scrollToPage(0) } }
}

/**
 * The clock page. Vertical drags move the clock with a soft rubber band; letting go past the
 * threshold changes the face, and the new face arrives from the direction of the swipe.
 */
@Composable
private fun ClockPage(
    settings: StandBySettings,
    style: StandByStyle,
    sources: WidgetDataSources,
    motion: Motion,
    direction: Int,
    onChangeFace: (Int) -> Unit,
    horizontalSwipeChangesFace: Boolean,
) {
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val change by rememberUpdatedState(onChangeFace)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val threshold = 56.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = {
                        val d = drag.value
                        if (abs(d) > threshold) change(if (d < 0) 1 else -1)
                        scope.launch { drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) } },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + amount * 0.35f) }
                    },
                )
            }
            .then(
                if (horizontalSwipeChangesFace) {
                    Modifier.pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (abs(total) > 72.dp.toPx()) change(if (total < 0) 1 else -1) },
                            onHorizontalDrag = { _, amount -> total += amount },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val travel = maxHeight.value * 0.5f
        val clockLayer = Modifier.graphicsLayer {
            val d = drag.value
            translationY = d
            alpha = 1f - (abs(d) / (travel * density)).coerceIn(0f, 0.6f)
        }
        val spec = ClockFaceCatalog.spec(settings.clockTheme)
        val panels = if (settings.showPanel && spec.allowsPanel) WidgetRegistry.enabledPanelWidgets(settings) else emptyList()
        if (panels.isEmpty()) {
            FaceSwitcher(settings, style, sources, motion, direction, clockLayer.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                FaceSwitcher(settings, style, sources, motion, direction, clockLayer.weight(1.7f).fillMaxHeight())
                Spacer(Modifier.width(36.dp))
                SecondaryLayer(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(vertical = 12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        panels.forEach { WidgetHost(it, sources, settings, style, Modifier.fillMaxWidth().weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Cross-dissolves faces as one composition: the old face recedes, the new one settles in. */
@Composable
private fun FaceSwitcher(
    settings: StandBySettings,
    style: StandByStyle,
    sources: WidgetDataSources,
    motion: Motion,
    direction: Int,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = settings.clockTheme,
        modifier = modifier,
        transitionSpec = {
            val enter = motion.ms(Motion.FACE_MS)
            val exit = motion.ms((Motion.FACE_MS * 0.55f).toInt())
            val d = direction
            (
                fadeIn(tween(enter, delayMillis = exit / 3, easing = Motion.EmphasizedDecelerate)) +
                    scaleIn(tween(enter, easing = Motion.EmphasizedDecelerate), initialScale = 0.96f) +
                    slideInVertically(tween(enter, easing = Motion.EmphasizedDecelerate)) { h -> (d * h * 0.06f).toInt() }
                ) togetherWith (
                fadeOut(tween(exit, easing = Motion.Accelerate)) +
                    scaleOut(tween(exit, easing = Motion.Accelerate), targetScale = 1.02f) +
                    slideOutVertically(tween(exit, easing = Motion.Accelerate)) { h -> (-d * h * 0.06f).toInt() }
                )
        },
        label = "face",
    ) { theme ->
        val faceStyle = if (theme == settings.clockTheme) {
            style
        } else {
            remember(theme) { StandByStyle.forSettings(settings, theme, style.nightFraction, motion.reduce) }
        }
        WidgetHost(WidgetRegistry.clock, sources, settings.copy(clockTheme = theme), faceStyle, Modifier.fillMaxSize())
    }
}

/** Floating information page: date, battery, alarm (and weather) as typography — no cards. */
@Composable
private fun InfoPage(settings: StandBySettings, style: StandByStyle, sources: WidgetDataSources) {
    val widgets: List<StandByWidget<*>> = WidgetRegistry.panelWidgets.filter { it.isEnabled(settings) }.ifEmpty { listOf(DateWidget) }
    SecondaryLayer(Modifier.fillMaxSize()) {
        // Up to three items float in one row; four become a calm 2 × 2 composition.
        val rows = if (widgets.size > 3) widgets.chunked(2) else listOf(widgets)
        CompositionLocalProvider(LocalPanelScale provides if (rows.size > 1) 1.35f else 1.55f) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { widget ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                WidgetHost(widget, sources, settings, style)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Applies the entrance/exit opacity of secondary content without recomposition. */
@Composable
private fun SecondaryLayer(modifier: Modifier, content: @Composable () -> Unit) {
    val alpha = LocalSecondaryAlpha.current
    Box(modifier.graphicsLayer { this.alpha = alpha() }) { content() }
}

/**
 * Tertiary feedback: two faint dots (only with more than one page) and, after a tap, a whisper of
 * "double-tap to exit". Fades in, rests briefly, fades out; nothing persistent stays on screen.
 */
@Composable
private fun PageDots(
    pageCount: Int,
    current: Int,
    style: StandByStyle,
    motion: Motion,
    dotsRequest: Int,
    hintRequest: Int,
    modifier: Modifier,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(dotsRequest, current) {
        if (dotsRequest == 0 && current == 0) return@LaunchedEffect
        alpha.animateTo(1f, tween(motion.ms(350), easing = Motion.Standard))
        delay(2_600)
        alpha.animateTo(0f, tween(motion.ms(900), easing = Motion.Standard))
    }
    Column(
        modifier.padding(bottom = 14.dp).graphicsLayer { this.alpha = alpha.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hintRequest > 0) {
            Text(
                stringResource(R.string.standby_exit_hint),
                style = TextStyle(
                    fontFamily = ClockFont.ROUNDED.family(600),
                    fontSize = 12.dp.asFixedSp(),
                    letterSpacing = 0.06.em,
                    color = style.tertiary,
                ),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (pageCount > 1) {
            Canvas(Modifier.width(26.dp).height(6.dp)) {
                val r = size.height / 2
                for (i in 0 until pageCount) {
                    drawCircle(
                        if (i == current) style.secondary else style.tertiary,
                        r,
                        Offset(r + i * (size.width - 2 * r) / (pageCount - 1).coerceAtLeast(1), size.height / 2),
                    )
                }
            }
        }
    }
}

/** The information page on its own (used for previews and screenshot tests). */
@Composable
internal fun InfoPagePreview(settings: StandBySettings, style: StandByStyle, sources: WidgetDataSources) {
    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) { InfoPage(settings, style, sources) }
}
