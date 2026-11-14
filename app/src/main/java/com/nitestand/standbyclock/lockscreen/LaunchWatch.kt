package com.nitestand.standbyclock.lockscreen

/** Outcome of watching an automatic launch. */
enum class LaunchVerdict { WAITING, CONFIRMED, USER_WOKE_PHONE, TIMED_OUT }

/**
 * Decides, while a launch is in flight, whether StandBy arrived, the user woke the phone
 * themselves (then the launch must be abandoned — never pop StandBy over a phone in use), or it
 * timed out. Pure, unit tested.
 *
 * StandBy's own launch turns the screen on a moment before the activity resumes, so a screen
 * that is on for less than [ownWakeGraceMs] without StandBy is still assumed to be ours.
 */
object LaunchWatch {
    fun verdict(
        resumed: Boolean,
        screenOnForMs: Long?,
        keyguardLocked: Boolean,
        elapsedMs: Long,
        timeoutMs: Long,
        ownWakeGraceMs: Long = 1_200,
    ): LaunchVerdict = when {
        resumed -> LaunchVerdict.CONFIRMED
        // Unlocked (fingerprint/PIN) while the screen is on: the user is using the phone.
        screenOnForMs != null && !keyguardLocked -> LaunchVerdict.USER_WOKE_PHONE
        screenOnForMs != null && screenOnForMs >= ownWakeGraceMs -> LaunchVerdict.USER_WOKE_PHONE
        elapsedMs >= timeoutMs -> LaunchVerdict.TIMED_OUT
        else -> LaunchVerdict.WAITING
    }
}
