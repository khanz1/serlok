package com.khanz1.serlok.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugSessionExportTest {
    private val idle = DetectorDebugState(
        thresholdG = 3.5f,
        aboveThreshold = false,
        coolingDown = false,
        cooldownRemainingMs = 0L,
    )

    @Test
    fun `csv includes session summary sample rows and time buckets`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(3.0f, nowMs = 20L, detector = idle, triggered = true)

        val csv = DebugSessionExport.csv(aggregator.snapshot(), aggregator.rawSamples())
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(
            "kind,elapsed,magnitudeG,peakG,meanG,triggered,thresholdG,triggerCount,lastTriggerG",
            lines[0],
        )
        assertEquals("session,20,3.000,3.000,2.000,,3.500,1,3.000", lines[1])
        assertTrue(lines.contains("sample,0,1.000,,,0,,,"))
        assertTrue(lines.contains("sample,20,3.000,,,1,,,"))
        assertTrue(lines.any { it.startsWith("second,0,,3.000,2.000,") })
        assertTrue(lines.any { it.startsWith("minute,0,,3.000,2.000,") })
    }

    @Test
    fun `reset clears raw samples`() {
        val aggregator = SessionDebugAggregator()
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.reset(startedAtMs = 5_000L, thresholdG = 2.5f)
        assertTrue(aggregator.rawSamples().isEmpty())
    }

    @Test
    fun `caps raw samples at the configured limit`() {
        val aggregator = SessionDebugAggregator(maxRawSamples = 2)
        aggregator.reset(startedAtMs = 0L, thresholdG = 3.5f)
        aggregator.onSample(1.0f, nowMs = 0L, detector = idle, triggered = false)
        aggregator.onSample(2.0f, nowMs = 10L, detector = idle, triggered = false)
        aggregator.onSample(3.0f, nowMs = 20L, detector = idle, triggered = false)
        val raw = aggregator.rawSamples()
        assertEquals(2, raw.size)
        assertEquals(10L, raw[0].elapsedMs)
        assertEquals(3.0f, raw[1].magnitudeG, 0.001f)
        assertFalse(raw[1].triggered)
    }

    @Test
    fun `suggested file name uses the utc session timestamp`() {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.set(2026, java.util.Calendar.AUGUST, 26, 21, 15, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        assertEquals(
            "gforce-session-20260826-211500.csv",
            DebugSessionExport.fileName(calendar.timeInMillis),
        )
    }
}
