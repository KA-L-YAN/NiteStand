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
}
