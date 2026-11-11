package com.nitestand.standbyclock.standby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nitestand.standbyclock.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class StandByViewModel(container: AppContainer, forceActive: Boolean) : ViewModel() {

    private val controller = createController(container, viewModelScope, forceActive, dream = false)

    val uiState: StateFlow<StandByUiState?> = controller.uiState

    fun start(force: Boolean = false) = controller.requestStart(force)
    fun exit() = controller.requestExit()
    fun cycleTheme(delta: Int) = controller.cycleTheme(delta)

    companion object {
        fun factory(container: AppContainer, forceActive: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer { StandByViewModel(container, forceActive) }
        }

        fun createController(
            container: AppContainer,
            scope: CoroutineScope,
            forceActive: Boolean,
            dream: Boolean,
        ) = StandByController(
            settingsRepository = container.settingsRepository,
            battery = container.batteryRepository.battery,
            orientationSource = container.orientationRepository,
            minuteTicks = StandByController.minuteTicks(container.clockTicker),
            scope = scope,
            forceActive = forceActive,
            dream = dream,
        )
    }
}
