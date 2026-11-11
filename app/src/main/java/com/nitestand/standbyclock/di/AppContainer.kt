package com.nitestand.standbyclock.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nitestand.standbyclock.alarm.AlarmRepository
import com.nitestand.standbyclock.charging.BatteryRepository
import com.nitestand.standbyclock.clock.ClockTicker
import com.nitestand.standbyclock.clock.TimeFormatPreference
import com.nitestand.standbyclock.sensors.OrientationRepository
import com.nitestand.standbyclock.settings.SettingsRepository
import com.nitestand.standbyclock.weather.LocalPlaceholderWeatherProvider
import com.nitestand.standbyclock.weather.WeatherProvider
import com.nitestand.standbyclock.widgets.WidgetDataSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Everything here is lazy and idle until a screen collects it:
 * the shared flows only register receivers / sensors while observed.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-wide scope for sharing flows; it never runs work on its own. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { appContext.preferencesDataStoreFile("standby_settings") },
            ),
        )
    }

    val batteryRepository: BatteryRepository by lazy { BatteryRepository(appContext, applicationScope) }
    val orientationRepository: OrientationRepository by lazy { OrientationRepository(appContext) }
    val alarmRepository: AlarmRepository by lazy { AlarmRepository(appContext, applicationScope) }
    val clockTicker: ClockTicker by lazy { ClockTicker(appContext) }
    val timeFormat: TimeFormatPreference by lazy { TimeFormatPreference(appContext) }

    /** Replace with a networked implementation to show real weather. */
    val weatherProvider: WeatherProvider by lazy { LocalPlaceholderWeatherProvider() }

    val widgetDataSources: WidgetDataSources by lazy {
        WidgetDataSources(
            battery = batteryRepository.battery,
            nextAlarm = alarmRepository.nextAlarm,
            weather = weatherProvider,
            ticker = clockTicker,
            timeFormat = timeFormat,
        )
    }
}
