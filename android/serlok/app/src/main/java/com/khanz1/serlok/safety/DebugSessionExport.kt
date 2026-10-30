package com.khanz1.serlok.safety

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GForceRawSample(
    val elapsedMs: Long,
    val magnitudeG: Float,
    val triggered: Boolean,
)

object DebugSessionExport {
    const val HEADER =
        "kind,elapsed,magnitudeG,peakG,meanG,triggered,thresholdG,triggerCount,lastTriggerG"

    fun csv(snapshot: DebugSnapshot, samples: List<GForceRawSample>): String {
        val lines = ArrayList<String>(4 + samples.size + snapshot.seconds.size + snapshot.minutes.size)
        lines.add(HEADER)
        lines.add(
            row(
                "session",
                snapshot.elapsedMs.toString(),
                format3(snapshot.nowG),
                format3(snapshot.peakG),
                format3(snapshot.meanG),
                "",
                format3(snapshot.thresholdG),
                snapshot.triggerCount.toString(),
                snapshot.lastTriggerG?.let { format3(it) } ?: "",
            ),
        )
        samples.forEach { sample ->
            lines.add(
                row(
                    "sample",
                    sample.elapsedMs.toString(),
                    format3(sample.magnitudeG),
                    "",
                    "",
                    if (sample.triggered) "1" else "0",
                    "",
                    "",
                    "",
                ),
            )
        }
        snapshot.seconds.forEach { bucket ->
            lines.add(bucketRow("second", bucket))
        }
        snapshot.minutes.forEach { bucket ->
            lines.add(bucketRow("minute", bucket))
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    fun fileName(startedAtMs: Long): String {
        val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return "gforce-session-${format.format(Date(startedAtMs))}.csv"
    }

    private fun bucketRow(kind: String, bucket: GForceTimeBucket): String =
        row(
            kind,
            bucket.elapsedIndex.toString(),
            "",
            format3(bucket.peakG),
            format3(bucket.meanG),
            "",
            "",
            "",
            "",
        )

    private fun row(vararg cells: String): String = cells.joinToString(",")

    private fun format3(value: Float): String = String.format(Locale.US, "%.3f", value)
}
