package com.khanz1.serlok.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNotificationTextTest {
    @Test
    fun `formats mini overlay body with two decimal places`() {
        val body = DebugNotificationText.body(
            nowG = 1.12f,
            peakG = 3.41f,
            meanG = 1.04f,
        )
        assertEquals("now 1.12g  peak 3.41g  avg 1.04g", body)
    }

    @Test
    fun `formats clipboard summary with detector and session fields`() {
        val summary = DebugNotificationText.summary(
            DebugSnapshot(
                sessionActive = true,
                nowG = 1.12f,
                peakG = 3.41f,
                meanG = 1.04f,
                thresholdG = 3.5f,
                triggerCount = 2,
                lastTriggerG = 3.87f,
                armed = false,
                coolingDown = true,
                cooldownRemainingMs = 1_200L,
                aboveThreshold = true,
                elapsedMs = 72_000L,
                seconds = emptyList(),
                minutes = emptyList(),
            ),
        )
        assertEquals(
            "now=1.12g peak=3.41g avg=1.04g threshold=3.5g " +
                "triggers=2 last=3.87g elapsed=01:12 cooling 1.2s",
            summary,
        )
    }

    @Test
    fun `overlay is effective only when developer options and toggle are both on`() {
        assertTrue(GForceDebugGate.isEffective(developerOptionsEnabled = true, overlayToggle = true))
        assertFalse(GForceDebugGate.isEffective(developerOptionsEnabled = true, overlayToggle = false))
        assertFalse(GForceDebugGate.isEffective(developerOptionsEnabled = false, overlayToggle = true))
        assertFalse(GForceDebugGate.isEffective(developerOptionsEnabled = false, overlayToggle = false))
    }
}
