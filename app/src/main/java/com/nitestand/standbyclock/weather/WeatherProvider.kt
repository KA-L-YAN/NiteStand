package com.nitestand.standbyclock.weather

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sin

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM }

data class WeatherReport(
    val temperatureCelsius: Double,
    val highCelsius: Double,
    val lowCelsius: Double,
    val condition: WeatherCondition,
    /** True when the data is illustrative and not a real forecast. Shown to the user. */
    val isSample: Boolean,
    /** Place the report is for ("Hyderabad"); null when unknown. */
    val placeName: String? = null,
    /** When the provider received the data (epoch ms). */
    val fetchedAtMillis: Long = 0L,
    val isDay: Boolean = true,
)

/** Where to get weather for. Built from settings by the weather widget. */
sealed interface WeatherRequest {
    /** No location chosen yet. */
    data object None : WeatherRequest

    /** A city the user picked (coordinates from Open-Meteo geocoding). */
    data class City(val name: String, val latitude: Double, val longitude: Double) : WeatherRequest

    /** The phone's approximate (city-level) location. */
    data object DeviceLocation : WeatherRequest
}

/** What the weather widget shows. */
data class WeatherState(val report: WeatherReport?, val status: Status) {
    enum class Status {
        /** [report] is current. */
        OK,

        /** Fetching for the first time. */
        LOADING,

        /** No city chosen and location not enabled. */
        NO_LOCATION,

        /** "Approximate location" chosen but the permission is not granted. */
        NO_PERMISSION,

        /** Network/service error; [report] may hold the last good data (shown while < 3 h old). */
        ERROR,
    }
}

/**
 * Weather source used by the weather widget. The real implementation is
 * [OpenMeteoWeatherProvider]; [LocalPlaceholderWeatherProvider] is used by tests and previews.
 */
interface WeatherProvider {
    /** Stream of weather for [request]; collected only while the weather widget is on screen. */
    fun observe(request: WeatherRequest): Flow<WeatherState>
}

/**
 * Offline placeholder for tests and screenshots: deterministic sample values, flagged
 * [WeatherReport.isSample]. Never used by the app itself since 2.3.0.
 */
class LocalPlaceholderWeatherProvider(
    private val today: () -> LocalDate = LocalDate::now,
    private val timeOfDay: () -> LocalTime = LocalTime::now,
    private val refreshMillis: Long = 30 * 60_000L,
) : WeatherProvider {

    override fun observe(request: WeatherRequest): Flow<WeatherState> = flow {
        while (true) {
            emit(WeatherState(sample(today(), timeOfDay()), WeatherState.Status.OK))
            delay(refreshMillis)
        }
    }

    fun sample(date: LocalDate, time: LocalTime): WeatherReport {
        val seasonal = 12.0 + 9.0 * sin((date.dayOfYear - 110) / 365.0 * 2 * Math.PI)
        val daily = 4.0 * sin((time.toSecondOfDay() / 86_400.0 - 0.375) * 2 * Math.PI)
        val conditions = listOf(
            WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.CLOUDY,
            WeatherCondition.RAIN, WeatherCondition.SNOW, WeatherCondition.FOG,
        )
        val condition = conditions[Math.floorMod(date.dayOfYear * 7 + date.year, conditions.size)]
        val adjusted = if (condition == WeatherCondition.SNOW && seasonal > 6) WeatherCondition.CLOUDY else condition
        return WeatherReport(
            temperatureCelsius = ((seasonal + daily) * 10).roundToInt() / 10.0,
            highCelsius = (seasonal + 4.0).roundToInt().toDouble(),
            lowCelsius = (seasonal - 4.0).roundToInt().toDouble(),
            condition = adjusted,
            isSample = true,
            placeName = "Sample",
        )
    }
}
