package com.nitestand.standbyclock.weather

import com.nitestand.standbyclock.settings.StandBySettings
import com.nitestand.standbyclock.settings.WeatherLocationMode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Robolectric provides the real org.json used by the parser. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenMeteoTest {

    // Shape of https://api.open-meteo.com/v1/forecast?...&current=temperature_2m,weather_code,is_day&daily=...
    private val forecastJson = """
        {"latitude":17.375,"longitude":78.5,"generationtime_ms":0.05,"utc_offset_seconds":19800,
         "timezone":"Asia/Kolkata","timezone_abbreviation":"GMT+5:30","elevation":536.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","is_day":""},
         "current":{"time":"2026-11-13T17:15","interval":900,"temperature_2m":27.4,"weather_code":2,"is_day":1},
         "daily_units":{"time":"iso8601","temperature_2m_max":"°C","temperature_2m_min":"°C"},
         "daily":{"time":["2026-11-13"],"temperature_2m_max":[30.1],"temperature_2m_min":[18.6]}}
    """.trimIndent()

    // Shape of https://geocoding-api.open-meteo.com/v1/search?name=Hyderabad
    private val searchJson = """
        {"results":[
          {"id":1269843,"name":"Hyderabad","latitude":17.38405,"longitude":78.45636,"elevation":505.0,
           "feature_code":"PPLA","country_code":"IN","timezone":"Asia/Kolkata","population":3597816,
           "country_id":1269750,"country":"India","admin1":"Telangana"},
          {"id":1176734,"name":"Hyderabad","latitude":25.39242,"longitude":68.37366,
           "country_code":"PK","timezone":"Asia/Karachi","country":"Pakistan","admin1":"Sindh"}],
         "generationtime_ms":0.6}
    """.trimIndent()

    @Test
    fun `parses current weather and today's range`() {
        val r = OpenMeteoParser.forecast(forecastJson, "Hyderabad", nowMillis = 1_000L)
        assertEquals(27.4, r.temperatureCelsius, 0.0)
        assertEquals(30.1, r.highCelsius, 0.0)
        assertEquals(18.6, r.lowCelsius, 0.0)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, r.condition)
        assertFalse(r.isSample)
        assertEquals("Hyderabad", r.placeName)
        assertEquals(1_000L, r.fetchedAtMillis)
    }

    @Test
    fun `parses city search results and handles no match`() {
        val places = OpenMeteoParser.places(searchJson)
        assertEquals(2, places.size)
        assertEquals("Hyderabad, Telangana, India", places[0].label)
        assertEquals(17.38405, places[0].latitude, 0.0)
        assertEquals(emptyList<WeatherPlace>(), OpenMeteoParser.places("""{"generationtime_ms":0.3}"""))
    }

    private class FakeClient(val body: () -> String) : OpenMeteoClient() {
        var calls = 0
        override fun forecast(latitude: Double, longitude: Double): String {
            calls++
            return body()
        }
    }

    @Test
    fun `no location set asks the user to choose one`() = runTest {
        val provider = OpenMeteoWeatherProvider(null, FakeClient { forecastJson })
        val states = provider.observe(WeatherRequest.None).toList()
        assertEquals(listOf(WeatherState.Status.NO_LOCATION), states.map { it.status })
    }

    @Test
    fun `fetches once and reuses the report across StandBy sessions`() = runTest {
        var clock = 0L
        val client = FakeClient { forecastJson }
        val provider = OpenMeteoWeatherProvider(null, client, now = { clock })
        val city = WeatherRequest.City("Hyderabad", 17.38, 78.46)
        val first = provider.observe(city).take(2).toList()
        assertEquals(listOf(WeatherState.Status.LOADING, WeatherState.Status.OK), first.map { it.status })
        // StandBy reopened 10 minutes later: cached report shown at once, no new request.
        clock += 10 * 60_000L
        val second = provider.observe(city).take(1).toList()
        assertEquals(WeatherState.Status.OK, second.single().status)
        assertEquals(1, client.calls)
    }

    @Test
    fun `network failure keeps the last good report while it is recent`() = runTest {
        var clock = 0L
        var fail = false
        val provider = OpenMeteoWeatherProvider(null, FakeClient { if (fail) throw IOException("offline") else forecastJson }, now = { clock })
        val city = WeatherRequest.City("Hyderabad", 17.38, 78.46)
        provider.observe(city).take(2).toList()
        fail = true
        clock += 40 * 60_000L // past the refresh interval
        val states = provider.observe(city).take(2).toList()
        assertEquals(WeatherState.Status.ERROR, states[1].status)
        assertEquals(27.4, states[1].report!!.temperatureCelsius, 0.0)
        clock += 4 * 60 * 60_000L // older than 3 h: no longer shown
        assertNull(provider.observe(city).take(2).toList()[1].report)
    }

    @Test
    fun `device location without permission reports it`() = runTest {
        val provider = OpenMeteoWeatherProvider(null, FakeClient { forecastJson })
        val states = provider.observe(WeatherRequest.DeviceLocation).toList()
        assertEquals(WeatherState.Status.NO_PERMISSION, states.last().status)
    }

    @Test
    fun `settings map to weather requests`() {
        assertEquals(WeatherRequest.None, StandBySettings().weatherRequest())
        assertEquals(
            WeatherRequest.City("Hyderabad", 17.38, 78.46),
            StandBySettings(weatherPlaceName = "Hyderabad", weatherLatitude = 17.38, weatherLongitude = 78.46).weatherRequest(),
        )
        assertEquals(WeatherRequest.DeviceLocation, StandBySettings(weatherLocationMode = WeatherLocationMode.DEVICE).weatherRequest())
    }
}
