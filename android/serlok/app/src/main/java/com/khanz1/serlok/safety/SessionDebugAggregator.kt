package com.khanz1.serlok.safety

data class GForceTimeBucket(
    val elapsedIndex: Int,
    val peakG: Float,
    val meanG: Float,
)

data class DetectorDebugState(
    val thresholdG: Float,
    val aboveThreshold: Boolean,
    val coolingDown: Boolean,
    val cooldownRemainingMs: Long,
) {
    val armed: Boolean get() = !aboveThreshold && !coolingDown
}

data class DebugSnapshot(
    val sessionActive: Boolean,
    val nowG: Float,
    val peakG: Float,
    val meanG: Float,
    val thresholdG: Float,
    val triggerCount: Int,
    val lastTriggerG: Float?,
    val armed: Boolean,
    val coolingDown: Boolean,
    val cooldownRemainingMs: Long,
    val aboveThreshold: Boolean,
    val elapsedMs: Long,
    val seconds: List<GForceTimeBucket>,
    val minutes: List<GForceTimeBucket>,
) {
    companion object {
        fun empty(): DebugSnapshot = DebugSnapshot(
            sessionActive = false,
            nowG = 0f,
            peakG = 0f,
            meanG = 0f,
            thresholdG = 3.5f,
            triggerCount = 0,
            lastTriggerG = null,
            armed = true,
            coolingDown = false,
            cooldownRemainingMs = 0L,
            aboveThreshold = false,
            elapsedMs = 0L,
            seconds = emptyList(),
            minutes = emptyList(),
        )
    }
}

class SessionDebugAggregator(
    private val maxSeconds: Int = DEFAULT_MAX_SECONDS,
    private val maxRawSamples: Int = DEFAULT_MAX_RAW_SAMPLES,
) {
    private var startedAtMs = 0L
    private var sessionActive = false
    private var nowG = 0f
    private var peakG = 0f
    private var sumG = 0.0
    private var sampleCount = 0
    private var thresholdG = 3.5f
    private var triggerCount = 0
    private var lastTriggerG: Float? = null
    private var lastDetector = DetectorDebugState(
        thresholdG = 3.5f,
        aboveThreshold = false,
        coolingDown = false,
        cooldownRemainingMs = 0L,
    )
    private var lastNowMs = 0L
    private val secondBuckets = ArrayDeque<MutableBucket>()
    private val minuteBuckets = ArrayDeque<MutableBucket>()
    private val rawSamples = ArrayDeque<GForceRawSample>()

    private val maxMinutes: Int
        get() = ((maxSeconds + 59) / 60).coerceAtLeast(1)

    fun reset(startedAtMs: Long, thresholdG: Float) {
        this.startedAtMs = startedAtMs
        this.thresholdG = thresholdG
        sessionActive = true
        nowG = 0f
        peakG = 0f
        sumG = 0.0
        sampleCount = 0
        triggerCount = 0
        lastTriggerG = null
        lastDetector = DetectorDebugState(
            thresholdG = thresholdG,
            aboveThreshold = false,
            coolingDown = false,
            cooldownRemainingMs = 0L,
        )
        lastNowMs = startedAtMs
        secondBuckets.clear()
        minuteBuckets.clear()
        rawSamples.clear()
    }

    fun onSample(
        magnitudeG: Float,
        nowMs: Long,
        detector: DetectorDebugState,
        triggered: Boolean,
    ) {
        nowG = magnitudeG
        lastNowMs = nowMs
        lastDetector = detector
        thresholdG = detector.thresholdG
        sampleCount++
        sumG += magnitudeG
        if (magnitudeG > peakG) {
            peakG = magnitudeG
        }
        if (triggered) {
            triggerCount++
            lastTriggerG = magnitudeG
        }
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
        addTo(secondBuckets, (elapsed / 1_000L).toInt(), magnitudeG, maxSeconds)
        addTo(minuteBuckets, (elapsed / 60_000L).toInt(), magnitudeG, maxMinutes)
        rawSamples.addLast(
            GForceRawSample(
                elapsedMs = elapsed,
                magnitudeG = magnitudeG,
                triggered = triggered,
            ),
        )
        while (rawSamples.size > maxRawSamples) {
            rawSamples.removeFirst()
        }
    }

    fun markInactive() {
        sessionActive = false
    }

    fun rawSamples(): List<GForceRawSample> = rawSamples.toList()

    fun startedAtMs(): Long = startedAtMs

    fun exportCsv(): String = DebugSessionExport.csv(snapshot(), rawSamples())

    fun snapshot(): DebugSnapshot {
        val elapsed = if (sampleCount == 0) {
            0L
        } else {
            (lastNowMs - startedAtMs).coerceAtLeast(0L)
        }
        return DebugSnapshot(
            sessionActive = sessionActive,
            nowG = nowG,
            peakG = peakG,
            meanG = if (sampleCount == 0) 0f else (sumG / sampleCount).toFloat(),
            thresholdG = thresholdG,
            triggerCount = triggerCount,
            lastTriggerG = lastTriggerG,
            armed = lastDetector.armed,
            coolingDown = lastDetector.coolingDown,
            cooldownRemainingMs = lastDetector.cooldownRemainingMs,
            aboveThreshold = lastDetector.aboveThreshold,
            elapsedMs = elapsed,
            seconds = secondBuckets.map { it.toBucket() },
            minutes = minuteBuckets.map { it.toBucket() },
        )
    }

    private fun addTo(
        deque: ArrayDeque<MutableBucket>,
        index: Int,
        value: Float,
        max: Int,
    ) {
        val last = deque.lastOrNull()
        if (last != null && last.elapsedIndex == index) {
            last.add(value)
            return
        }
        val bucket = MutableBucket(index)
        bucket.add(value)
        deque.addLast(bucket)
        while (deque.size > max) {
            deque.removeFirst()
        }
    }

    private class MutableBucket(val elapsedIndex: Int) {
        var peakG = 0f
        var sumG = 0.0
        var count = 0

        fun add(value: Float) {
            if (count == 0 || value > peakG) {
                peakG = value
            }
            sumG += value
            count++
        }

        fun toBucket(): GForceTimeBucket =
            GForceTimeBucket(
                elapsedIndex = elapsedIndex,
                peakG = peakG,
                meanG = (sumG / count).toFloat(),
            )
    }

    companion object {
        const val DEFAULT_MAX_SECONDS = 7_200
        const val DEFAULT_MAX_RAW_SAMPLES = 180_000
    }
}
