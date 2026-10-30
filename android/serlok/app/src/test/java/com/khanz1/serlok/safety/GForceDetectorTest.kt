package com.khanz1.serlok.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GForceDetectorTest {
    @Test
    fun `normalizes acceleration in meters per second squared to g`() {
        assertEquals(1f, GForceDetector.normalizedG(0f, 0f, 9.80665f), 0.001f)
        assertEquals(3.5f, GForceDetector.normalizedG(34.323275f, 0f, 0f), 0.001f)
    }

    @Test
    fun `triggers once on the first rising edge above threshold`() {
        val detector = GForceDetector(thresholdG = 2.5f)
        val trigger = detector.onSample(30f, 0f, 0f, 100L)
        assertTrue((trigger?.magnitudeG ?: 0f) > 3f)
        assertNull(detector.onSample(31f, 0f, 0f, 150L))
    }

    @Test
    fun `blocks repeated rising edges for three seconds and requires rearm`() {
        val detector = GForceDetector(thresholdG = 2.5f)
        assertTrue(detector.onSample(26f, 0f, 0f, 100L) != null)
        assertNull(detector.onSample(27f, 0f, 0f, 200L))
        assertNull(detector.onSample(9.8f, 0f, 0f, 300L))
        assertNull(detector.onSample(26f, 0f, 0f, 3_099L))
        assertNull(detector.onSample(9.8f, 0f, 0f, 3_100L))
        assertTrue(detector.onSample(26f, 0f, 0f, 3_101L) != null)
    }

    @Test
    fun `reports cooldown remaining after a trigger`() {
        val detector = GForceDetector(thresholdG = 2.5f)
        assertTrue(detector.onSample(26f, 0f, 0f, 100L) != null)
        val cooling = detector.debugState(nowMs = 1_100L)
        assertTrue(cooling.coolingDown)
        assertEquals(2_000L, cooling.cooldownRemainingMs)
        assertTrue(cooling.aboveThreshold)
        assertFalse(cooling.armed)
    }

    @Test
    fun `reports armed after cooldown and hysteresis release`() {
        val detector = GForceDetector(thresholdG = 2.5f)
        assertTrue(detector.onSample(26f, 0f, 0f, 100L) != null)
        detector.onSample(9.8f, 0f, 0f, 300L)
        val ready = detector.debugState(nowMs = 3_200L)
        assertFalse(ready.coolingDown)
        assertEquals(0L, ready.cooldownRemainingMs)
        assertFalse(ready.aboveThreshold)
        assertTrue(ready.armed)
        assertEquals(2.5f, ready.thresholdG, 0.001f)
    }
}
