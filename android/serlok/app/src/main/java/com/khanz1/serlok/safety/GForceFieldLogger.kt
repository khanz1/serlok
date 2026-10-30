package com.khanz1.serlok.safety

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Append-only field log. Not part of the SOS payload. */
object GForceFieldLogger {
    const val TAG = "SerlokGForce"
    private const val FOLDER = "SerlokFieldTest"
    private const val FILE_NAME = "gforce-trials.csv"
    private const val HEADER =
        "isoTime,scenario,thresholdG,triggerG,sessionPeakG,triggered,notes"

    fun appendTrigger(
        context: Context,
        thresholdG: Float,
        triggerG: Float,
        sessionPeakG: Float,
    ) {
        append(
            context = context,
            thresholdG = thresholdG,
            triggerG = formatG(triggerG),
            sessionPeakG = sessionPeakG,
            triggered = "yes",
        )
    }

    fun appendSessionStop(
        context: Context,
        thresholdG: Float,
        sessionPeakG: Float,
    ) {
        append(
            context = context,
            thresholdG = thresholdG,
            triggerG = "",
            sessionPeakG = sessionPeakG,
            triggered = "no",
        )
    }

    fun logPeak(sessionPeakG: Float) {
        Log.i(TAG, "peak=${formatG(sessionPeakG)} g")
    }

    fun logTrigger(triggerG: Float, sessionPeakG: Float, thresholdG: Float) {
        Log.i(
            TAG,
            "trigger=${formatG(triggerG)} g peak=${formatG(sessionPeakG)} g " +
                "threshold=${formatG(thresholdG)} g",
        )
    }

    private fun append(
        context: Context,
        thresholdG: Float,
        triggerG: String,
        sessionPeakG: Float,
        triggered: String,
    ) {
        val line = listOf(
            isoNow(),
            "",
            formatG(thresholdG),
            triggerG,
            formatG(sessionPeakG),
            triggered,
            "",
        ).joinToString(",")
        val local = File(File(context.getExternalFilesDir(null), FOLDER).apply { mkdirs() }, FILE_NAME)
        writeLocal(local, line)
        writeDownloads(context, local)
        Log.i(TAG, "csv ${local.absolutePath} $line")
    }

    private fun writeLocal(file: File, line: String) {
        val needsHeader = !file.exists() || file.length() == 0L
        file.appendText(buildString {
            if (needsHeader) {
                append(HEADER)
                append('\n')
            }
            append(line)
            append('\n')
        })
    }

    private fun writeDownloads(context: Context, local: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val body = local.readText()
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existingUri = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(FILE_NAME, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
            }
        }
        val uri = existingUri ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            },
        )
        if (uri == null) return
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(body.toByteArray())
        }
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    fun formatG(value: Float): String = String.format(Locale.US, "%.3f", value)
}
