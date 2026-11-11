package com.nitestand.standbyclock.clock

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClockTickerTest {

    @Test
    fun `next minute boundary`() {
        // 12:00:59.000 → 1 s (+margin) to the boundary
        val at = 59_000L
        val wait = ClockTicker.millisUntilNextBoundary(at, TickInterval.MINUTE)
        assertTrue(wait in 1_000L..1_100L)
        // exactly on a boundary → a full minute
        assertTrue(ClockTicker.millisUntilNextBoundary(120_000L, TickInterval.MINUTE) in 60_000L..60_100L)
    }

    @Test
    fun `next second boundary`() {
        assertTrue(ClockTicker.millisUntilNextBoundary(1_250L, TickInterval.SECOND) in 750L..800L)
    }

    @Test
    fun `ticks are emitted on minute boundaries without polling`() = runTest {
        val ticker = ClockTicker(context = null, nowMillis = { currentTime + 30_000L })
        val ticks = ticker.ticks(TickInterval.MINUTE).take(3).toList()
        assertEquals(3, ticks.size)
        // first emission immediately, then roughly 30 s and 90 s later on minute boundaries
        assertEquals(0, ticks[1].second)
        assertEquals(0, ticks[2].second)
        assertEquals(1L, java.time.Duration.between(ticks[1], ticks[2]).toMinutes())
    }
}
