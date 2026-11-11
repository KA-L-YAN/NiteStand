package com.nitestand.standbyclock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nitestand.standbyclock.charging.BatterySnapshot
import com.nitestand.standbyclock.di.AppContainer
import com.nitestand.standbyclock.settings.StandBySettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the setup and settings screens. */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<StandBySettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val battery: StateFlow<BatterySnapshot?> = container.batteryRepository.battery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasOrientationSensor: Boolean get() = container.orientationRepository.hasSensor

    fun update(transform: (StandBySettings) -> StandBySettings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}
