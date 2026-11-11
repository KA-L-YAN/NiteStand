package com.nitestand.standbyclock.display

import kotlin.math.abs
import kotlin.random.Random

/** Offset in density-independent pixels. */
data class ShiftOffset(val xDp: Int, val yDp: Int)

/**
 * Produces the slow, small position changes used for OLED burn-in protection.
 *
 * Each call to [next] moves the content by at most [maxStepDp] from its previous position,
 * staying within ±[smallRadiusDp] of the centre. Every [repositionEvery] steps the anchor is
 * occasionally moved further (up to ±[largeRadiusDp]) so that no pixel keeps showing the same
 * edge for hours. Values are deliberately tiny so the movement is barely noticeable.
 */
class BurnInShifter(
    private val smallRadiusDp: Int = 6,
    private val largeRadiusDp: Int = 14,
    private val maxStepDp: Int = 3,
    private val repositionEvery: Int = 12,
    /** Seeded by default so the drift path is deterministic (and testable). */
    private val random: Random = Random(DEFAULT_SEED),
) {
    init {
        require(smallRadiusDp in 1..largeRadiusDp)
        require(maxStepDp >= 1)
    }

    private var anchorX = 0
    private var anchorY = 0
    var current = ShiftOffset(0, 0)
        private set
    private var steps = 0

    fun next(): ShiftOffset {
        steps++
        if (steps % repositionEvery == 0) {
            val limit = largeRadiusDp - smallRadiusDp
            anchorX = random.nextInt(-limit, limit + 1)
            anchorY = random.nextInt(-limit, limit + 1)
        }
        var candidate: ShiftOffset
        var attempts = 0
        do {
            val x = stepToward(current.xDp, anchorX)
            val y = stepToward(current.yDp, anchorY)
            candidate = ShiftOffset(x, y)
            attempts++
        } while (candidate == current && attempts < 8)
        if (candidate == current) {
            // Guarantee movement: nudge one axis back toward the anchor centre.
            candidate = ShiftOffset(current.xDp + if (current.xDp > anchorX) -1 else 1, current.yDp)
        }
        current = candidate
        return candidate
    }

    companion object {
        const val DEFAULT_SEED = 0x5713_B1
    }

    private fun stepToward(value: Int, anchor: Int): Int {
        val step = random.nextInt(-maxStepDp, maxStepDp + 1)
        val proposed = value + step
        val min = anchor - smallRadiusDp
        val max = anchor + smallRadiusDp
        val bounded = proposed.coerceIn(min, max).coerceIn(-largeRadiusDp, largeRadiusDp)
        // When re-anchoring, drift there gradually instead of jumping.
        return if (abs(bounded - value) > maxStepDp) value + maxStepDp * Integer.signum(bounded - value) else bounded
    }
}
