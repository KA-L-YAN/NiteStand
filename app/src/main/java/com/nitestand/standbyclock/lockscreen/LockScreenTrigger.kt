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

    /** The orientation sensor is only needed while the phone is awake on the lock screen. */
    val needsOrientationSensor: Boolean
        get() = eligible && mode.requiresLandscape && screenOn && keyguardLocked && !standByShowing

    val powerMet: Boolean get() = !mode.requiresCharging || charging?.isPowered == true
    val orientationMet: Boolean
        get() = !mode.requiresLandscape || orientation.isLandscape || orientation == DeviceOrientation.UNAVAILABLE
}

/**
 * Edge-triggered decision for the lock-screen watcher. It fires once when the phone is awake,
 * locked and satisfies the activation mode, then stays quiet until a condition is broken (screen
 * off, unlocked, picked up into portrait, unplugged). That way exiting StandBy with a double tap
 * does not make it re-open immediately.
 */
class LockScreenTrigger {
    private var fired = false

    /** @return true when StandBy should be launched now. */
    fun update(inputs: WatcherInputs): Boolean {
        if (inputs.eligible && inputs.standByShowing) {
            // StandBy is already on screen (launched by us or by the user). The sensor is paused
            // meanwhile, so do not treat the missing orientation as "picked up"; stay fired so that
            // exiting StandBy does not immediately re-open it.
            fired = true
            return false
        }
        val armedReset = !inputs.eligible || !inputs.screenOn || !inputs.keyguardLocked ||
            !inputs.powerMet || !inputs.orientationMet
        if (armedReset) {
            fired = false
            return false
        }
        if (fired) return false
        fired = true
        return true
    }
}
