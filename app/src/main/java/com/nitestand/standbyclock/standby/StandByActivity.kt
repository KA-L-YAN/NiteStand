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
                viewModel.uiState.filterNotNull().collect(::applyWindowState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FORCE, false)) viewModel.start(force = true)
    }

    private fun applyWindowState(state: StandByUiState) {
        applyShowWhenLocked(state.settings.showOnLockScreen)

        val active = state.activation.isActive
        if (active != appliedActive) {
            appliedActive = active
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
            window.attributes = window.attributes.apply { screenBrightness = brightness }
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
        viewModel.exit()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finish()
    }

    companion object {
        const val SHORTCUT_ID = "start_standby"
        const val EXTRA_FORCE = "com.nitestand.standbyclock.extra.FORCE"

        /** @param force start immediately regardless of power/orientation (preview / manual). */
        fun intent(context: Context, force: Boolean = false): Intent =
            Intent(context, StandByActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_FORCE, force)
    }
}
