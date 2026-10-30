package com.khanz1.serlok.ui.screens.room

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal fun formatIncidentWhen(triggeredAt: String): String {
    val parsed = parseIso(triggeredAt) ?: return triggeredAt
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = parsed }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed)
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return "Today, $time"
    }
    return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(parsed)
}

private fun parseIso(value: String): java.util.Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}
