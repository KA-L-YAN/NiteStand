package com.nitestand.standbyclock.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

/**
 * Event-driven battery state. A single dynamically registered receiver listens for
 * ACTION_BATTERY_CHANGED (sticky, so the current value arrives immediately),
 * ACTION_POWER_CONNECTED and ACTION_POWER_DISCONNECTED. Nothing is polled; the receiver is
 * unregistered as soon as no screen observes the flow.
 */
class BatteryRepository(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext

    val battery: Flow<BatterySnapshot> = callbackFlow {
        var last: BatterySnapshot? = null
        fun publish(snapshot: BatterySnapshot) {
            last = snapshot
            trySend(snapshot)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> publish(parse(intent))
                    Intent.ACTION_POWER_CONNECTED -> publish(BatteryStateParser.applyPowerHint(last, connected = true))
                    Intent.ACTION_POWER_DISCONNECTED -> publish(BatteryStateParser.applyPowerHint(last, connected = false))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // System broadcasts are delivered to non-exported receivers.
        val sticky = ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        sticky?.let { publish(parse(it)) }
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 2_000), replay = 1)

    private fun parse(intent: Intent): BatterySnapshot = BatteryStateParser.parse(
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
    )
}
