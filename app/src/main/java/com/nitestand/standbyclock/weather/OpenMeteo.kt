package com.nitestand.standbyclock.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToLong

/** A place returned by city search. */
data class WeatherPlace(val name: String, val region: String?, val country: String?, val latitude: Double, val longitude: Double) {
    /** "Hyderabad, Telangana, India". */
    val label: String get() = listOfNotNull(name, region?.takeIf { it != name }, country).joinToString(", ")
}

/**
 * Parsing of Open-Meteo responses (https://open-meteo.com, free, no API key; data CC BY 4.0).
 * Uses the platform org.json parser — no extra dependency.
 */
object OpenMeteoParser {

    /** WMO weather interpretation codes (as used by Open-Meteo) → condition. */
    fun condition(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.CLOUDY
    }

    /** Parses a `/v1/forecast` response requested with [OpenMeteoClient.forecastUrl]. */
    fun forecast(json: String, placeName: String?, nowMillis: Long): WeatherReport {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        val temp = current.getDouble("temperature_2m")
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp
        return WeatherReport(
            temperatureCelsius = temp,
            highCelsius = high,
            lowCelsius = low,
            condition = condition(current.optInt("weather_code", 3)),
            isSample = false,
            placeName = placeName,
            fetchedAtMillis = nowMillis,
            isDay = current.optInt("is_day", 1) == 1,
        )
    }

    /** Parses a `/v1/search` geocoding response. Missing "results" means no match. */
    fun places(json: String): List<WeatherPlace> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            WeatherPlace(
                name = r.getString("name"),
                region = r.optString("admin1").takeIf { it.isNotBlank() },
                country = r.optString("country").takeIf { it.isNotBlank() },
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }
}

/** Minimal Open-Meteo HTTP client (HttpURLConnection; call from a background dispatcher). */
open class OpenMeteoClient {

    open fun forecast(latitude: Double, longitude: Double): String = get(forecastUrl(latitude, longitude))

    open fun search(query: String, language: String = Locale.getDefault().language): String = get(searchUrl(query, language))

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NiteStand-Android")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /**
         * Coordinates are rounded to 2 decimals (~1 km) before they leave the phone: precise
         * enough for a forecast, not a precise location.
         */
        fun round(value: Double): Double = (value * 100).roundToLong() / 100.0

        fun forecastUrl(latitude: Double, longitude: Double): String =
            "https://api.open-meteo.com/v1/forecast?latitude=${round(latitude)}&longitude=${round(longitude)}" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1"

        fun searchUrl(query: String, language: String): String =
            "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
                "&count=8&language=$language&format=json"
    }
}
