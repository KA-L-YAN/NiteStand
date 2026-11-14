package com.nitestand.standbyclock.lockscreen

/**
 * "Is the phone resting on a stand?" — landscape held for [holdMs] with the roll and tilt angles
 * varying less than [maxWobbleDeg]. A stand is motionless; a phone held sideways in a hand (e.g.
 * watching a video) wobbles by a few degrees. Pure, unit tested.
 */
class StillnessDetector(private val holdMs: Long = 1_500, private val maxWobbleDeg: Float = 2.0f) {
    private data class Sample(val t: Long, val roll: Float, val tilt: Float)

    private val window = ArrayDeque<Sample>()

    /** @return true once the phone has been still in landscape for [holdMs]. */
    fun add(timeMs: Long, landscape: Boolean, rollDeg: Float, tiltDeg: Float): Boolean {
        if (!landscape) {
            window.clear()
            return false
        }
        window.addLast(Sample(timeMs, rollDeg, tiltDeg))
        // Drop everything before the most recent movement beyond the tolerance.
        while (window.size > 1 && wobble() > maxWobbleDeg) window.removeFirst()
        while (window.size > 1 && timeMs - window[1].t >= holdMs) window.removeFirst()
        return window.size > 1 && timeMs - window.first().t >= holdMs && wobble() <= maxWobbleDeg
    }

    private fun wobble(): Float {
        val rolls = window.map { it.roll }
        val tilts = window.map { it.tilt }
        return maxOf(rolls.max() - rolls.min(), tilts.max() - tilts.min())
    }
}
