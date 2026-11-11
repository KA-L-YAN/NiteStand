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
import com.nitestand.standbyclock.diagnostics.ActivationTrace
import com.nitestand.standbyclock.diagnostics.EventLog
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.StandBySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Screen and keyguard state as seen by the watcher. */
data class ScreenState(val screenOn: Boolean, val keyguardLocked: Boolean)

/**
 * Opt-in "Lock-screen auto start".
 *
 * **Activation** ([ActivationTrigger], fires once per placement), always with the screen OFF:
 * when the screen turns off (power button, or the lock screen timing out) the watcher holds a short
 * partial wake lock and watches the orientation for [WATCH_WINDOW_MS]. If the phone is — or within
 * that window is put — in position, it starts StandBy. A full-screen intent posted while the device
 * is **non-interactive** is launched by Android immediately (alarm-clock behaviour); StandBy then
 * turns the display back on (setTurnScreenOn) above the still-locked keyguard.
 *
 * The awake lock screen never launches: OxygenOS 16 shows a full-screen intent posted in that state
 * as a plain notification (verified on a OnePlus 12R).
 *
 * **Launch pipeline** (each step recorded in [ActivationTrace]):
 * 1. direct `startActivity` — works whenever Android grants this app a background start;
 * 2. full-screen-intent notification (requires notifications + "full-screen notifications" access);
 * 3. verification: the StandBy activity must reach RESUMED within a few seconds;
 * 4. otherwise one quiet fallback notification that states why (never repeated until re-armed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockScreenWatcherService : Service() {

    private var scope: CoroutineScope? = null
    private val trigger = ActivationTrigger()
    private val screen = MutableStateFlow(ScreenState(screenOn = false, keyguardLocked = false))
    private var receiver: BroadcastReceiver? = null
    private var latest: WatcherInputs? = null
    private var sensitivityConfig = com.nitestand.standbyclock.sensors.OrientationSensitivity.NORMAL.config
    private var launchJob: Job? = null

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

        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        screen.value = readScreenState()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        screen.value = ScreenState(screenOn = true, keyguardLocked = false)
                        trigger.onUnlocked()
                        EventLog.log("Watcher: unlocked → re-armed")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screen.value = ScreenState(screenOn = false, keyguardLocked = keyguardLocked())
                        onScreenOff(serviceScope)
                    }
                    else -> {
                        screen.value = readScreenState()
                        // Screen came back on: the screen-off watch window is over (a launch in
                        // progress keeps running in its own job).
                        screenOffJob?.cancel()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val container = appContainer
        serviceScope.launch {
            combine(
                container.settingsRepository.settings,
                screen,
                container.batteryRepository.battery,
                StandBySession.showing,
            ) { s, scr, battery, showing ->
                sensitivityConfig = s.orientationSensitivity.config
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
            }.collect { inputs ->
                latest = inputs
                if (!inputs.eligible) {
                    EventLog.log("Watcher: disabled by settings, stopping")
                    stopSelf()
                    return@collect
                }
                trigger.update(inputs)
            }
        }
    }

    private var screenOffJob: Job? = null

    /**
     * Screen off: watch the orientation for a short window under a partial wake lock, so both
     * "put it on the stand, then lock" and "lock, then put it on the stand" activate StandBy.
     */
    private fun onScreenOff(serviceScope: CoroutineScope) {
        val base = latest ?: return
        if (!base.eligible || base.standByShowing) {
            // StandBy itself (battery timeout / power button) turned the screen off: do nothing.
            if (base.standByShowing) EventLog.log("Watcher: screen off while StandBy showing — no action")
            return
        }
        screenOffJob?.cancel()
        val wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NiteStand:activation")
            ?.apply { acquire(WAKE_LOCK_MS) }
        screenOffJob = serviceScope.launch {
            try {
                EventLog.log("Watcher: screen off — watching position for ${WATCH_WINDOW_MS / 1000} s (armed=${trigger.armed})")
                if (!base.mode.requiresLandscape) {
                    val inputs = base.copy(screenOn = false, keyguardLocked = keyguardLocked())
                    trigger.onScreenOff(inputs)?.let { source -> launch(source, inputs)?.join() }
                    return@launch
                }
                var last: DeviceOrientation? = null
                val fired = withTimeoutOrNull(WATCH_WINDOW_MS) {
                    appContainer.orientationRepository
                        .observe(sensitivityConfig.copy(settleMillis = SCREEN_OFF_SETTLE_MS))
                        .first { orientation ->
                            if (orientation != last) {
                                last = orientation
                                EventLog.log("Watcher: screen off, orientation=$orientation")
                            }
                            val inputs = base.copy(screenOn = false, orientation = orientation, keyguardLocked = keyguardLocked())
                            val source = trigger.onScreenOff(inputs)
                            if (source != null) {
                                launch(source, inputs)
                                true
                            } else {
                                false
                            }
                        }
                }
                if (fired != null) launchJob?.join() else EventLog.log("Watcher: watch window ended (last=$last)")
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    /** Runs the launch pipeline once for an activation. */
    private fun launch(source: ActivationSource, inputs: WatcherInputs): Job? {
        if (launchJob?.isActive == true) return null
        val serviceScope = scope ?: return null
        val job = serviceScope.launch {
            val context = this@LockScreenWatcherService
            ActivationTrace.begin("Activation condition detected (${source.label()}, mode ${inputs.mode}, orientation ${inputs.orientation}, power ${inputs.charging})")
            ActivationTrace.step("Watcher running; screen ${if (isInteractive()) "on" else "off"}, keyguard ${if (keyguardLocked()) "locked" else "unlocked"}")
            val readiness = LaunchReadiness.read(context)
            ActivationTrace.step("Readiness: ${if (readiness.issues.isEmpty()) "all set" else readiness.issues.joinToString()}")
            trigger.onLaunchRequested()
            val before = StandBySession.resumeCount.value

            // 1) Direct start: allowed when Android grants this app a background start.
            try {
                startActivity(StandByNotifications.launchIntent(context))
                ActivationTrace.step("Direct activity start requested")
            } catch (e: RuntimeException) {
                ActivationTrace.step("Direct activity start rejected: ${e.javaClass.simpleName}")
            }
            if (awaitResumed(before, DIRECT_WAIT_MS)) return@launch confirmed()

            // 2) Full-screen intent.
            if (readiness.notificationsAllowed) {
                val posted = StandByNotifications.postFullScreenLaunch(context)
                ActivationTrace.step(
                    if (posted) {
                        "Full-screen intent posted (channel importance ${StandByNotifications.launchChannelImportance(context)}, " +
                            "full-screen access ${if (readiness.fullScreenAllowed) "granted" else "DENIED"})"
                    } else {
                        "Full-screen intent could not be posted"
                    },
                )
                if (posted && awaitResumed(before, FSI_WAIT_MS)) return@launch confirmed()
            } else {
                ActivationTrace.step("Full-screen intent skipped: notifications are off")
            }

            // 3) Failure → one quiet, explanatory fallback.
            trigger.onLaunchFailed()
            val reason = readiness.blockingIssue
            ActivationTrace.step("Launch blocked: StandBy did not resume (${reason?.name ?: "system/OEM declined the launch"})")
            if (StandByNotifications.postFallback(context, reason)) {
                trigger.onFallbackPosted()
                ActivationTrace.step("Fallback notification posted")
            }
        }
        launchJob = job
        return job
    }

    private fun confirmed() {
        trigger.onLaunchConfirmed()
        StandByNotifications.cancelLaunch(this)
        ActivationTrace.step("Launch confirmed: StandBy activity resumed")
    }

    private suspend fun awaitResumed(before: Int, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) { StandBySession.resumeCount.first { it > before } } != null

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

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive == true

    private fun readScreenState(): ScreenState = ScreenState(screenOn = isInteractive(), keyguardLocked = keyguardLocked())

    private fun ActivationSource.label() = when (this) {
        ActivationSource.SCREEN_OFF -> "screen off, in position"
    }

    companion object {
        /** Upper bound: watch window (12 s) + launch verification (≤4.7 s); released as soon as done. */
        private const val WAKE_LOCK_MS = 18_000L

        /** How long after the screen turns off the phone may still be put on the stand. */
        private const val WATCH_WINDOW_MS = 12_000L

        /** The orientation must be stable this long (no false triggers while being handled). */
        private const val SCREEN_OFF_SETTLE_MS = 500L
        private const val DIRECT_WAIT_MS = 700L
        private const val FSI_WAIT_MS = 4_000L

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
