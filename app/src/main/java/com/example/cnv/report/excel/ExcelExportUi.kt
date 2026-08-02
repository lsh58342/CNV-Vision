package com.example.cnv.report.excel

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * SAF helpers for Excel export / reopen (STEP 19-1).
 */
object ExcelExportUi {
    const val MIME_XLSX =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun createDocumentIntent(fileName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_XLSX
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

    fun createCsvDocumentIntent(fileName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

    fun takePersistablePermission(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    fun openIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_XLSX)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
