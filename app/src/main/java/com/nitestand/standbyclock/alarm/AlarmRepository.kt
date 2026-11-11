package com.nitestand.standbyclock.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import java.time.Instant

/** The next user-visible alarm, as published by the system. */
data class NextAlarm(val triggerAt: Instant)

/**
 * Reads AlarmManager.getNextAlarmClock(), which needs no permission and covers alarms set
 * through AlarmManager.setAlarmClock() — what Google Clock, Samsung Clock and most alarm apps
 * use. Updates arrive through ACTION_NEXT_ALARM_CLOCK_CHANGED; nothing is polled.
 */
class AlarmRepository(context: Context, scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    val nextAlarm: Flow<NextAlarm?> = callbackFlow {
        fun read(): NextAlarm? = alarmManager?.nextAlarmClock?.let { NextAlarm(Instant.ofEpochMilli(it.triggerTime)) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(read())
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 2_000), replay = 1)
}
