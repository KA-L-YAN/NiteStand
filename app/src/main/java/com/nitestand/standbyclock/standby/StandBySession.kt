package com.nitestand.standbyclock.standby

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the visible StandBy display came from. */
enum class StandBySurface { ACTIVITY, SCREEN_SAVER }

/**
 * In-process record of whether a StandBy display is currently showing, shared by the activity,
 * the dream, the Quick Settings tile, the lock-screen watcher and the diagnostics screen.
 */
object StandBySession {
    private val _showing = MutableStateFlow<StandBySurface?>(null)
    val showing: StateFlow<StandBySurface?> = _showing.asStateFlow()

    private val _lastState = MutableStateFlow<StandByState?>(null)
    val lastState: StateFlow<StandByState?> = _lastState.asStateFlow()

    private val _resumeCount = MutableStateFlow(0)

    /** Incremented every time the StandBy activity reaches RESUMED (used to verify launches). */
    val resumeCount: StateFlow<Int> = _resumeCount.asStateFlow()

    fun markResumed() {
        _resumeCount.value = _resumeCount.value + 1
    }

    private val _exitRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when something outside the StandBy surface (e.g. the tile) asks it to close. */
    val exitRequests: SharedFlow<Unit> = _exitRequests

    fun report(surface: StandBySurface, state: StandByState) {
        _lastState.value = state
        _showing.value = if (state == StandByState.ACTIVE) surface else _showing.value.takeIf { it != surface }
    }

    fun ended(surface: StandBySurface) {
        if (_showing.value == surface) _showing.value = null
        _lastState.value = null
    }

    fun requestExit() {
        _exitRequests.tryEmit(Unit)
    }
}
