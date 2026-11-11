package com.nitestand.standbyclock

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nitestand.standbyclock.diagnostics.DeviceDiagnostics
import com.nitestand.standbyclock.diagnostics.DiagnosticsReport
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.lockscreen.LockScreenWatcherService
import com.nitestand.standbyclock.lockscreen.StandByNotifications
import com.nitestand.standbyclock.sensors.OrientationReading
import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.standby.StandByActivity
import com.nitestand.standbyclock.standby.StandBySession
import com.nitestand.standbyclock.ui.MainViewModel
import com.nitestand.standbyclock.ui.diagnostics.DiagnosticsActions
import com.nitestand.standbyclock.ui.diagnostics.DiagnosticsScreen
import com.nitestand.standbyclock.ui.gallery.ClockGalleryScreen
import com.nitestand.standbyclock.ui.settings.SettingsScreen
import com.nitestand.standbyclock.ui.setup.SetupScreen
import com.nitestand.standbyclock.ui.theme.StandByClockTheme
import com.nitestand.standbyclock.util.openScreenSaverSettings
import kotlinx.coroutines.delay

/** Entry point: setup, settings and diagnostics. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(appContainer) }

    private enum class Screen { SETUP, SETTINGS, CLOCK_FACES, DIAGNOSTICS }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        publishLauncherShortcut()
        StandByNotifications.createChannels(this)

        setContent {
            StandByClockTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val battery by viewModel.battery.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.SETUP) }
                var showFsiDialog by rememberSaveable { mutableStateOf(false) }
                var galleryReturn by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
                val current = settings ?: return@StandByClockTheme

                // Keep the opt-in lock-screen watcher service in sync with the settings.
                val watcherWanted = current.standByEnabled && current.lockScreenWatcher && current.activationMode.supportsLockScreenWatcher
                LaunchedEffect(watcherWanted) { LockScreenWatcherService.sync(this@MainActivity, watcherWanted) }

                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    EventLog.log("Notification permission ${if (granted) "granted" else "denied"}")
                    if (granted && !StandByNotifications.canUseFullScreenIntent(this)) showFsiDialog = true
                }
                val onToggleWatcher: (Boolean) -> Unit = { enable ->
                    viewModel.update { it.copy(lockScreenWatcher = enable) }
                    if (enable) {
                        if (!StandByNotifications.canPostNotifications(this)) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else if (!StandByNotifications.canUseFullScreenIntent(this)) {
                            showFsiDialog = true
                        }
                    }
                }

                BackHandler(enabled = screen != Screen.SETUP) {
                    screen = when (screen) {
                        Screen.DIAGNOSTICS -> Screen.SETTINGS
                        Screen.CLOCK_FACES -> galleryReturn
                        else -> Screen.SETUP
                    }
                }

                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
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
                            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                            onOpenClockFaces = {
                                galleryReturn = Screen.SETUP
                                screen = Screen.CLOCK_FACES
                            },
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            settings = current,
                            hasOrientationSensor = viewModel.hasOrientationSensor,
                            onUpdate = viewModel::update,
                            onToggleWatcher = onToggleWatcher,
                            onBack = { screen = Screen.SETUP },
                            onOpenScreenSaverSettings = { openScreenSaverSettings() },
                            onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                            onOpenClockFaces = {
                                galleryReturn = Screen.SETTINGS
                                screen = Screen.CLOCK_FACES
                            },
                        )
                        Screen.CLOCK_FACES -> ClockGalleryScreen(
                            settings = current,
                            sources = appContainer.widgetDataSources,
                            onUpdate = viewModel::update,
                            onBack = { screen = galleryReturn },
                            onPreviewFullScreen = { startActivity(StandByActivity.intent(this@MainActivity, force = true)) },
                        )
                        Screen.DIAGNOSTICS -> DiagnosticsRoute(current, onBack = { screen = Screen.SETTINGS })
                    }
                }

                if (showFsiDialog) {
                    AlertDialog(
                        onDismissRequest = { showFsiDialog = false },
                        title = { Text(stringResource(R.string.fsi_dialog_title)) },
                        text = { Text(stringResource(R.string.fsi_dialog_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showFsiDialog = false
                                openFullScreenIntentSettings()
                            }) { Text(stringResource(R.string.fsi_dialog_open)) }
                        },
                        dismissButton = { TextButton(onClick = { showFsiDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
                    )
                }
            }
        }
    }

    @Composable
    private fun DiagnosticsRoute(settings: StandBySettings, onBack: () -> Unit) {
        val container = appContainer
        var snapshot by remember { mutableStateOf(DeviceDiagnostics.snapshot(this)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        // Refresh the system snapshot every 2 s while this screen is visible (keyguard, permissions…).
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    snapshot = DeviceDiagnostics.snapshot(this@MainActivity)
                    delay(2_000)
                }
            }
        }
        val battery by container.batteryRepository.battery.collectAsStateWithLifecycle(initialValue = null)
        val readings = remember(settings.orientationSensitivity) {
            container.orientationRepository.readings(settings.orientationSensitivity.config)
        }
        val orientation by readings.collectAsStateWithLifecycle(initialValue = null as OrientationReading?)
        val showing by StandBySession.showing.collectAsStateWithLifecycle()
        val lastState by StandBySession.lastState.collectAsStateWithLifecycle()
        val watcherRunning by LockScreenWatcherService.running.collectAsStateWithLifecycle()
        val events by EventLog.entries.collectAsStateWithLifecycle()
        val sections = DiagnosticsReport.sections(
            snapshot, battery, orientation, showing, lastState, watcherRunning, settings, BuildConfig.VERSION_NAME,
        )
        DiagnosticsScreen(
            sections = sections,
            events = events,
            actions = DiagnosticsActions(
                onBack = onBack,
                onCopy = {
                    val text = DiagnosticsReport.format(sections, events)
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("NiteStand diagnostics", text))
                    Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
                },
                onOpenScreenSaver = { openScreenSaverSettings() },
                onOpenBattery = { openBatterySettings() },
                onOpenNotifications = {
                    tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                },
                onOpenFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    { openFullScreenIntentSettings() }
                } else {
                    null
                },
            ),
        )
    }

    /** OxygenOS keeps "Battery usage: Unrestricted" under App info › Battery. */
    private fun openBatterySettings() {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) ||
            tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tryStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null))) ||
                tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Long-press launcher shortcut "Start StandBy". */
    private fun publishLauncherShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, StandByActivity.SHORTCUT_ID)
            .setShortLabel(getString(R.string.shortcut_start_short))
            .setLongLabel(getString(R.string.shortcut_start_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_standby))
            .setIntent(StandByActivity.intent(this, force = true))
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut)) }
    }
}
