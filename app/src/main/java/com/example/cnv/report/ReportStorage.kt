package com.example.cnv.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.cnv.util.AppFileGuard
import java.io.File

/** Internal report files under files/reports/{sessionId}/ */
object ReportStorage {

    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        reportsRoot().mkdirs()
    }

    fun reportsRoot(): File =
        File(requireContext().filesDir, "reports").also { it.mkdirs() }

    fun sessionDir(sessionId: String): File? =
        AppFileGuard.childDir(reportsRoot(), sessionId)?.also { it.mkdirs() }

    fun excelFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "inspection_report.xlsx")

    fun csvFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "inspection_summary.csv")

    fun maintenanceJsonFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "maintenance_report.json")

    fun maintenanceCsvFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "maintenance_report.csv")

    fun maintenanceTextFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "maintenance_report.txt")

    fun criticalMapFile(sessionId: String): File =
        File(requireSessionDir(sessionId), "critical_shock_map.png")

    fun fileUri(file: File): Uri {
        val ctx = requireContext()
        val safe = AppFileGuard.fileUnderRoot(reportsRoot(), file)
            ?: error("Report file outside reports root: ${file.path}")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", safe)
    }

    fun deleteSession(sessionId: String) {
        sessionDir(sessionId)?.deleteRecursively()
    }

    private fun requireSessionDir(sessionId: String): File =
        sessionDir(sessionId) ?: error("Invalid sessionId for report storage: $sessionId")

    private fun requireContext(): Context =
        appContext ?: error("ReportStorage not bound — call bind() in Application.onCreate")
}
