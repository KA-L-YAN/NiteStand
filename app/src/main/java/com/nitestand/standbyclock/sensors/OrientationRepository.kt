package com.nitestand.standbyclock.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Source of physical orientation. Abstracted so the controller can be tested with fakes. */
fun interface OrientationSource {
    fun observe(config: OrientationConfig): Flow<DeviceOrientation>
}

/**
 * Listens to the gravity sensor (falling back to a low-pass-filtered accelerometer) only
 * while the returned flow is collected — i.e. only while a StandBy surface is visible.
 * Sampling is 5 Hz with batching allowed, which is plenty for detecting how a phone sits on a stand.
 */
class OrientationRepository(context: Context) : OrientationSource {

    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)

    val hasSensor: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    override fun observe(config: OrientationConfig): Flow<DeviceOrientation> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            trySend(DeviceOrientation.UNAVAILABLE)
            awaitClose { }
            return@callbackFlow
        }

        val classifier = OrientationClassifier(config)
        val filter = if (sensor.type == Sensor.TYPE_ACCELEROMETER) LowPassFilter() else null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = filter?.apply(event.values) ?: event.values
                trySend(classifier.update(values[0], values[1], values[2], SystemClock.elapsedRealtime()))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SAMPLING_PERIOD_US, MAX_REPORT_LATENCY_US)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()

    /** Isolates gravity from raw accelerometer readings. */
    private class LowPassFilter(private val alpha: Float = 0.8f) {
        private val gravity = FloatArray(3)
        private var initialised = false

        fun apply(values: FloatArray): FloatArray {
            if (!initialised) {
                values.copyInto(gravity, endIndex = 3)
                initialised = true
            } else {
                for (i in 0..2) gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
            }
            return gravity
        }
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 200_000
        const val MAX_REPORT_LATENCY_US = 400_000
    }
}
