package com.khanz1.serlok.safety.benchmark

import com.khanz1.serlok.safety.GForceDetector

data class ReplayResult(
    val peakG: Float,
    val triggerCount: Int,
    val firstTriggerMs: Long?,
    val rearm: Boolean,
    val cooldownBlocked: Boolean,
)

object DetectorReplay {
    fun run(
        samples: List<AccelSample>,
        thresholdG: Float,
        cooldownMs: Long = 3_000L,
    ): ReplayResult {
        val detector = GForceDetector(thresholdG, cooldownMs)
        val release = thresholdG * 0.8f
        val startMs = if (samples.isEmpty()) {
            0L
        } else {
            samples.first().timestampNs / 1_000_000L
        }
        var peakG = 0f
        var triggerCount = 0
        var firstTriggerMs: Long? = null
        var lastTriggerAtMs = Long.MIN_VALUE
        var rearm = false
        var cooldownBlocked = false
        var armed = true

        for (sample in samples) {
            val nowMs = sample.timestampNs / 1_000_000L
            val mag = GForceDetector.normalizedG(sample.x, sample.y, sample.z)
            if (mag > peakG) {
                peakG = mag
            }

            if (mag < release) {
                if (triggerCount > 0) {
                    rearm = true
                }
                armed = true
            }

            val risingEdge = mag >= thresholdG && armed
            val trigger = detector.onSample(sample.x, sample.y, sample.z, nowMs)

            if (trigger != null) {
                triggerCount += 1
                if (firstTriggerMs == null) {
                    firstTriggerMs = trigger.detectedAtMs - startMs
                }
                lastTriggerAtMs = trigger.detectedAtMs
                armed = false
            } else if (risingEdge) {
                val inCooldown =
                    lastTriggerAtMs != Long.MIN_VALUE &&
                        nowMs - lastTriggerAtMs < cooldownMs
                if (inCooldown) {
                    cooldownBlocked = true
                }
                armed = false
            }
        }

        return ReplayResult(
            peakG = peakG,
            triggerCount = triggerCount,
            firstTriggerMs = firstTriggerMs,
            rearm = rearm,
            cooldownBlocked = cooldownBlocked,
        )
    }
}
