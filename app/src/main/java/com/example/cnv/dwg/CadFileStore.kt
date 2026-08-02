package com.example.cnv.dwg

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * Copies a user-picked CAD URI into app-private storage so [DxfReader] can open a real File path.
 */
object CadFileStore {

    fun persistPickedFile(context: Context, uri: Uri): String {
        val displayName = queryDisplayName(context, uri) ?: "drawing.dxf"
        val ext = extensionOf(displayName, uri)
        val dir = File(context.filesDir, "cad").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot open CAD URI: $uri")
        return dest.absolutePath
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun extensionOf(displayName: String, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', missingDelimiterValue = "")
        if (fromName.equals("dxf", ignoreCase = true) || fromName.equals("dwg", ignoreCase = true)) {
            return fromName.lowercase()
        }
        val path = uri.toString().lowercase()
        return when {
            path.contains(".dxf") -> "dxf"
            path.contains(".dwg") -> "dwg"
            else -> "dxf"
        }
    }
}
