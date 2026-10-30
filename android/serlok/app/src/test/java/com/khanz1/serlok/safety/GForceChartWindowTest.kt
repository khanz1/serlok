package com.khanz1.serlok.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GForceChartWindowTest {
    private fun bucket(index: Int, peak: Float) = GForceTimeBucket(
        elapsedIndex = index,
        peakG = peak,
        meanG = peak / 2f,
    )

    @Test
    fun `visible window keeps the last n buckets`() {
        val buckets = (0..9).map { index -> bucket(index, index.toFloat()) }
        val visible = GForceChartWindow.visible(buckets, windowSize = 3)
        assertEquals(listOf(7, 8, 9), visible.map { it.elapsedIndex })
        assertEquals(9f, visible.last().peakG, 0.001f)
    }

    @Test
    fun `visible window returns all buckets when shorter than range`() {
        val buckets = listOf(bucket(0, 1f), bucket(1, 2f))
        val visible = GForceChartWindow.visible(buckets, windowSize = 30)
        assertEquals(2, visible.size)
    }

    @Test
    fun `auto y scale uses the greater of peak, threshold, and one g`() {
        assertEquals(
            3.5f,
            GForceChartWindow.yMax(peaks = listOf(1.1f, 2.0f), thresholdG = 3.5f, scale = GForceChartWindow.YScale.AUTO),
            0.001f,
        )
        assertEquals(
            4.2f,
            GForceChartWindow.yMax(peaks = listOf(4.2f), thresholdG = 2.5f, scale = GForceChartWindow.YScale.AUTO),
            0.001f,
        )
    }

    @Test
    fun `fixed y scale ignores live peaks`() {
        assertEquals(
            2f,
            GForceChartWindow.yMax(peaks = listOf(9f), thresholdG = 4.5f, scale = GForceChartWindow.YScale.G2),
            0.001f,
        )
        assertEquals(
            5f,
            GForceChartWindow.yMax(peaks = emptyList(), thresholdG = 3.5f, scale = GForceChartWindow.YScale.G5),
            0.001f,
        )
        assertEquals(
            10f,
            GForceChartWindow.yMax(peaks = listOf(1f), thresholdG = 3.5f, scale = GForceChartWindow.YScale.G10),
            0.001f,
        )
    }

    @Test
    fun `y ticks include zero and the scale max`() {
        val ticks = GForceChartWindow.yTicks(maxY = 4f, count = 5)
        assertEquals(5, ticks.size)
        assertEquals(0f, ticks.first(), 0.001f)
        assertEquals(4f, ticks.last(), 0.001f)
        assertEquals(2f, ticks[2], 0.001f)
    }

    @Test
    fun `pointer x maps to the nearest bucket index`() {
        assertEquals(0, GForceChartWindow.indexFromX(x = 0f, width = 100f, bucketCount = 5))
        assertEquals(4, GForceChartWindow.indexFromX(x = 100f, width = 100f, bucketCount = 5))
        assertEquals(2, GForceChartWindow.indexFromX(x = 50f, width = 100f, bucketCount = 5))
        assertEquals(0, GForceChartWindow.indexFromX(x = -10f, width = 100f, bucketCount = 5))
        assertEquals(-1, GForceChartWindow.indexFromX(x = 10f, width = 100f, bucketCount = 0))
    }

    @Test
    fun `point popup uses peak g at the inspected bucket`() {
        val visible = listOf(bucket(4, 1.12f), bucket(5, 3.41f))
        val point = GForceChartWindow.inspect(visible, localIndex = 1, maxY = 5f)
        assertTrue(point != null)
        assertEquals(3.41f, point!!.peakG, 0.001f)
        assertEquals(5, point.elapsedIndex)
        assertEquals(1f, point.xFraction, 0.001f)
        assertEquals(1f - 3.41f / 5f, point.yFraction, 0.001f)
    }
}
