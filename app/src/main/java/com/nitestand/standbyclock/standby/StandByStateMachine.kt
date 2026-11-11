package com.nitestand.standbyclock.standby

import com.nitestand.standbyclock.charging.ChargingStatus
import com.nitestand.standbyclock.sensors.DeviceOrientation

/**
 * Central StandBy activation logic. Pure and deterministic: feed it [StandByEvent]s and it
 * returns the resulting [ActivationSnapshot].
 *
 * Rules (automatic modes):
 * - power requirement: the phone is charging or full (only if the mode requires charging)
 * - orientation requirement: the phone is in either landscape direction (only if required);
 *   when the device has no orientation sensor the requirement is treated as met
 * - all requirements met → ACTIVE if auto start is on (or the user tapped start), else READY
 * - after the user exits, StandBy stays READY until a requirement is broken, so it does not
 *   immediately re-open; breaking a requirement re-arms automatic activation
 *
 * Manual mode: only [StandByEvent.StartRequested] activates; ExitRequested deactivates.
 */
class StandByStateMachine {

    private var enabled = false
    private var mode = ActivationMode.CHARGING_AND_LANDSCAPE
    private var autoStart = true
    private var charging: ChargingStatus? = null
    private var orientation = DeviceOrientation.UNKNOWN

    /** Manual / preview start: active regardless of conditions until exit. */
    private var forced = false

    /** User tapped "start" while READY with auto start off. */
    private var confirmed = false

    /** User exited while the conditions were still met. */
    private var dismissed = false

    var snapshot: ActivationSnapshot = evaluate()
        private set

    fun onEvent(event: StandByEvent): ActivationSnapshot {
        when (event) {
            is StandByEvent.SettingsChanged -> {
                enabled = event.enabled
                mode = event.mode
                autoStart = event.autoStart
            }
            is StandByEvent.ChargingChanged -> charging = event.status
            is StandByEvent.OrientationChanged -> orientation = event.orientation
            is StandByEvent.StartRequested -> {
                dismissed = false
                if (event.force || !mode.isAutomatic) forced = true else confirmed = true
            }
            StandByEvent.ExitRequested -> {
                forced = false
                confirmed = false
                dismissed = true
            }
        }
        if (!conditionsMet()) {
            // A broken requirement re-arms automatic activation.
            dismissed = false
            confirmed = false
        }
        snapshot = evaluate()
        return snapshot
    }

    private fun powerMet(): Boolean = !mode.requiresCharging || charging?.isPowered == true

    private fun orientationMet(): Boolean =
        !mode.requiresLandscape || orientation.isLandscape || orientation == DeviceOrientation.UNAVAILABLE

    private fun conditionsMet(): Boolean = enabled && mode.isAutomatic && powerMet() && orientationMet()

    private fun evaluate(): ActivationSnapshot {
        val state = when {
            forced -> StandByState.ACTIVE
            !enabled || !mode.isAutomatic -> StandByState.IDLE
            !powerMet() -> StandByState.IDLE
            !orientationMet() ->
                if (orientation == DeviceOrientation.UNKNOWN) StandByState.CHARGING else StandByState.WAITING_FOR_LANDSCAPE
            dismissed -> StandByState.READY
            autoStart || confirmed -> StandByState.ACTIVE
            else -> StandByState.READY
        }
        return ActivationSnapshot(
            state = state,
            mode = mode,
            enabled = enabled,
            powerRequirementMet = powerMet(),
            orientationRequirementMet = orientationMet(),
            charging = charging,
            orientation = orientation,
            forced = forced,
        )
    }
}
