package com.khanz1.serlok.safety

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object DebugSessionDownload {
    const val FOLDER = "SerlokDebug"

    fun save(context: Context, fileName: String, csv: String): Uri? {
        val localDir = File(context.getExternalFilesDir(null), FOLDER).apply { mkdirs() }
        val local = File(localDir, fileName)
        local.writeText(csv)
        val downloadsUri = writeDownloads(context, fileName, csv)
        if (downloadsUri != null) return downloadsUri
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            local,
        )
    }

    private fun writeDownloads(context: Context, fileName: String, csv: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(csv.toByteArray())
        } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
