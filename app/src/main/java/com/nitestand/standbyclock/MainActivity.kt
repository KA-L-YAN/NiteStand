package com.nitestand.standbyclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nitestand.standbyclock.standby.StandByActivity
import com.nitestand.standbyclock.ui.MainViewModel
import com.nitestand.standbyclock.ui.settings.SettingsScreen
import com.nitestand.standbyclock.ui.setup.SetupScreen
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import com.nitestand.standbyclock.util.openScreenSaverSettings

/** Entry point: the setup / configuration screen and settings. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(appContainer) }

    private enum class Screen { SETUP, SETTINGS }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        publishLauncherShortcut()

        setContent {
            StandByClockTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val battery by viewModel.battery.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.SETUP) }
                val current = settings ?: return@StandByClockTheme

                BackHandler(enabled = screen == Screen.SETTINGS) { screen = Screen.SETUP }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState == Screen.SETTINGS
                        (slideInHorizontally(tween(300)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
                    },
                    label = "mainNavigation",
                ) { target ->
                    when (target) {
                        Screen.SETUP -> SetupScreen(
                            settings = current,
                            battery = battery,
                            sources = appContainer.widgetDataSources,
                            onStartStandBy = { startActivity(StandByActivity.intent(this@MainActivity, force = false)) },
                            onPreview = { startActivity(StandByActivity.intent(this@MainActivity, force = true)) },
                            onOpenScreenSaverSettings = { openScreenSaverSettings() },
                            onOpenSettings = { screen = Screen.SETTINGS },
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = current,
                            hasOrientationSensor = viewModel.hasOrientationSensor,
                            onUpdate = viewModel::update,
                            onBack = { screen = Screen.SETUP },
                            onOpenScreenSaverSettings = { openScreenSaverSettings() },
                        )
                    }
                }
            }
        }
    }

    /** Long-press launcher shortcut "Start StandBy". */
    private fun publishLauncherShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, StandByActivity.SHORTCUT_ID)
            .setShortLabel(getString(R.string.shortcut_start_short))
            .setLongLabel(getString(R.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_standby))
            .setIntent(StandByActivity.intent(this))
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut)) }
    }
}
