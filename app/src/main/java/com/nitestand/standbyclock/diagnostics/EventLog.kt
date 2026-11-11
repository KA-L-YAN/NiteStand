package com.nitestand.standbyclock.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Small in-memory log of StandBy decisions (last [CAPACITY] entries), shown on the diagnostics
 * screen and mirrored to logcat under the tag "NiteStand" (`adb logcat -s NiteStand`).
 */
object EventLog {
    const val TAG = "NiteStand"
    private const val CAPACITY = 80
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun log(message: String) {
        runCatching { Log.i(TAG, message) }
        val line = "${LocalTime.now().format(timeFormat)}  $message"
        _entries.update { (it + line).takeLast(CAPACITY) }
    }
}
