package com.nitestand.standbyclock.standby

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation

/** Lifecycle of the StandBy display. */
enum class StandByState {
    /** StandBy disabled, waiting for power, or waiting for a manual start. */
    IDLE,

    /** The power requirement is met; the orientation has not been measured yet. */
    CHARGING,

    /** The power requirement is met but the phone is not in landscape. */
    WAITING_FOR_LANDSCAPE,

    /** Every condition is met, but StandBy is not shown (auto start off, or the user exited). */
    READY,

    /** The StandBy display is showing. */
    ACTIVE,
}

/**
 * How StandBy decides to activate. Persisted by name.
 *
 * Automatic modes evaluate power/orientation while a StandBy surface (or the optional lock-screen
 * watcher) is running. Non-automatic modes only start on an explicit user action: a tap in the
 * app, the Quick Settings tile, or the system screen saver.
 */
enum class ActivationMode(
    val requiresCharging: Boolean,
    val requiresLandscape: Boolean,
    val isAutomatic: Boolean,
) {
    CHARGING_AND_LANDSCAPE(requiresCharging = true, requiresLandscape = true, isAutomatic = true),
    CHARGING_ONLY(requiresCharging = true, requiresLandscape = false, isAutomatic = true),
    LANDSCAPE_ONLY(requiresCharging = false, requiresLandscape = true, isAutomatic = true),
    MANUAL(requiresCharging = false, requiresLandscape = false, isAutomatic = false),

    /** StandBy appears only when the system starts it as the screen saver (landscape required there). */
    SCREEN_SAVER(requiresCharging = false, requiresLandscape = true, isAutomatic = false),

    /** StandBy is started from the Quick Settings tile and stays until the user exits. */
    QUICK_SETTINGS(requiresCharging = false, requiresLandscape = false, isAutomatic = false);

    /** Modes in which the optional lock-screen watcher can start StandBy by itself. */
    val supportsLockScreenWatcher: Boolean get() = isAutomatic

    /**
     * Effective mode inside the system screen saver. The system only starts a dream while charging
     * or docked, so the dream only needs to decide whether landscape is required.
     */
    val dreamMode: ActivationMode get() = if (requiresLandscape) LANDSCAPE_ONLY else CHARGING_ONLY

    companion object {
        /** Maps the two independent "require …" toggles back to a mode. Both off means manual. */
        fun from(requireCharging: Boolean, requireLandscape: Boolean): ActivationMode = when {
            requireCharging && requireLandscape -> CHARGING_AND_LANDSCAPE
            requireCharging -> CHARGING_ONLY
            requireLandscape -> LANDSCAPE_ONLY
            else -> MANUAL
        }
    }
}

/** Inputs to the state machine. */
sealed interface StandByEvent {
    data class SettingsChanged(val enabled: Boolean, val mode: ActivationMode, val autoStart: Boolean) : StandByEvent
    data class ChargingChanged(val status: ChargingStatus) : StandByEvent
    data class OrientationChanged(val orientation: DeviceOrientation) : StandByEvent

    /**
     * The user asked to start StandBy. [force] = true is a manual/preview start that ignores
     * the activation conditions until the user exits.
     */
    data class StartRequested(val force: Boolean) : StandByEvent

    /** The user left StandBy (double tap, back, …). */
    data object ExitRequested : StandByEvent
}

/** Result of the state machine plus the facts the UI needs to explain it. */
data class ActivationSnapshot(
    val state: StandByState,
    val mode: ActivationMode,
    val enabled: Boolean,
    val powerRequirementMet: Boolean,
    val orientationRequirementMet: Boolean,
    val charging: ChargingStatus?,
    val orientation: DeviceOrientation,
    val forced: Boolean,
) {
    val isActive: Boolean get() = state == StandByState.ACTIVE
}
