package com.example.cnv.ui.screen.report

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.ReplayMetadataRepository
import com.example.cnv.report.MaintenanceReport
import com.example.cnv.report.ReportMapBuilder
import com.example.cnv.report.ReportStorage
import com.example.cnv.report.ReportExportFormat
import com.example.cnv.report.ZoneIssueRow
import com.example.cnv.report.excel.ExcelExportUi
import com.example.cnv.report.excel.InspectionExcelExportService
import com.example.cnv.report.excel.InspectionExcelReportGenerator
import com.example.cnv.rule.RuleHit
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.review.HeatMapPreviewView
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Maintenance Report & Work Order (STEP 19 / 19-1).
 * Displays Analysis + Rule Results only — no recalculation.
 * Excel export uses Repository data via [InspectionExcelExportService].
 */
class MaintenanceReportScreen : BaseScreen() {

    private val vm: MaintenanceReportViewModel by viewModels { MaintenanceReportViewModel.Factory() }
    private val catalog = FactoryCatalog.get()
    private val excelExport = InspectionExcelExportService(catalog)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var sessionId: String? = null
    private var drawingId: String? = null
    private var pendingExcelFileName: String = InspectionExcelReportGenerator.defaultFileName()

    private val createExcelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val sid = sessionId ?: return@registerForActivityResult
        val did = drawingId ?: return@registerForActivityResult
        ExcelExportUi.takePersistablePermission(requireContext(), uri)
        Toast.makeText(requireContext(), R.string.excel_exporting, Toast.LENGTH_SHORT).show()
        excelExport.exportAsync(
            sessionId = sid,
            drawingId = did,
            targetUri = uri,
            contentResolver = requireContext().contentResolver,
            fileName = pendingExcelFileName,
        ) { exportResult ->
            if (!isAdded) return@exportAsync
            if (exportResult.success) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.excel_export_ok, exportResult.fileName.orEmpty()),
                    Toast.LENGTH_SHORT,
                ).show()
                view?.let { bindExcelArchiveButton(it, sid) }
            } else {
                Toast.makeText(
                    requireContext(),
                    exportResult.errorMessage ?: getString(R.string.excel_export_fail),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_maintenance_report, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionId = arguments?.getString(NavArgs.SESSION_ID)
        drawingId = arguments?.getString(NavArgs.DRAWING_ID)
        if (sessionId.isNullOrBlank() || drawingId.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }
        bindActions(view, drawingId!!, sessionId!!)
        vm.state.observe(viewLifecycleOwner) { state -> bindState(view, state) }
        vm.load(sessionId!!, drawingId!!)
        bindExcelArchiveButton(view, sessionId!!)
    }

    private fun bindActions(view: View, drawingId: String, sessionId: String) {
        view.findViewById<MaterialButton>(R.id.button_report_create_wo_priority).setOnClickListener {
            vm.createWorkOrderFromPriority()
        }
        view.findViewById<MaterialButton>(R.id.button_report_export_excel).setOnClickListener {
            pendingExcelFileName = InspectionExcelReportGenerator.defaultFileName()
            createExcelLauncher.launch(ExcelExportUi.createDocumentIntent(pendingExcelFileName))
        }
        view.findViewById<MaterialButton>(R.id.button_report_open_excel).setOnClickListener {
            openArchivedExcel(sessionId)
        }
        view.findViewById<MaterialButton>(R.id.button_report_open_critical_map).setOnClickListener {
            openCriticalMap(sessionId)
        }
        view.findViewById<MaterialButton>(R.id.button_report_export_pdf).setOnClickListener {
            vm.export(ReportExportFormat.PDF)
        }
        view.findViewById<MaterialButton>(R.id.button_report_export_csv).setOnClickListener {
            vm.export(ReportExportFormat.CSV)
        }
        view.findViewById<MaterialButton>(R.id.button_report_export_json).setOnClickListener {
            vm.export(ReportExportFormat.JSON)
        }
        view.findViewById<MaterialButton>(R.id.button_report_replay).setOnClickListener {
            catalog.replayMetadata.put(
                ReplayMetadataRepository.ReplayMeta(
                    drawingId = drawingId,
                    sessionId = sessionId,
                    label = "Replay · ${sessionId.take(8)}",
                ),
            )
            nav().navigate(
                CnvDestination.REPLAY,
                args = Bundle().apply {
                    putString(NavArgs.DRAWING_ID, drawingId)
                    putString(NavArgs.SESSION_ID, sessionId)
                },
            )
        }
        view.findViewById<MaterialButton>(R.id.button_report_back).setOnClickListener {
            nav().navigateBack()
        }
    }

    private fun bindExcelArchiveButton(view: View, sessionId: String) {
        val btn = view.findViewById<MaterialButton>(R.id.button_report_open_excel)
        val cached = catalog.excelArchives.get(sessionId)
        if (cached != null) {
            btn.isVisible = true
            btn.text = getString(R.string.report_open_excel_named, cached.fileName)
            return
        }
        btn.isVisible = false
        com.example.cnv.inspection.db.InspectionDbGate.submit(
            block = {
                catalog.excelArchives.getOrLoad(sessionId, catalog.inspections.underlying())
            },
            onMain = { entry ->
                if (entry != null) {
                    btn.isVisible = true
                    btn.text = getString(R.string.report_open_excel_named, entry.fileName)
                }
            },
        )
    }

    private fun openCriticalMap(sessionId: String) {
        val file = ReportStorage.criticalMapFile(sessionId)
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.excel_open_fail, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = ReportStorage.fileUri(file)
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/png")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure {
            Toast.makeText(requireContext(), R.string.excel_open_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openArchivedExcel(sessionId: String) {
        val entry = catalog.excelArchives.get(sessionId) ?: return
        val uri = Uri.parse(entry.fileUri)
        runCatching {
            startActivity(ExcelExportUi.openIntent(uri))
        }.onFailure {
            Toast.makeText(requireContext(), R.string.excel_open_fail, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindState(view: View, state: MaintenanceReportViewModel.UiState) {
        state.toastMessage?.let {
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }

        val status = view.findViewById<TextView>(R.id.report_status)
        when {
            state.loading -> status.setText(R.string.report_loading)
            state.errorMessage != null -> status.text = state.errorMessage
            else -> status.text = getString(
                R.string.report_ready,
                state.report?.reportVersion ?: 0,
            )
        }

        val report = state.report
        view.findViewById<TextView>(R.id.report_inspection_summary).text =
            if (report != null) formatInspectionSummary(report) else "—"
        view.findViewById<TextView>(R.id.report_maintenance_summary).text =
            if (report != null) formatMaintenanceSummary(report) else "—"
        view.findViewById<TextView>(R.id.report_priority_issue).text =
            if (report != null) formatPriority(report) else "—"
        view.findViewById<MaterialButton>(R.id.button_report_create_wo_priority).isEnabled =
            report?.priorityIssue != null

        bindZones(view, report?.zoneIssues.orEmpty())
        bindIssues(view, report?.issueDetails.orEmpty())
        bindWorkOrders(view, state)

        val criticalCount = state.criticalPointCount
        view.findViewById<HeatMapPreviewView>(R.id.report_heatmap_preview)
            .setMapData(state.heatPoints, state.routePolyline, criticalOnly = true)
        val mapLabel = if (state.criticalMapReady) "critical_shock_map.png" else "—"
        view.findViewById<TextView>(R.id.report_heatmap_meta).text =
            getString(R.string.report_heatmap_meta, criticalCount, mapLabel)
        view.findViewById<MaterialButton>(R.id.button_report_open_critical_map).isVisible =
            state.criticalMapReady

        view.findViewById<TextView>(R.id.report_actions).text =
            formatActions(report)
    }

    private fun formatInspectionSummary(r: MaintenanceReport): String {
        val date = if (r.inspectionDateMs > 0L) dateFmt.format(Date(r.inspectionDateMs)) else "—"
        val time = if (r.inspectionDateMs > 0L) timeFmt.format(Date(r.inspectionDateMs)) else "—"
        val avgSpeed = r.averageSpeedMmPerSec * 60f / 1000f
        return getString(
            R.string.report_inspection_format,
            date,
            time,
            formatDuration(r.durationMs),
            r.distanceMm,
            r.coverage * 100f,
            r.validationScore,
            avgSpeed,
            r.averageShock,
            r.maximumShock,
            r.shockCount,
        )
    }

    private fun formatMaintenanceSummary(r: MaintenanceReport): String {
        val s = r.maintenanceSummary
        return getString(
            R.string.report_maintenance_format,
            s.criticalCount,
            s.highCount,
            s.mediumCount,
            s.lowCount,
            s.inspectionGrade.name,
            s.overallStatus.name,
        )
    }

    private fun formatPriority(r: MaintenanceReport): String {
        val p = r.priorityIssue ?: return getString(R.string.report_priority_none)
        return getString(
            R.string.report_issue_detail_format,
            p.ruleId,
            p.ruleVersion,
            p.description,
            p.severity.name,
            p.recommendation.displayLabel(),
            tsFmt.format(Date(p.timestampMs)),
        )
    }

    private fun formatActions(report: MaintenanceReport?): String {
        if (report == null || report.recommendedActions.isEmpty()) {
            return getString(R.string.report_actions_none)
        }
        return report.recommendedActions.joinToString("\n") { a ->
            "• ${a.recommendation.displayLabel()} [${a.highestSeverity.name}]" +
                " — ${a.relatedRuleIds.joinToString()}"
        }
    }

    private fun bindZones(view: View, zones: List<ZoneIssueRow>) {
        val list = view.findViewById<LinearLayout>(R.id.report_zone_list)
        val empty = view.findViewById<TextView>(R.id.report_zone_empty)
        list.removeAllViews()
        if (zones.isEmpty()) {
            empty.isVisible = true
            return
        }
        empty.isVisible = false
        for (zone in zones) {
            val row = layoutInflater.inflate(R.layout.item_report_row, list, false)
            row.findViewById<TextView>(R.id.report_row_title).text = zone.zoneName
            row.findViewById<TextView>(R.id.report_row_body).text = getString(
                R.string.report_zone_format,
                zone.issueCount,
                zone.highestSeverity.name,
                zone.shockCount,
                zone.coverage * 100f,
                zone.validationScore,
            )
            list.addView(row)
        }
    }

    private fun bindIssues(view: View, issues: List<RuleHit>) {
        val list = view.findViewById<LinearLayout>(R.id.report_issue_list)
        val empty = view.findViewById<TextView>(R.id.report_issue_empty)
        list.removeAllViews()
        if (issues.isEmpty()) {
            empty.isVisible = true
            return
        }
        empty.isVisible = false
        for (hit in issues) {
            val row = layoutInflater.inflate(R.layout.item_report_row, list, false)
            row.findViewById<TextView>(R.id.report_row_title).text =
                "${hit.ruleId} · ${hit.severity.name}"
            row.findViewById<TextView>(R.id.report_row_body).text = getString(
                R.string.report_issue_detail_format,
                hit.ruleId,
                hit.ruleVersion,
                hit.description,
                hit.severity.name,
                hit.recommendation.displayLabel(),
                tsFmt.format(Date(hit.timestampMs)),
            )
            val action = row.findViewById<MaterialButton>(R.id.report_row_action)
            action.isVisible = true
            action.setOnClickListener { vm.createWorkOrder(hit) }
            list.addView(row)
        }
    }

    private fun bindWorkOrders(view: View, state: MaintenanceReportViewModel.UiState) {
        val list = view.findViewById<LinearLayout>(R.id.report_work_order_list)
        val empty = view.findViewById<TextView>(R.id.report_work_order_empty)
        list.removeAllViews()
        if (state.workOrders.isEmpty()) {
            empty.isVisible = true
            return
        }
        empty.isVisible = false
        for (wo in state.workOrders) {
            val row = layoutInflater.inflate(R.layout.item_report_row, list, false)
            row.findViewById<TextView>(R.id.report_row_title).text =
                "WO ${wo.workOrderId.take(8)} · ${wo.status.name}"
            row.findViewById<TextView>(R.id.report_row_body).text = getString(
                R.string.report_work_order_format,
                wo.buildingName.ifBlank { "—" },
                wo.floorName.ifBlank { "—" },
                wo.drawingName.ifBlank { wo.drawingId },
                wo.zoneName.ifBlank { "—" },
                wo.ruleId,
                wo.severity.name,
                wo.recommendation.displayLabel(),
                if (wo.inspectionDateMs > 0L) dateFmt.format(Date(wo.inspectionDateMs)) else "—",
                wo.status.name,
            )
            list.addView(row)
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
    }
}
