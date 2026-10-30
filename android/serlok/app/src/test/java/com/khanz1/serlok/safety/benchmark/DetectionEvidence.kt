package com.khanz1.serlok.safety.benchmark

import java.io.File
import java.util.Locale
import kotlin.math.sqrt

data class RawTrial(
    val scenario: String,
    val positive: Boolean,
    val thresholdG: Float,
    val seed: Long,
    val peakG: Float,
    val triggerCount: Int,
    val firstTriggerMs: Long?,
    val rearm: Boolean,
    val cooldownBlocked: Boolean,
)

data class SummaryRow(
    val scenario: String,
    val thresholdG: Float,
    val n: Int,
    val peakMean: Double,
    val peakSd: Double,
    val peakMin: Double,
    val peakMax: Double,
    val triggerRate: Double,
    val meanIncidentCount: Double,
)

data class ConfusionRow(
    val thresholdG: Float,
    val tp: Int,
    val fp: Int,
    val tn: Int,
    val fn: Int,
    val precision: Double?,
    val recall: Double?,
    val accuracy: Double,
    val fpr: Double?,
)

object DetectionStats {
    fun sampleSd(values: List<Double>): Double {
        if (values.size < 2) {
            return 0.0
        }
        val mean = values.average()
        val sumSq = values.sumOf { value ->
            val delta = value - mean
            delta * delta
        }
        return sqrt(sumSq / (values.size - 1))
    }

    fun summarize(rows: List<RawTrial>): List<SummaryRow> {
        return rows
            .groupBy { it.scenario to it.thresholdG }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .map { (key, group) ->
                val peaks = group.map { it.peakG.toDouble() }
                SummaryRow(
                    scenario = key.first,
                    thresholdG = key.second,
                    n = group.size,
                    peakMean = peaks.average(),
                    peakSd = sampleSd(peaks),
                    peakMin = peaks.min(),
                    peakMax = peaks.max(),
                    triggerRate = group.count { it.triggerCount > 0 }.toDouble() / group.size,
                    meanIncidentCount = group.map { it.triggerCount.toDouble() }.average(),
                )
            }
    }

    fun confusion(rows: List<RawTrial>): List<ConfusionRow> {
        return rows
            .groupBy { it.thresholdG }
            .toSortedMap()
            .map { (threshold, group) ->
                var tp = 0
                var fp = 0
                var tn = 0
                var fn = 0
                for (row in group) {
                    val predicted = row.triggerCount > 0
                    when {
                        row.positive && predicted -> tp += 1
                        !row.positive && predicted -> fp += 1
                        !row.positive && !predicted -> tn += 1
                        else -> fn += 1
                    }
                }
                ConfusionRow(
                    thresholdG = threshold,
                    tp = tp,
                    fp = fp,
                    tn = tn,
                    fn = fn,
                    precision = ratio(tp, tp + fp),
                    recall = ratio(tp, tp + fn),
                    accuracy = (tp + tn).toDouble() / group.size,
                    fpr = ratio(fp, fp + tn),
                )
            }
    }

    private fun ratio(numerator: Int, denominator: Int): Double? {
        if (denominator == 0) {
            return null
        }
        return numerator.toDouble() / denominator
    }
}

object DetectionCsv {
    fun writeRaw(file: File, rows: List<RawTrial>) {
        val lines = mutableListOf(
            "scenario,threshold,seed,peak_g,trigger_count,first_trigger_ms,rearm,cooldown_blocked",
        )
        for (row in rows) {
            val first = row.firstTriggerMs?.toString().orEmpty()
            lines.add(
                listOf(
                    row.scenario,
                    fmt(row.thresholdG.toDouble(), 1),
                    row.seed.toString(),
                    fmt(row.peakG.toDouble(), 6),
                    row.triggerCount.toString(),
                    first,
                    row.rearm.toString(),
                    row.cooldownBlocked.toString(),
                ).joinToString(",")
            )
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    fun writeSummary(file: File, rows: List<SummaryRow>) {
        val lines = mutableListOf(
            "scenario,threshold,n,peak_mean,peak_sd,peak_min,peak_max,trigger_rate,mean_incident_count",
        )
        for (row in rows) {
            lines.add(
                listOf(
                    row.scenario,
                    fmt(row.thresholdG.toDouble(), 1),
                    row.n.toString(),
                    fmt(row.peakMean, 6),
                    fmt(row.peakSd, 6),
                    fmt(row.peakMin, 6),
                    fmt(row.peakMax, 6),
                    fmt(row.triggerRate, 4),
                    fmt(row.meanIncidentCount, 4),
                ).joinToString(",")
            )
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    fun writeConfusion(file: File, rows: List<ConfusionRow>) {
        val lines = mutableListOf(
            "threshold,tp,fp,tn,fn,precision,recall,accuracy,fpr",
        )
        for (row in rows) {
            lines.add(
                listOf(
                    fmt(row.thresholdG.toDouble(), 1),
                    row.tp.toString(),
                    row.fp.toString(),
                    row.tn.toString(),
                    row.fn.toString(),
                    fmtNullable(row.precision, 4),
                    fmtNullable(row.recall, 4),
                    fmt(row.accuracy, 4),
                    fmtNullable(row.fpr, 4),
                ).joinToString(",")
            )
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    fun writeSweep(file: File, rows: List<ConfusionRow>) {
        val lines = mutableListOf("threshold,tp,fp,tn,fn,accuracy,fpr")
        for (row in rows) {
            lines.add(
                listOf(
                    fmt(row.thresholdG.toDouble(), 1),
                    row.tp.toString(),
                    row.fp.toString(),
                    row.tn.toString(),
                    row.fn.toString(),
                    fmt(row.accuracy, 4),
                    fmtNullable(row.fpr, 4),
                ).joinToString(",")
            )
        }
        file.writeText(lines.joinToString("\n") + "\n")
    }

    private fun fmt(value: Double, digits: Int): String {
        return String.format(Locale.US, "%.${digits}f", value)
    }

    private fun fmtNullable(value: Double?, digits: Int): String {
        if (value == null) {
            return ""
        }
        return fmt(value, digits)
    }
}

object EvidencePaths {
    fun resolve(): File {
        val fromProperty = System.getProperty("serlok.evidence.dir")
        if (!fromProperty.isNullOrBlank()) {
            return File(fromProperty)
        }
        val fromEnv = System.getenv("SERLOK_EVIDENCE_DIR")
        if (!fromEnv.isNullOrBlank()) {
            return File(fromEnv)
        }
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return defaultFromUserDir(userDir)
    }

    internal fun defaultFromUserDir(userDir: File): File {
        val path = userDir.invariantSeparatorsPath
        return when {
            path.endsWith("android/serlok/app") -> File(userDir, "../../../evidence")
            path.endsWith("android/serlok") -> File(userDir, "../../evidence")
            else -> File(userDir, "evidence")
        }
    }
}
