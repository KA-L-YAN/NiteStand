package com.nitestand.standbyclock.lockscreen

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nitestand.standbyclock.appContainer
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.StandBySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Screen and keyguard state as seen by the watcher. */
data class ScreenState(val screenOn: Boolean, val keyguardLocked: Boolean)

/**
 * Opt-in "Lock-screen auto start" (experimental).
 *
 * Android does not deliver accelerometer events to idle background apps and blocks background
 * activity starts. A foreground service is therefore required to observe the phone at all, and a
 * full-screen-intent notification is required to show StandBy from it.
 *
 * To stay battery-friendly the service itself is idle almost all the time: it listens to screen
 * on/off, unlock and power broadcasts, and runs the orientation sensor only while the phone is
 * awake on the lock screen. It never holds a wake lock and never turns the screen on by itself —
 * the user wakes the phone (power button, tap/lift to wake, or plugging in) and, if the phone is
 * locked and in landscape (and charging, if the mode requires it), StandBy is shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockScreenWatcherService : Service() {

    private var scope: CoroutineScope? = null
    private val trigger = LockScreenTrigger()
    private val screen = MutableStateFlow(ScreenState(screenOn = false, keyguardLocked = false))
    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        StandByNotifications.createChannels(this)
        ServiceCompat.startForeground(
            this,
            StandByNotifications.SERVICE_NOTIFICATION_ID,
            StandByNotifications.serviceNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        running.value = true
        EventLog.log("Watcher service started")

        screen.value = readScreenState()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> ScreenState(screenOn = true, keyguardLocked = false)
                    Intent.ACTION_SCREEN_OFF -> ScreenState(screenOn = false, keyguardLocked = keyguardLocked())
                    else -> readScreenState()
                }
                EventLog.log("Watcher: ${intent.action?.substringAfterLast('.')} → screenOn=${state.screenOn} locked=${state.keyguardLocked}")
                screen.value = state
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val container = appContainer
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        serviceScope.launch {
            val settings = container.settingsRepository.settings
            val base: Flow<WatcherInputs> = combine(
                settings,
                screen,
                container.batteryRepository.battery,
                StandBySession.showing,
            ) { s, scr, battery, showing ->
                WatcherInputs(
                    standByEnabled = s.standByEnabled,
                    watcherEnabled = s.lockScreenWatcher,
                    mode = s.activationMode,
                    screenOn = scr.screenOn,
                    keyguardLocked = scr.keyguardLocked,
                    charging = battery.status,
                    orientation = DeviceOrientation.UNKNOWN,
                    standByShowing = showing != null,
                ) to s.orientationSensitivity
            }.distinctUntilChanged().flatMapLatest { (inputs, sensitivity) ->
                if (inputs.needsOrientationSensor) {
                    container.orientationRepository.observe(sensitivity.config).map { inputs.copy(orientation = it) }
                } else {
                    flowOf(inputs)
                }
            }
            base.collect { inputs ->
                if (!inputs.eligible) {
                    EventLog.log("Watcher: disabled by settings, stopping")
                    stopSelf()
                    return@collect
                }
                if (trigger.update(inputs)) {
                    EventLog.log("Watcher: conditions met (mode=${inputs.mode}, orientation=${inputs.orientation}, power=${inputs.charging}) → launching StandBy")
                    StandByNotifications.launchStandBy(this@LockScreenWatcherService)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        receiver = null
        scope?.cancel()
        scope = null
        running.value = false
        EventLog.log("Watcher service stopped")
        super.onDestroy()
    }

    private fun keyguardLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun readScreenState(): ScreenState = ScreenState(
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive == true,
        keyguardLocked = keyguardLocked(),
    )

    companion object {
        /** True while the service is running in this process (for diagnostics). */
        val running = MutableStateFlow(false)

        /** Starts or stops the service to match the settings. Must be called from the foreground. */
        fun sync(context: Context, enabled: Boolean) {
            val intent = Intent(context, LockScreenWatcherService::class.java)
            if (enabled && !running.value) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: IllegalStateException) {
                    EventLog.log("Watcher: could not start service: ${e.message}")
                } catch (e: SecurityException) {
                    EventLog.log("Watcher: could not start service: ${e.message}")
                }
            } else if (!enabled && running.value) {
                context.stopService(intent)
            }
        }
    }
}
