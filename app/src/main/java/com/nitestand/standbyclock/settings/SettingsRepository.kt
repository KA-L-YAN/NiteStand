package com.nitestand.standbyclock.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nitestand.standbyclock.clock.ClockPalette
import com.nitestand.standbyclock.clock.ClockTheme
import com.nitestand.standbyclock.clock.DateStyle
import com.nitestand.standbyclock.clock.HourFormat
import com.nitestand.standbyclock.display.BrightnessLevel
import com.nitestand.standbyclock.display.NightMode
import com.nitestand.standbyclock.sensors.OrientationSensitivity
import com.nitestand.standbyclock.standby.ActivationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists [StandBySettings] in Jetpack DataStore (Preferences). */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<StandBySettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::read)
        .distinctUntilChanged()

    suspend fun current(): StandBySettings = settings.first()

    /** Atomically reads, transforms and writes the settings. */
    suspend fun update(transform: (StandBySettings) -> StandBySettings) {
        dataStore.edit { prefs ->
            val updated = transform(read(prefs)).sanitized()
            write(prefs, updated)
        }
    }

    private fun StandBySettings.sanitized(): StandBySettings = copy(
        nightStartMinutes = nightStartMinutes.coerceIn(0, MINUTES_PER_DAY - 1),
        nightEndMinutes = nightEndMinutes.coerceIn(0, MINUTES_PER_DAY - 1),
        burnInIntervalSeconds = burnInIntervalSeconds.coerceIn(15, 600),
        batteryTimeoutMinutes = batteryTimeoutMinutes.coerceIn(0, 24 * 60),
    )

    internal companion object Keys {
        private const val MINUTES_PER_DAY = 24 * 60

        val ENABLED = booleanPreferencesKey("standby_enabled")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        val ACTIVATION_MODE = stringPreferencesKey("activation_mode")
        val SENSITIVITY = stringPreferencesKey("orientation_sensitivity")
        val LOCK_SCREEN_WATCHER = booleanPreferencesKey("lock_screen_watcher")
        val BATTERY_TIMEOUT = intPreferencesKey("battery_timeout_minutes")
        val CLOCK_THEME = stringPreferencesKey("clock_theme")
        val HOUR_FORMAT = stringPreferencesKey("hour_format")
        val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_AM_PM = booleanPreferencesKey("show_am_pm")
        val DATE_STYLE = stringPreferencesKey("date_style")
        val WORLD_PRIMARY = stringPreferencesKey("world_primary_city")
        val WORLD_SECONDARY = stringPreferencesKey("world_secondary_city")
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        const val CURRENT_SCHEMA = 2

        fun faceColorKey(theme: ClockTheme) = stringPreferencesKey("face_color_${theme.name}")
        val SHOW_PANEL = booleanPreferencesKey("show_panel")
        val SHOW_INFO_PAGE = booleanPreferencesKey("show_info_page")
        val WIDGET_DATE = booleanPreferencesKey("widget_date")
        val WIDGET_BATTERY = booleanPreferencesKey("widget_battery")
        val WIDGET_ALARM = booleanPreferencesKey("widget_alarm")
        val WIDGET_WEATHER = booleanPreferencesKey("widget_weather")
        val WEATHER_MODE = stringPreferencesKey("weather_location_mode")
        val WEATHER_PLACE = stringPreferencesKey("weather_place_name")
        val WEATHER_LAT = doublePreferencesKey("weather_latitude")
        val WEATHER_LON = doublePreferencesKey("weather_longitude")
        val BRIGHTNESS = stringPreferencesKey("brightness")
        val NIGHT_MODE = stringPreferencesKey("night_mode")
        val NIGHT_START = intPreferencesKey("night_start_minutes")
        val NIGHT_END = intPreferencesKey("night_end_minutes")
        val ACCENT = stringPreferencesKey("accent")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val BURN_IN = booleanPreferencesKey("burn_in_protection")
        val BURN_IN_INTERVAL = intPreferencesKey("burn_in_interval_seconds")

        private inline fun <reified T : Enum<T>> Preferences.enumOf(key: Preferences.Key<String>, default: T): T =
            this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

        fun read(prefs: Preferences): StandBySettings {
            val d = StandBySettings()
            return StandBySettings(
                standByEnabled = prefs[ENABLED] ?: d.standByEnabled,
                autoStart = prefs[AUTO_START] ?: d.autoStart,
                showOnLockScreen = prefs[LOCK_SCREEN] ?: d.showOnLockScreen,
                activationMode = prefs.enumOf(ACTIVATION_MODE, d.activationMode),
                orientationSensitivity = prefs.enumOf(SENSITIVITY, d.orientationSensitivity),
                lockScreenWatcher = prefs[LOCK_SCREEN_WATCHER] ?: d.lockScreenWatcher,
                batteryTimeoutMinutes = prefs[BATTERY_TIMEOUT] ?: d.batteryTimeoutMinutes,
                clockTheme = prefs.enumOf(CLOCK_THEME, d.clockTheme),
                hourFormat = prefs.enumOf(HOUR_FORMAT, d.hourFormat),
                showSeconds = prefs[SHOW_SECONDS] ?: d.showSeconds,
                showDate = prefs[SHOW_DATE] ?: d.showDate,
                showAmPm = prefs[SHOW_AM_PM] ?: d.showAmPm,
                dateStyle = prefs.enumOf(DATE_STYLE, d.dateStyle),
                faceColors = ClockTheme.entries.mapNotNull { theme ->
                    prefs[faceColorKey(theme)]
                        ?.let { name -> ClockPalette.entries.firstOrNull { it.name == name } }
                        ?.let { theme to it }
                }.toMap(),
                worldPrimaryCity = prefs[WORLD_PRIMARY] ?: d.worldPrimaryCity,
                worldSecondaryCity = prefs[WORLD_SECONDARY] ?: d.worldSecondaryCity,
                showPanel = prefs[SHOW_PANEL] ?: d.showPanel,
                showInfoPage = prefs[SHOW_INFO_PAGE] ?: d.showInfoPage,
                showDateWidget = prefs[WIDGET_DATE] ?: d.showDateWidget,
                showBatteryWidget = prefs[WIDGET_BATTERY] ?: d.showBatteryWidget,
                showAlarmWidget = prefs[WIDGET_ALARM] ?: d.showAlarmWidget,
                showWeatherWidget = prefs[WIDGET_WEATHER] ?: d.showWeatherWidget,
                weatherLocationMode = prefs.enumOf(WEATHER_MODE, d.weatherLocationMode),
                weatherPlaceName = prefs[WEATHER_PLACE] ?: d.weatherPlaceName,
                weatherLatitude = prefs[WEATHER_LAT],
                weatherLongitude = prefs[WEATHER_LON],
                brightness = prefs.enumOf(BRIGHTNESS, d.brightness),
                nightMode = prefs.enumOf(NIGHT_MODE, d.nightMode),
                nightStartMinutes = prefs[NIGHT_START] ?: d.nightStartMinutes,
                nightEndMinutes = prefs[NIGHT_END] ?: d.nightEndMinutes,
                accent = prefs.enumOf(ACCENT, d.accent),
                reduceMotion = prefs[REDUCE_MOTION] ?: d.reduceMotion,
                burnInProtection = prefs[BURN_IN] ?: d.burnInProtection,
                burnInIntervalSeconds = prefs[BURN_IN_INTERVAL] ?: d.burnInIntervalSeconds,
            )
        }

        fun write(prefs: MutablePreferences, s: StandBySettings) {
            prefs[ENABLED] = s.standByEnabled
            prefs[AUTO_START] = s.autoStart
            prefs[LOCK_SCREEN] = s.showOnLockScreen
            prefs[ACTIVATION_MODE] = s.activationMode.name
            prefs[SENSITIVITY] = s.orientationSensitivity.name
            prefs[LOCK_SCREEN_WATCHER] = s.lockScreenWatcher
            prefs[BATTERY_TIMEOUT] = s.batteryTimeoutMinutes
            prefs[CLOCK_THEME] = s.clockTheme.name
            prefs[HOUR_FORMAT] = s.hourFormat.name
            prefs[SHOW_SECONDS] = s.showSeconds
            prefs[SHOW_DATE] = s.showDate
            prefs[SHOW_AM_PM] = s.showAmPm
            prefs[DATE_STYLE] = s.dateStyle.name
            prefs[WORLD_PRIMARY] = s.worldPrimaryCity
            prefs[WORLD_SECONDARY] = s.worldSecondaryCity
            ClockTheme.entries.forEach { theme ->
                val color = s.faceColors[theme]
                if (color == null) prefs.remove(faceColorKey(theme)) else prefs[faceColorKey(theme)] = color.name
            }
            prefs[SCHEMA_VERSION] = CURRENT_SCHEMA
            prefs[SHOW_PANEL] = s.showPanel
            prefs[SHOW_INFO_PAGE] = s.showInfoPage
            prefs[WIDGET_DATE] = s.showDateWidget
            prefs[WIDGET_BATTERY] = s.showBatteryWidget
            prefs[WIDGET_ALARM] = s.showAlarmWidget
            prefs[WIDGET_WEATHER] = s.showWeatherWidget
            prefs[WEATHER_MODE] = s.weatherLocationMode.name
            prefs[WEATHER_PLACE] = s.weatherPlaceName
            if (s.weatherLatitude == null) prefs.remove(WEATHER_LAT) else prefs[WEATHER_LAT] = s.weatherLatitude
            if (s.weatherLongitude == null) prefs.remove(WEATHER_LON) else prefs[WEATHER_LON] = s.weatherLongitude
            prefs[BRIGHTNESS] = s.brightness.name
            prefs[NIGHT_MODE] = s.nightMode.name
            prefs[NIGHT_START] = s.nightStartMinutes
            prefs[NIGHT_END] = s.nightEndMinutes
            prefs[ACCENT] = s.accent.name
            prefs[REDUCE_MOTION] = s.reduceMotion
            prefs[BURN_IN] = s.burnInProtection
            prefs[BURN_IN_INTERVAL] = s.burnInIntervalSeconds
        }
    }
}
