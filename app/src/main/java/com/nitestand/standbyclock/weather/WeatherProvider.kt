package com.nitestand.standbyclock.weather

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sin

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, FOG }

data class WeatherReport(
    val temperatureCelsius: Double,
    val highCelsius: Double,
    val lowCelsius: Double,
    val condition: WeatherCondition,
    /** True when the data is illustrative and not a real forecast. Shown to the user. */
    val isSample: Boolean,
)

/**
 * Weather source used by the weather widget. A networked implementation (e.g. a forecast API
 * plus coarse location) can be swapped in through the app container without touching the UI.
 */
interface WeatherProvider {
    /** Hot or cold stream of reports; null when no data is available. */
    fun observe(): Flow<WeatherReport?>
}

/**
 * Offline placeholder: produces plausible, deterministic sample values that drift slowly over
 * the day. It never touches the network or location, and every report is flagged [WeatherReport.isSample]
 * so the widget labels it as sample data instead of pretending to be a forecast.
 */
class LocalPlaceholderWeatherProvider(
    private val today: () -> LocalDate = LocalDate::now,
    private val timeOfDay: () -> LocalTime = LocalTime::now,
    private val refreshMillis: Long = 30 * 60_000L,
) : WeatherProvider {

    override fun observe(): Flow<WeatherReport?> = flow {
        while (true) {
            emit(sample(today(), timeOfDay()))
            delay(refreshMillis)
        }
    }

    fun sample(date: LocalDate, time: LocalTime): WeatherReport {
        val seasonal = 12.0 + 9.0 * sin((date.dayOfYear - 110) / 365.0 * 2 * Math.PI)
        val daily = 4.0 * sin((time.toSecondOfDay() / 86_400.0 - 0.375) * 2 * Math.PI)
        val conditions = WeatherCondition.entries
        val condition = conditions[Math.floorMod(date.dayOfYear * 7 + date.year, conditions.size)]
        val adjusted = if (condition == WeatherCondition.SNOW && seasonal > 6) WeatherCondition.CLOUDY else condition
        return WeatherReport(
            temperatureCelsius = ((seasonal + daily) * 10).roundToInt() / 10.0,
            highCelsius = (seasonal + 4.0).roundToInt().toDouble(),
            lowCelsius = (seasonal - 4.0).roundToInt().toDouble(),
            condition = adjusted,
            isSample = true,
        )
    }
}
