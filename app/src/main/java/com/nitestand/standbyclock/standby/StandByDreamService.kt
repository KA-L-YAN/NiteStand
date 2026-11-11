package com.nitestand.standbyclock.standby

import android.service.dreams.DreamService
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitestand.standbyclock.appContainer
import com.nitestand.standbyclock.display.BrightnessPolicy
import com.nitestand.standbyclock.ui.standby.DreamIdleContent
import com.nitestand.standbyclock.ui.standby.StandByScreen
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import com.nitestand.standbyclock.util.ComposeLifecycleHost
import com.nitestand.standbyclock.util.isSystemReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Mode B — StandBy as an Android screen saver (Dream).
 *
 * The system starts dreams itself, e.g. when the device is charging/docked and the screen
 * times out, and shows them above the keyguard. This is the only supported way for a
 * third-party app to appear automatically on a locked, charging phone without user action.
 * If the phone is not in landscape (and landscape is required), a dim fallback is shown and
 * the dream ends after a short grace period so the screen can turn off.
 */
class StandByDreamService : DreamService() {

    private var scope: CoroutineScope? = null
    private val host = ComposeLifecycleHost()
    private var idleTimer: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        val dreamScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = dreamScope
        val container = appContainer
        val controller = StandByViewModel.createController(container, dreamScope, forceActive = false, alwaysAutoStart = true)

        host.create()
        val view = ComposeView(this).apply {
            host.attachTo(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                StandByClockTheme {
                    val state by controller.uiState.collectAsStateWithLifecycle()
                    val current = state ?: return@StandByClockTheme
                    StandByScreen(
                        state = current,
                        sources = container.widgetDataSources,
                        onExit = {
                            controller.requestExit()
                            wakeUp()
                        },
                        onCycleTheme = controller::cycleTheme,
                        systemReduceMotion = isSystemReduceMotion(),
                        inactiveContent = { DreamIdleContent(current, container.widgetDataSources) },
                    )
                }
            }
        }
        host.attachTo(window.decorView)
        setContentView(view)

        dreamScope.launch {
            controller.uiState.filterNotNull().collect { state ->
                applyDisplay(state)
                handleIdle(state, dreamScope)
            }
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        host.start()
        host.resume()
    }

    override fun onDreamingStopped() {
        host.pause()
        host.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        host.destroy()
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun applyDisplay(state: StandByUiState) {
        val active = state.activation.isActive
        // In night mode let the system dim the screen as far as it allows.
        isScreenBright = !(active && state.nightActive)
        val brightness = if (active) {
            BrightnessPolicy.windowBrightness(state.settings.brightness, state.nightActive)
        } else {
            BrightnessPolicy.NIGHT_BRIGHTNESS
        }
        window?.let { w ->
            if (w.attributes.screenBrightness != brightness) {
                w.attributes = w.attributes.apply { screenBrightness = brightness }
            }
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun handleIdle(state: StandByUiState, dreamScope: CoroutineScope) {
        if (!state.settings.standByEnabled) {
            finish()
            return
        }
        if (state.activation.isActive) {
            idleTimer?.cancel()
            idleTimer = null
        } else if (idleTimer == null) {
            idleTimer = dreamScope.launch {
                delay(IDLE_GRACE_MILLIS)
                finish()
            }
        }
    }

    private companion object {
        const val IDLE_GRACE_MILLIS = 30_000L
    }
}
