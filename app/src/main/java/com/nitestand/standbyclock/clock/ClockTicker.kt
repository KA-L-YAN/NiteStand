package com.nitestand.standbyclock.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TickInterval(val millis: Long) { SECOND(1_000L), MINUTE(60_000L) }

/**
 * Emits the current time once, then exactly at each minute (or second) boundary. Between
 * emissions the coroutine is suspended in a single timed delay — no busy loop and no
 * sub-second polling. Clock or time-zone changes trigger an immediate re-emission.
 * Collection stops (and the receiver is unregistered) as soon as the UI stops observing.
 */
class ClockTicker(
    private val context: Context?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    fun ticks(interval: TickInterval): Flow<ZonedDateTime> = callbackFlow {
        fun now(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis()), ZoneId.systemDefault())

        val appContext = context?.applicationContext
        val receiver = appContext?.let { ctx ->
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(now())
                }
            }.also { r ->
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(ctx, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        }
        val job = launch {
            while (isActive) {
                send(now())
                delay(millisUntilNextBoundary(nowMillis(), interval))
            }
        }
        awaitClose {
            job.cancel()
            if (receiver != null) appContext.unregisterReceiver(receiver)
        }
    }.conflate()

    companion object {
        /** Small margin so the emission lands just after the boundary, never just before. */
        private const val BOUNDARY_MARGIN_MS = 15L

        fun millisUntilNextBoundary(epochMillis: Long, interval: TickInterval): Long {
            val remainder = Math.floorMod(epochMillis, interval.millis)
            return interval.millis - remainder + BOUNDARY_MARGIN_MS
        }
    }
}
