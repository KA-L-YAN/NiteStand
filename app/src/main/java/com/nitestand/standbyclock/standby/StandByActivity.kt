package com.nitestand.standbyclock.standby

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nitestand.standbyclock.appContainer
import com.nitestand.standbyclock.display.BrightnessPolicy
import com.nitestand.standbyclock.ui.standby.StandByScreen
import com.nitestand.standbyclock.ui.standby.WaitingContent
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import com.nitestand.standbyclock.util.isSystemReduceMotion
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.lockscreen.StandByNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Mode A — the StandBy activity.
 *
 * While open it watches power and orientation. When the conditions are met it becomes ACTIVE:
 * immersive full screen, sensor-landscape (both directions), FLAG_KEEP_SCREEN_ON and the chosen
 * window brightness. When they are not met, all of that is released and the screen may time out
 * normally. With "Show on lock screen" enabled the activity uses setShowWhenLocked(true), so
 * after the screen locks it is shown above the keyguard (without unlocking the device) the next
 * time the screen turns on, e.g. when the charger is plugged in.
 */
class StandByActivity : ComponentActivity() {

    private val viewModel: StandByViewModel by viewModels {
        StandByViewModel.factory(appContainer, forceActive = intent.getBooleanExtra(EXTRA_FORCE, false))
    }

    private var appliedActive: Boolean? = null
    private var appliedBrightness: Float? = null
    private var appliedShowWhenLocked: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyShowWhenLocked(true)
        handleLaunchIntent(intent)
        ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_ID)

        onBackPressedDispatcher.addCallback(this) { exitStandBy() }

        setContent {
            StandByClockTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val current = state ?: return@StandByClockTheme
                StandByScreen(
                    state = current,
                    sources = appContainer.widgetDataSources,
                    onExit = ::exitStandBy,
                    onCycleTheme = viewModel::cycleTheme,
                    systemReduceMotion = isSystemReduceMotion(),
                    inactiveContent = {
                        WaitingContent(
                            state = current,
                            sources = appContainer.widgetDataSources,
                            onStart = viewModel::start,
                            onClose = ::exitStandBy,
                        )
                    },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Re-apply window flags (and restart the battery timer) every time the screen comes back.
                appliedActive = null
                appliedBrightness = null
                launch { StandBySession.exitRequests.collect { exitStandBy() } }
                viewModel.uiState.filterNotNull().collect(::applyWindowState)
            }
        }
    }

    override fun onDestroy() {
        StandBySession.ended(StandBySurface.ACTIVITY)
        super.onDestroy()
    }

    /**
     * Launched by the lock-screen watcher: allow this activity to turn the screen on (needed if the
     * screen went off between the trigger and the launch) and remove the launch notification.
     */
    private fun handleLaunchIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_FROM_LOCK_SCREEN, false)) {
            EventLog.log("StandBy opened from lock-screen watcher")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        StandByNotifications.cancelLaunch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FORCE, false)) viewModel.start(force = true)
    }

    private fun applyWindowState(state: StandByUiState) {
        applyShowWhenLocked(state.settings.showOnLockScreen)

        val active = state.activation.isActive
        StandBySession.report(StandBySurface.ACTIVITY, state.activation.state)
        updateBatteryTimer(state)
        if (active != appliedActive) {
            appliedActive = active
            EventLog.log("StandBy activity: ${state.activation.state} (power=${state.activation.charging}, orientation=${state.activation.orientation})")
            val insets = WindowInsetsControllerCompat(window, window.decorView)
            if (active) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insets.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        val brightness = if (active) {
            BrightnessPolicy.windowBrightness(state.settings.brightness, state.nightActive)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (brightness != appliedBrightness) {
            appliedBrightness = brightness
            rampBrightness(brightness, instant = state.settings.reduceMotion)
        }
    }

    private var brightnessJob: Job? = null

    /**
     * Moves the window brightness override to [target] over ~1.5 s (e.g. into night mode) instead
     * of jumping. Only this window is affected; the system brightness is never changed, and the
     * override disappears with the window when StandBy exits.
     */
    private fun rampBrightness(target: Float, instant: Boolean) {
        brightnessJob?.cancel()
        val start = window.attributes.screenBrightness
        fun apply(value: Float) {
            window.attributes = window.attributes.apply { screenBrightness = value }
        }
        if (instant || start < 0f || target < 0f) {
            apply(target)
            return
        }
        brightnessJob = lifecycleScope.launch {
            val steps = 15
            for (i in 1..steps) {
                apply(start + (target - start) * i / steps)
                delay(100)
            }
        }
    }

    private var batteryTimer: Job? = null
    private var batteryTimerKey: Pair<Boolean, Int>? = null

    /**
     * On battery, FLAG_KEEP_SCREEN_ON would otherwise drain the battery indefinitely. After the
     * configured time the flag is released so the normal system screen timeout turns the screen
     * off; waking the phone again shows StandBy again with a fresh timer.
     */
    private fun updateBatteryTimer(state: StandByUiState) {
        val onBattery = state.activation.isActive && state.activation.charging?.isPowered != true
        val minutes = state.settings.batteryTimeoutMinutes
        val key = (onBattery && minutes > 0) to minutes
        if (key == batteryTimerKey && batteryTimer?.isActive == true) return
        batteryTimerKey = key
        batteryTimer?.cancel()
        batteryTimer = null
        if (key.first) {
            batteryTimer = lifecycleScope.launch {
                delay(minutes * 60_000L)
                EventLog.log("StandBy: battery timeout ($minutes min) reached, releasing keep-screen-on")
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else if (state.activation.isActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyShowWhenLocked(show: Boolean) {
        if (show == appliedShowWhenLocked) return
        appliedShowWhenLocked = show
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(show)
        } else {
            @Suppress("DEPRECATION")
            if (show) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    /**
     * Leaves StandBy. If the device is locked, finishing simply reveals the secure keyguard —
     * StandBy never dismisses or bypasses it.
     */
    private fun exitStandBy() {
        EventLog.log("StandBy activity: user exit")
        viewModel.exit()
        StandBySession.ended(StandBySurface.ACTIVITY)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finish()
    }

    companion object {
        const val SHORTCUT_ID = "start_standby"
        const val EXTRA_FORCE = "com.nitestand.standbyclock.extra.FORCE"
        const val EXTRA_FROM_LOCK_SCREEN = "com.nitestand.standbyclock.extra.FROM_LOCK_SCREEN"

        /**
         * @param force start immediately regardless of power/orientation (preview / manual / tile).
         * @param fromLockScreen launched by the lock-screen watcher's full-screen intent.
         */
        fun intent(context: Context, force: Boolean = false, fromLockScreen: Boolean = false): Intent =
            Intent(context, StandByActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_FORCE, force)
                .putExtra(EXTRA_FROM_LOCK_SCREEN, fromLockScreen)
    }
}
