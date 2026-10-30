package com.khanz1.serlok.safety

import java.util.Locale

object GForceDebugGate {
    fun isEffective(developerOptionsEnabled: Boolean, overlayToggle: Boolean): Boolean =
        developerOptionsEnabled && overlayToggle
}

object DebugNotificationText {
    fun body(nowG: Float, peakG: Float, meanG: Float): String =
        "now ${format2(nowG)}g  peak ${format2(peakG)}g  avg ${format2(meanG)}g"

    fun summary(snapshot: DebugSnapshot): String {
        val last = snapshot.lastTriggerG?.let { value ->
            "last=${format2(value)}g"
        } ?: "last=-"
        val status = when {
            snapshot.coolingDown ->
                "cooling ${format1(snapshot.cooldownRemainingMs / 1_000f)}s"
            snapshot.armed -> "armed"
            else -> "waiting re-arm"
        }
        return "now=${format2(snapshot.nowG)}g peak=${format2(snapshot.peakG)}g " +
            "avg=${format2(snapshot.meanG)}g threshold=${format1(snapshot.thresholdG)}g " +
            "triggers=${snapshot.triggerCount} $last elapsed=${formatElapsed(snapshot.elapsedMs)} $status"
    }

    fun formatElapsed(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1_000L
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)

    fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)
}
