package com.khanz1.serlok.safety

import kotlin.math.sqrt

data class GForceTrigger(
    val magnitudeG: Float,
    val x: Float,
    val y: Float,
    val z: Float,
    val detectedAtMs: Long,
)

/**
 * Deterministic heuristic shake/impact detector. It emits on a rising edge,
 * uses hysteresis to re-arm, and applies a local cooldown before another
 * incident can be emitted.
 */
class GForceDetector(
    thresholdG: Float,
    private val cooldownMs: Long = 3_000L,
) {
    private var thresholdG = thresholdG
    private var aboveThreshold = false
    private var lastTriggerAtMs = Long.MIN_VALUE

    fun updateThreshold(value: Float) {
        if (value == thresholdG) return
        thresholdG = value
        aboveThreshold = false
    }

    fun onSample(x: Float, y: Float, z: Float, nowMs: Long): GForceTrigger? {
        val magnitudeG = normalizedG(x, y, z)
        val releaseThreshold = thresholdG * 0.8f
        if (magnitudeG < releaseThreshold) aboveThreshold = false

        if (magnitudeG < thresholdG || aboveThreshold) return null

        aboveThreshold = true
        if (isCoolingDown(nowMs)) return null

        val trigger = GForceTrigger(
            magnitudeG = magnitudeG,
            x = x,
            y = y,
            z = z,
            detectedAtMs = nowMs,
        )
        lastTriggerAtMs = nowMs
        return trigger
    }

    private fun isCoolingDown(nowMs: Long): Boolean =
        lastTriggerAtMs != Long.MIN_VALUE && nowMs - lastTriggerAtMs < cooldownMs

    fun debugState(nowMs: Long): DetectorDebugState {
        val remaining = if (lastTriggerAtMs == Long.MIN_VALUE) {
            0L
        } else {
            (cooldownMs - (nowMs - lastTriggerAtMs)).coerceAtLeast(0L)
        }
        return DetectorDebugState(
            thresholdG = thresholdG,
            aboveThreshold = aboveThreshold,
            coolingDown = remaining > 0L,
            cooldownRemainingMs = remaining,
        )
    }

    companion object {
        const val EARTH_GRAVITY_MPS2 = 9.80665

        fun normalizedG(x: Float, y: Float, z: Float): Float =
            (sqrt((x * x + y * y + z * z).toDouble()) / EARTH_GRAVITY_MPS2).toFloat()
    }
}
