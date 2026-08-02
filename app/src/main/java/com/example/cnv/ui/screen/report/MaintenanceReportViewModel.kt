package com.example.cnv.ui.screen.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.report.MaintenanceReport
import com.example.cnv.report.MaintenanceReportAssembler
import com.example.cnv.report.ReportExportFormat
import com.example.cnv.report.ReportExportPayload
import com.example.cnv.report.ReportExporter
import com.example.cnv.report.WorkOrder
import com.example.cnv.rule.RuleHit

/**
 * Maintenance Report ViewModel (STEP 19).
 * Loads Report via Repository; HeatMap from HeatMap Repository only.
 * Does not analyze events, re-evaluate rules, or regenerate HeatMap.
 */
class MaintenanceReportViewModel(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val errorMessage: String? = null,
        val sessionId: String? = null,
        val drawingId: String? = null,
        val report: MaintenanceReport? = null,
        val heatPoints: List<DrawingHeatPoint> = emptyList(),
        val workOrders: List<WorkOrder> = emptyList(),
        val lastExport: ReportExportPayload? = null,
        val toastMessage: String? = null,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var loadGeneration = 0

    fun load(sessionId: String, drawingId: String) {
        val gen = ++loadGeneration
        val heatPoints = catalog.heatMaps.loadHeatPointsForSession(drawingId, sessionId)
        _state.value = UiState(
            loading = true,
            sessionId = sessionId,
            drawingId = drawingId,
            heatPoints = heatPoints,
            workOrders = catalog.workOrders.forSession(sessionId),
        )
        catalog.reports.getOrAssembleAsync(sessionId, drawingId) { report ->
            if (gen != loadGeneration) return@getOrAssembleAsync
            if (report == null) {
                _state.value = UiState(
                    loading = false,
                    errorMessage = "Maintenance Report unavailable",
                    sessionId = sessionId,
                    drawingId = drawingId,
                    heatPoints = heatPoints,
                    workOrders = catalog.workOrders.forSession(sessionId),
                )
                return@getOrAssembleAsync
            }
            _state.value = UiState(
                loading = false,
                sessionId = sessionId,
                drawingId = drawingId,
                report = report,
                heatPoints = heatPoints,
                workOrders = catalog.workOrders.forSession(sessionId),
            )
        }
    }

    fun createWorkOrder(hit: RuleHit): WorkOrder? {
        val report = _state.value?.report ?: return null
        val order = MaintenanceReportAssembler.toWorkOrder(report, hit)
        catalog.workOrders.put(order)
        val cur = _state.value ?: return order
        _state.value = cur.copy(
            workOrders = catalog.workOrders.forSession(report.sessionId),
            toastMessage = "Work Order created: ${order.workOrderId.take(8)}",
        )
        return order
    }

    fun createWorkOrderFromPriority(): WorkOrder? {
        val hit = _state.value?.report?.priorityIssue ?: return null
        return createWorkOrder(hit)
    }

    fun export(format: ReportExportFormat) {
        val report = _state.value?.report ?: return
        val payload = ReportExporter.export(report, format)
        val cur = _state.value ?: return
        _state.value = cur.copy(
            lastExport = payload,
            toastMessage = "Exported ${payload.fileName} (${payload.format})",
        )
    }

    fun consumeToast() {
        val cur = _state.value ?: return
        if (cur.toastMessage != null) {
            _state.value = cur.copy(toastMessage = null)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MaintenanceReportViewModel() as T
        }
    }
}
