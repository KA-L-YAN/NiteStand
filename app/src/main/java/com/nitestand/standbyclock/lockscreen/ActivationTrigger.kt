package com.nitestand.standbyclock.lockscreen

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.standby.ActivationMode

/** Everything the lock-screen watcher knows at one moment. */
data class WatcherInputs(
    val standByEnabled: Boolean,
    val watcherEnabled: Boolean,
    val mode: ActivationMode,
    val screenOn: Boolean,
    val keyguardLocked: Boolean,
    val charging: ChargingStatus?,
    val orientation: DeviceOrientation,
    val standByShowing: Boolean,
) {
    val eligible: Boolean get() = standByEnabled && watcherEnabled && mode.supportsLockScreenWatcher

    /** Continuous sensor use is limited to the awake lock screen; screen-off uses a one-shot sample. */
    val needsOrientationSensor: Boolean
        get() = eligible && mode.requiresLandscape && screenOn && keyguardLocked && !standByShowing

    val powerMet: Boolean get() = !mode.requiresCharging || charging?.isPowered == true
    val orientationMet: Boolean
        get() = !mode.requiresLandscape || orientation.isLandscape || orientation == DeviceOrientation.UNAVAILABLE

    /** True when the orientation is actually known (a reading exists or no sensor is needed). */
    val orientationKnown: Boolean get() = !mode.requiresLandscape || orientation != DeviceOrientation.UNKNOWN
}

/** Phases of one automatic activation, shown in Diagnostics. */
enum class ActivationPhase {
    IDLE,
    ACTIVATION_DETECTED,
    LAUNCH_REQUESTED,
    STANDBY_ACTIVE,
    LAUNCH_FAILED,
    FALLBACK_NOTIFICATION,
}

/** What made the conditions true. */
enum class ActivationSource {
    /**
     * The screen is off (phone locked) and the phone is in position — either already when it was
     * locked, or placed on the stand within the watch window after locking.
     *
     * This is the only automatic source: a full-screen intent posted while the device is
     * non-interactive is launched by Android (alarm-clock behaviour). On OxygenOS 16 a full-screen
     * intent posted while the *awake* lock screen is showing is presented as a notification
     * instead (confirmed on a OnePlus 12R, NiteStand 2.1.0 trace), so the awake lock screen never
     * triggers a launch; StandBy follows as soon as that screen turns off.
     */
    SCREEN_OFF,
}

/**
 * The watcher's activation state machine. Pure and deterministic.
 *
 * It fires **once per placement**. After firing (or after StandBy was shown by any means) it is
 * disarmed, and it only re-arms when a condition is *observed* to break:
 * - the phone is unlocked (normal use resumes),
 * - the orientation is measured as not landscape (picked up), or
 * - power is lost (in charging modes), or the feature is turned off.
 *
 * This prevents loops: StandBy's own battery timeout turning the screen off, the user pressing
 * power while StandBy shows, exiting StandBy, or a failed launch never re-trigger by themselves.
 */
class ActivationTrigger {
    var armed: Boolean = true
        private set
    var phase: ActivationPhase = ActivationPhase.IDLE
        private set

    /** A reading taken while the screen is off (at lock time or during the watch window after it). */
    fun onScreenOff(inputs: WatcherInputs): ActivationSource? = evaluate(inputs, ActivationSource.SCREEN_OFF)

    /**
     * Any change while the screen is on (orientation on the awake lock screen, power, settings).
     * Only updates arming (e.g. portrait observed → re-armed); it never launches.
     */
    fun update(inputs: WatcherInputs) {
        evaluate(inputs, source = null)
    }

    fun onUnlocked() {
        armed = true
        if (phase != ActivationPhase.STANDBY_ACTIVE) phase = ActivationPhase.IDLE
    }

    fun onLaunchRequested() {
        phase = ActivationPhase.LAUNCH_REQUESTED
    }

    fun onLaunchConfirmed() {
        phase = ActivationPhase.STANDBY_ACTIVE
    }

    fun onLaunchFailed() {
        phase = ActivationPhase.LAUNCH_FAILED
    }

    fun onFallbackPosted() {
        phase = ActivationPhase.FALLBACK_NOTIFICATION
    }

    private fun evaluate(inputs: WatcherInputs, source: ActivationSource?): ActivationSource? {
        if (!inputs.eligible) {
            armed = true
            phase = ActivationPhase.IDLE
            return null
        }
        if (inputs.standByShowing) {
            armed = false
            phase = ActivationPhase.STANDBY_ACTIVE
            return null
        }
        if (phase == ActivationPhase.STANDBY_ACTIVE) phase = ActivationPhase.IDLE
        val brokenPower = !inputs.powerMet
        val brokenOrientation = inputs.orientationKnown && !inputs.orientationMet
        if (brokenPower || brokenOrientation) {
            armed = true
            return null
        }
        if (source == null || !inputs.orientationKnown || !armed) return null
        armed = false
        phase = ActivationPhase.ACTIVATION_DETECTED
        return source
    }
}
