package com.khanz1.serlok.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDebugAggregatorTest {
    private val idle = DetectorDebugState(
        thresholdG = 3.5f,
        aboveThreshold = false,
        coolingDown = false,
        cooldownRemainingMs = 0L,
    )

    @Test
    fun `tracks session peak and running mean`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(3.0f, nowMs = 10L, detector = idle, triggered = false)

        val snapshot = aggregator.snapshot()
        assertEquals(3.0f, snapshot.peakG, 0.001f)
        assertEquals(2.0f, snapshot.meanG, 0.001f)
        assertEquals(3.0f, snapshot.nowG, 0.001f)
        assertTrue(snapshot.sessionActive)
        assertEquals(3.5f, snapshot.thresholdG, 0.001f)
        assertEquals(10L, snapshot.elapsedMs)
    }

    @Test
    fun `buckets peak and mean per elapsed second`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(2.0f, nowMs = 500L, detector = idle, triggered = false)
        aggregator.onSample(1.5f, nowMs = 1_000L, detector = idle, triggered = false)

        val seconds = aggregator.snapshot().seconds
        assertEquals(2, seconds.size)
        assertEquals(0, seconds[0].elapsedIndex)
        assertEquals(2.0f, seconds[0].peakG, 0.001f)
        assertEquals(1.5f, seconds[0].meanG, 0.001f)
        assertEquals(1, seconds[1].elapsedIndex)
        assertEquals(1.5f, seconds[1].peakG, 0.001f)
        assertEquals(1.5f, seconds[1].meanG, 0.001f)
    }

    @Test
    fun `buckets peak and mean per elapsed minute`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(4.0f, nowMs = 30_000L, detector = idle, triggered = false)
        aggregator.onSample(2.0f, nowMs = 60_000L, detector = idle, triggered = false)

        val minutes = aggregator.snapshot().minutes
        assertEquals(2, minutes.size)
        assertEquals(0, minutes[0].elapsedIndex)
        assertEquals(4.0f, minutes[0].peakG, 0.001f)
        assertEquals(2.5f, minutes[0].meanG, 0.001f)
        assertEquals(1, minutes[1].elapsedIndex)
        assertEquals(2.0f, minutes[1].peakG, 0.001f)
    }

    @Test
    fun `counts triggers and last trigger g`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(4.1f, nowMs = 0L, detector = idle, triggered = true)
        aggregator.onSample(1.0f, nowMs = 20L, detector = idle, triggered = false)

        val snapshot = aggregator.snapshot()
        assertEquals(1, snapshot.triggerCount)
        assertEquals(4.1f, snapshot.lastTriggerG ?: 0f, 0.001f)
    }

    @Test
    fun `reset clears previous session stats`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(5.0f, nowMs = 0L, detector = idle, triggered = true)
        aggregator.reset(startedAtMs = 10_000L, thresholdG = 2.5f)

        val snapshot = aggregator.snapshot()
        assertEquals(0f, snapshot.peakG, 0.001f)
        assertEquals(0f, snapshot.meanG, 0.001f)
        assertEquals(0, snapshot.triggerCount)
        assertNull(snapshot.lastTriggerG)
        assertTrue(snapshot.seconds.isEmpty())
        assertEquals(2.5f, snapshot.thresholdG, 0.001f)
        assertTrue(snapshot.sessionActive)
    }

    @Test
    fun `markInactive keeps last snapshot until reset`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(2.2f, nowMs = 1_000L, detector = idle, triggered = false)
        aggregator.markInactive()

        val snapshot = aggregator.snapshot()
        assertFalse(snapshot.sessionActive)
        assertEquals(2.2f, snapshot.peakG, 0.001f)
        assertEquals(1, snapshot.seconds.size)
    }

    @Test
    fun `caps second buckets at two hours`() {
        val aggregator = SessionDebugAggregator(maxSeconds = 3)
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(2.0f, nowMs = 1_000L, detector = idle, triggered = false)
        aggregator.onSample(3.0f, nowMs = 2_000L, detector = idle, triggered = false)
        aggregator.onSample(4.0f, nowMs = 3_000L, detector = idle, triggered = false)

        val seconds = aggregator.snapshot().seconds
        assertEquals(3, seconds.size)
        assertEquals(1, seconds.first().elapsedIndex)
        assertEquals(3, seconds.last().elapsedIndex)
        assertEquals(4.0f, seconds.last().peakG, 0.001f)
    }

    @Test
    fun `copies detector armed and cooldown into snapshot`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        val cooling = DetectorDebugState(
            thresholdG = 3.5f,
            aboveThreshold = true,
            coolingDown = true,
            cooldownRemainingMs = 1_500L,
        )
        aggregator.onSample(4.0f, nowMs = 50L, detector = cooling, triggered = true)

        val snapshot = aggregator.snapshot()
        assertFalse(snapshot.armed)
        assertTrue(snapshot.coolingDown)
        assertEquals(1_500L, snapshot.cooldownRemainingMs)
        assertTrue(snapshot.aboveThreshold)
    }
}
