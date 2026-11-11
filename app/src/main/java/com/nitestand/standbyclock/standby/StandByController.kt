package com.nitestand.standbyclock.standby

import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.clock.TickInterval
import com.nitestand.standbyclock.display.NightModeSchedule
import com.nitestand.standbyclock.sensors.DeviceOrientation
import com.nitestand.standbyclock.sensors.OrientationSource
import com.nitestand.standbyclock.settings.SettingsRepository
import com.nitestand.standbyclock.settings.StandBySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class StandByUiState(
    val activation: ActivationSnapshot,
    val settings: StandBySettings,
    val nightActive: Boolean,
)

/**
 * Wires the data sources into the [StandByStateMachine] for one StandBy surface (the
 * activity or the dream). Used through a ViewModel in the activity and directly in the dream.
 *
 * Battery efficiency: everything is cold and shared with WhileSubscribed, so when the surface
 * stops observing (screen off, activity stopped) the sensor listener, broadcast receivers and
 * timers are released. The orientation sensor is only used when the activation mode needs it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StandByController(
    private val settingsRepository: SettingsRepository,
    battery: Flow<BatterySnapshot>,
    private val orientationSource: OrientationSource,
    minuteTicks: Flow<ZonedDateTime>,
    private val scope: CoroutineScope,
    forceActive: Boolean = false,
    /** The dream ignores the "start automatically" toggle: the user already chose to show it. */
    private val alwaysAutoStart: Boolean = false,
) {
    private val machine = StandByStateMachine()
    private val commands = MutableSharedFlow<StandByEvent>(extraBufferCapacity = 16)

    init {
        if (forceActive) machine.onEvent(StandByEvent.StartRequested(force = true))
    }

    private val settings: Flow<StandBySettings> = settingsRepository.settings
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val orientation: Flow<DeviceOrientation> = settings
        .map { (it.standByEnabled && it.requireLandscape) to it.orientationSensitivity }
        .distinctUntilChanged()
        .flatMapLatest { (needed, sensitivity) ->
            if (needed) orientationSource.observe(sensitivity.config) else flowOf(DeviceOrientation.UNKNOWN)
        }

    private val activation: Flow<ActivationSnapshot> = merge(
        settings.map { StandByEvent.SettingsChanged(it.standByEnabled, it.activationMode, it.autoStart || alwaysAutoStart) },
        battery.map { StandByEvent.ChargingChanged(it.status) },
        orientation.map { StandByEvent.OrientationChanged(it) },
        commands,
    )
        // merge() delivers to a single downstream collector, so the machine is never used concurrently.
        .map { machine.onEvent(it) }
        .distinctUntilChanged()

    private val nightActive: Flow<Boolean> = combine(settings, minuteTicks) { s, now ->
        NightModeSchedule.isNight(s.nightMode, s.nightStartMinutes, s.nightEndMinutes, now.toLocalTime())
    }.distinctUntilChanged()

    val uiState: StateFlow<StandByUiState?> = combine(activation, settings, nightActive) { a, s, n ->
        StandByUiState(a, s, n)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun requestStart(force: Boolean = false) {
        commands.tryEmit(StandByEvent.StartRequested(force))
    }

    fun requestExit() {
        commands.tryEmit(StandByEvent.ExitRequested)
    }

    fun cycleTheme(delta: Int) {
        scope.launch { settingsRepository.update { it.copy(clockTheme = it.clockTheme.cycle(delta)) } }
    }

    companion object {
        fun minuteTicks(ticker: com.nitestand.standbyclock.clock.ClockTicker) = ticker.ticks(TickInterval.MINUTE)
    }
}
