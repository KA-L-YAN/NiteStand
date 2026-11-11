package com.nitestand.standbyclock.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Step-by-step record of the most recent StandBy activation attempt (automatic or manual), shown
 * in Diagnostics › Last activation attempt and mirrored to logcat ("NiteStand").
 */
object ActivationTrace {
    data class Step(val time: String, val text: String)

    private val format = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val _steps = MutableStateFlow<List<Step>>(emptyList())
    val steps: StateFlow<List<Step>> = _steps.asStateFlow()

    /** Starts a new attempt, discarding the previous one. */
    fun begin(title: String) {
        _steps.value = emptyList()
        step(title)
    }

    fun step(text: String) {
        EventLog.log("Activation: $text")
        _steps.update { (it + Step(LocalTime.now().format(format), text)).takeLast(40) }
    }

    /** Adds [text] only if it is not already the most recent step (idempotent markers). */
    fun stepOnce(text: String) {
        if (_steps.value.none { it.text == text }) step(text)
    }
}
