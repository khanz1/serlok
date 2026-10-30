package com.khanz1.serlok.safety

import kotlin.math.roundToInt

data class GForceChartPoint(
    val elapsedIndex: Int,
    val peakG: Float,
    val meanG: Float,
    val xFraction: Float,
    val yFraction: Float,
)

object GForceChartWindow {
    val SECOND_WINDOWS = listOf(10, 30, 60, 120, 300)
    val MINUTE_WINDOWS = listOf(5, 10, 30, 60)

    enum class YScale { AUTO, G2, G5, G10 }

    fun visible(
        buckets: List<GForceTimeBucket>,
        windowSize: Int,
    ): List<GForceTimeBucket> {
        if (buckets.isEmpty() || windowSize <= 0) return emptyList()
        return buckets.takeLast(windowSize)
    }

    fun yMax(
        peaks: List<Float>,
        thresholdG: Float,
        scale: YScale,
    ): Float {
        return when (scale) {
            YScale.G2 -> 2f
            YScale.G5 -> 5f
            YScale.G10 -> 10f
            YScale.AUTO -> maxOf(peaks.maxOrNull() ?: 1f, thresholdG, 1f)
        }
    }

    fun yTicks(maxY: Float, count: Int = 5): List<Float> {
        val tickCount = count.coerceAtLeast(2)
        return (0 until tickCount).map { index ->
            maxY * index / (tickCount - 1)
        }
    }

    fun indexFromX(x: Float, width: Float, bucketCount: Int): Int {
        if (bucketCount <= 0 || width <= 0f) return -1
        if (bucketCount == 1) return 0
        val fraction = (x / width).coerceIn(0f, 1f)
        return (fraction * (bucketCount - 1)).roundToInt()
    }

    fun inspect(
        buckets: List<GForceTimeBucket>,
        localIndex: Int,
        maxY: Float,
    ): GForceChartPoint? {
        val bucket = buckets.getOrNull(localIndex) ?: return null
        val safeMax = maxY.coerceAtLeast(0.001f)
        val xFraction = if (buckets.size == 1) {
            0.5f
        } else {
            localIndex.toFloat() / (buckets.size - 1)
        }
        return GForceChartPoint(
            elapsedIndex = bucket.elapsedIndex,
            peakG = bucket.peakG,
            meanG = bucket.meanG,
            xFraction = xFraction,
            yFraction = (1f - (bucket.peakG / safeMax)).coerceIn(0f, 1f),
        )
    }
}
