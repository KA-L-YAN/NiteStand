package com.nitestand.standbyclock.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeatherProviderTest {

    @Test
    fun `placeholder data is flagged as sample and deterministic`() {
        val provider = LocalPlaceholderWeatherProvider()
        val a = provider.sample(LocalDate.of(2026, 7, 1), LocalTime.of(15, 0))
        val b = provider.sample(LocalDate.of(2026, 7, 1), LocalTime.of(15, 0))
        assertEquals(a, b)
        assertTrue(a.isSample)
        assertTrue(a.lowCelsius <= a.highCelsius)
    }

    @Test
    fun `WMO codes map to conditions`() {
        assertEquals(WeatherCondition.CLEAR, OpenMeteoParser.condition(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, OpenMeteoParser.condition(2))
        assertEquals(WeatherCondition.CLOUDY, OpenMeteoParser.condition(3))
        assertEquals(WeatherCondition.FOG, OpenMeteoParser.condition(45))
        assertEquals(WeatherCondition.DRIZZLE, OpenMeteoParser.condition(53))
        assertEquals(WeatherCondition.RAIN, OpenMeteoParser.condition(63))
        assertEquals(WeatherCondition.RAIN, OpenMeteoParser.condition(81))
        assertEquals(WeatherCondition.SNOW, OpenMeteoParser.condition(75))
        assertEquals(WeatherCondition.THUNDERSTORM, OpenMeteoParser.condition(95))
    }

    @Test
    fun `coordinates are rounded to about a kilometre before leaving the phone`() {
        val url = OpenMeteoClient.forecastUrl(17.385044, 78.486671)
        assertTrue(url, url.contains("latitude=17.39&longitude=78.49"))
        assertTrue(url, url.contains("current=temperature_2m,weather_code,is_day"))
        assertTrue(OpenMeteoClient.searchUrl("São Paulo", "en").contains("name=S%C3%A3o+Paulo"))
    }
}
