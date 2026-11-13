package com.nitestand.standbyclock.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nitestand.standbyclock.diagnostics.EventLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Coordinates for a fetch. */
data class WeatherFix(val latitude: Double, val longitude: Double, val name: String?)

/**
 * Real weather from Open-Meteo.
 *
 * Battery and data use: the flow only runs while the weather widget is on screen. A report is
 * reused for [REFRESH_MS] (also across StandBy sessions — no fetch every time StandBy opens);
 * failures retry after [RETRY_MS]; the last good report stays visible for up to [STALE_MS].
 * Device location uses the last known coarse fix (no GPS, no continuous updates).
 */
class OpenMeteoWeatherProvider(
    private val context: Context?,
    private val client: OpenMeteoClient = OpenMeteoClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val locate: (suspend () -> WeatherFix?)? = null,
) : WeatherProvider {

    private val cache = HashMap<WeatherRequest, WeatherReport>()

    private val _lastStatus = MutableStateFlow<String>("Not requested yet")

    /** Human-readable status of the last fetch, for Diagnostics. */
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    override fun observe(request: WeatherRequest): Flow<WeatherState> = flow {
        if (request == WeatherRequest.None) {
            emit(WeatherState(null, WeatherState.Status.NO_LOCATION))
            return@flow
        }
        cache[request]?.let { emit(WeatherState(it, WeatherState.Status.OK)) } ?: emit(WeatherState(null, WeatherState.Status.LOADING))
        while (true) {
            val cached = cache[request]
            val age = cached?.let { now() - it.fetchedAtMillis }
            if (cached != null && age != null && age < REFRESH_MS) {
                delay(REFRESH_MS - age)
                continue
            }
            if (request == WeatherRequest.DeviceLocation && !hasLocationPermission()) {
                emit(WeatherState(null, WeatherState.Status.NO_PERMISSION))
                _lastStatus.value = "Location permission not granted"
                return@flow
            }
            val result = runCatching {
                val fix = when (request) {
                    is WeatherRequest.City -> WeatherFix(request.latitude, request.longitude, request.name)
                    WeatherRequest.DeviceLocation -> (locate ?: ::lastKnownFix)() ?: error("No approximate location available yet")
                    WeatherRequest.None -> error("unreachable")
                }
                OpenMeteoParser.forecast(client.forecast(fix.latitude, fix.longitude), fix.name, now())
            }
            result.onSuccess { report ->
                cache[request] = report
                _lastStatus.value = "OK: ${report.temperatureCelsius}°C ${report.condition} for ${report.placeName ?: "current location"}"
                emit(WeatherState(report, WeatherState.Status.OK))
                delay(REFRESH_MS)
            }.onFailure { error ->
                _lastStatus.value = "Failed: ${error.javaClass.simpleName}: ${error.message}"
                runCatching { EventLog.log("Weather: ${_lastStatus.value}") }
                val stillUsable = cached?.takeIf { now() - it.fetchedAtMillis < STALE_MS }
                emit(WeatherState(stillUsable, WeatherState.Status.ERROR))
                delay(RETRY_MS)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun hasLocationPermission(): Boolean = context != null &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Coarse location without GPS or continuous updates: the freshest last-known fix from the
     * network/fused/passive providers, else one current coarse fix (Android 11+).
     */
    @Suppress("MissingPermission")
    private suspend fun lastKnownFix(): WeatherFix? {
        val ctx = context ?: return null
        if (!hasLocationPermission()) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }.getOrDefault(false) }
        val best = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        val location = best ?: currentFix(lm, providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER })
        return location?.let { WeatherFix(it.latitude, it.longitude, null) }
    }

    @Suppress("MissingPermission")
    private suspend fun currentFix(lm: LocationManager, provider: String?): Location? {
        if (provider == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val ctx = context ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { cont.resume(it) }
            }
        }
    }

    companion object {
        const val REFRESH_MS = 30 * 60_000L
        const val RETRY_MS = 10 * 60_000L
        const val STALE_MS = 3 * 60 * 60_000L
    }
}
