package com.khanz1.serlok.safety.benchmark

import com.khanz1.serlok.safety.GForceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GForceDetectionBenchmarkTest {
    @Test
    fun `replay reports a trigger on a single synthetic spike`() {
        val samples = listOf(
            AccelSample(0f, 0f, 9.80665f, 0L),
            AccelSample(50f, 0f, 0f, AccelerationProfiles.PERIOD_NS),
            AccelSample(0f, 0f, 9.80665f, AccelerationProfiles.PERIOD_NS * 2),
        )
        val result = DetectorReplay.run(samples, thresholdG = 2.5f)
        assertTrue(result.peakG > 2.5f)
        assertEquals(1, result.triggerCount)
        assertEquals(20L, result.firstTriggerMs)
    }

    @Test
    fun `writes detection evidence csv from real GForceDetector`() {
        val traces = AccelerationProfiles.generateAll(repetitions = REPETITIONS)
        assertEquals(SCENARIO_COUNT * REPETITIONS, traces.size)

        val namedThresholds = listOf(2.5f, 3.5f, 4.0f, 4.5f)
        val namedRows = mutableListOf<RawTrial>()
        for (trace in traces) {
            for (threshold in namedThresholds) {
                namedRows.add(replayRow(trace, threshold))
            }
        }

        val sweepRows = mutableListOf<RawTrial>()
        for (trace in traces) {
            var hundredths = SWEEP_START_HUNDREDTHS
            while (hundredths <= SWEEP_END_HUNDREDTHS) {
                val threshold = hundredths / 10f
                sweepRows.add(replayRow(trace, threshold))
                hundredths += 1
            }
        }

        val summary = DetectionStats.summarize(namedRows)
        val confusion = DetectionStats.confusion(namedRows)
        val sweep = DetectionStats.confusion(sweepRows)

        val outDir = EvidencePaths.resolve()
        outDir.mkdirs()
        DetectionCsv.writeRaw(File(outDir, "detection_raw.csv"), namedRows)
        DetectionCsv.writeSummary(File(outDir, "detection_summary.csv"), summary)
        DetectionCsv.writeConfusion(File(outDir, "confusion_matrix.csv"), confusion)
        DetectionCsv.writeSweep(File(outDir, "threshold_sweep.csv"), sweep)

        assertEquals(SCENARIO_COUNT * REPETITIONS * namedThresholds.size, namedRows.size)
        assertEquals(SCENARIO_COUNT * namedThresholds.size, summary.size)
        assertEquals(namedThresholds.size, confusion.size)
        assertEquals(SWEEP_STEPS, sweep.size)
        assertTrue(GForceDetector.normalizedG(0f, 0f, 9.80665f) > 0.99f)
    }

    private fun replayRow(trace: AccelerationProfiles.Trace, thresholdG: Float): RawTrial {
        val result = DetectorReplay.run(trace.samples, thresholdG)
        return RawTrial(
            scenario = trace.scenario.id,
            positive = trace.scenario.positive,
            thresholdG = thresholdG,
            seed = trace.seed,
            peakG = result.peakG,
            triggerCount = result.triggerCount,
            firstTriggerMs = result.firstTriggerMs,
            rearm = result.rearm,
            cooldownBlocked = result.cooldownBlocked,
        )
    }

    companion object {
        private const val SCENARIO_COUNT = 8
        private const val REPETITIONS = 20
        private const val SWEEP_START_HUNDREDTHS = 20
        private const val SWEEP_END_HUNDREDTHS = 50
        private const val SWEEP_STEPS = SWEEP_END_HUNDREDTHS - SWEEP_START_HUNDREDTHS + 1
    }
}
