package com.nitestand.standbyclock.weather

import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.settings.WeatherLocationMode

/** The weather request implied by the settings. */
fun StandBySettings.weatherRequest(): WeatherRequest {
    val lat = weatherLatitude
    val lon = weatherLongitude
    return when {
        weatherLocationMode == WeatherLocationMode.DEVICE -> WeatherRequest.DeviceLocation
        lat != null && lon != null -> WeatherRequest.City(weatherPlaceName, lat, lon)
        else -> WeatherRequest.None
    }
}
