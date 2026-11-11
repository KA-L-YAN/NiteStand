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

/** How StandBy decides to activate. Persisted by name. */
enum class ActivationMode(val requiresCharging: Boolean, val requiresLandscape: Boolean) {
    CHARGING_AND_LANDSCAPE(requiresCharging = true, requiresLandscape = true),
    CHARGING_ONLY(requiresCharging = true, requiresLandscape = false),
    LANDSCAPE_ONLY(requiresCharging = false, requiresLandscape = true),
    MANUAL(requiresCharging = false, requiresLandscape = false);

    val isAutomatic: Boolean get() = this != MANUAL

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
