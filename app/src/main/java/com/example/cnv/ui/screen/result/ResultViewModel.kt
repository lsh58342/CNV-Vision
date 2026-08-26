package com.example.cnv.ui.screen.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.db.InspectionDbGate

/**
 * Result Screen ViewModel — reads existing InspectionResult only (no new calculations).
 */
class ResultViewModel : ViewModel() {

    data class SummaryUi(
        val sessionId: String,
        val distanceMm: Float,
        val durationSec: Long,
        val shockCount: Int,
        val coveragePercent: Float,
        val empty: Boolean = false,
        val reportReady: Boolean = false,
        val reportStatus: String? = null,
        val excelFileName: String? = null,
        val excelUri: String? = null,
        val drawingId: String? = null,
    )

    private val catalog = FactoryCatalog.get()

    private val _summary = MutableLiveData<SummaryUi>()
    val summary: LiveData<SummaryUi> = _summary

    fun loadLatest() {
        val result: InspectionResult? = catalog.inspections.underlying().latest()
        if (result == null) {
            _summary.value = SummaryUi(
                sessionId = "—",
                distanceMm = 0f,
                durationSec = 0L,
                shockCount = 0,
                coveragePercent = 0f,
                empty = true,
            )
            return
        }
        val base = SummaryUi(
            sessionId = result.sessionId,
            distanceMm = result.statistics.totalDistanceMm,
            durationSec = result.durationMs / 1000L,
            shockCount = result.statistics.shockCount,
            coveragePercent = result.routeQualityScore * 100f,
            empty = false,
        )
        _summary.value = base
        InspectionDbGate.submit(
            block = {
                val persisted = catalog.inspections.loadSession(result.sessionId)
                val excel = catalog.excelArchives.getOrLoad(
                    result.sessionId,
                    catalog.inspections.underlying(),
                )
                val report = catalog.reports.getCached(result.sessionId)
                Triple(persisted, excel, report)
            },
            onMain = { (persisted, excel, report) ->
                val status = when {
                    report != null -> {
                        "${report.maintenanceSummary.overallStatus.name} · Grade ${report.maintenanceSummary.inspectionGrade.name}"
                    }
                    excel != null -> "Excel ready"
                    else -> null
                }
                _summary.value = base.copy(
                    reportReady = excel != null || report != null,
                    reportStatus = status,
                    excelFileName = excel?.fileName,
                    excelUri = excel?.fileUri,
                    drawingId = persisted?.summary?.drawingId,
                )
            },
        )
    }
}
