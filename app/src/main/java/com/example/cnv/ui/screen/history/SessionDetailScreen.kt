package com.example.cnv.ui.screen.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.camera.ShockClipStorage
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.ReplayMetadataRepository
import com.example.cnv.heatmap.HeatPointsCodec
import com.example.cnv.imu.ShockUnits
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.report.excel.ExcelExportUi
import com.example.cnv.report.excel.InspectionCsvExportService
import com.example.cnv.report.excel.InspectionExcelExportService
import com.example.cnv.report.excel.InspectionExcelReportGenerator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.inspection.ShockGraphState
import com.example.cnv.ui.screen.inspection.ShockGraphView
import com.example.cnv.ui.screen.review.HeatMapPreviewView
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspection Session Detail — summary / profile / shock history / export (STEP 20-18).
 */
class SessionDetailScreen : BaseScreen() {

    private val catalog = FactoryCatalog.get()
    private val csvExport = InspectionCsvExportService(catalog)
    private val excelExport = InspectionExcelExportService(catalog)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private var sessionId: String? = null
    private var drawingId: String? = null
    private var pendingCsvName = "inspection.csv"
    private var pendingExcelName = InspectionExcelReportGenerator.defaultFileName()

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val sid = sessionId ?: return@registerForActivityResult
        val did = drawingId ?: return@registerForActivityResult
        Toast.makeText(requireContext(), R.string.csv_exporting, Toast.LENGTH_SHORT).show()
        csvExport.exportAsync(
            sessionId = sid,
            drawingId = did,
            targetUri = uri,
            contentResolver = requireContext().contentResolver,
            fileName = pendingCsvName,
        ) { exportResult ->
            if (!isAdded) return@exportAsync
            Toast.makeText(
                requireContext(),
                if (exportResult.success) {
                    getString(R.string.csv_export_ok, exportResult.fileName.orEmpty())
                } else {
                    exportResult.errorMessage ?: getString(R.string.csv_export_fail)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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
            fileName = pendingExcelName,
        ) { exportResult ->
            if (!isAdded) return@exportAsync
            Toast.makeText(
                requireContext(),
                if (exportResult.success) {
                    getString(R.string.excel_export_ok, exportResult.fileName.orEmpty())
                } else {
                    exportResult.errorMessage ?: getString(R.string.excel_export_fail)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_session_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionId = arguments?.getString(NavArgs.SESSION_ID)
        drawingId = arguments?.getString(NavArgs.DRAWING_ID)
        val sid = sessionId
        val did = drawingId
        if (sid.isNullOrBlank() || did.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }

        catalog.inspections.loadSessionAsync(sid) { persisted ->
            if (!isAdded) return@loadSessionAsync
            val summary = persisted?.summary?.takeIf { it.drawingId == did }
            if (summary == null) {
                Toast.makeText(requireContext(), R.string.history_session_missing, Toast.LENGTH_SHORT).show()
                nav().navigateBack()
                return@loadSessionAsync
            }
            bindDetail(view, summary)
            bindShockGraph(view, persisted.events, summary)
            bindShockClips(view, persisted.events)
            bindHeatMap(view, summary, did)
            bindActions(view, did, sid)
            catalog.analysis.getOrAnalyzeAsync(sid, did) { analysis ->
                if (!isAdded || analysis == null) return@getOrAnalyzeAsync
                bindAnalysis(view, analysis)
            }
        }
    }

    private fun bindShockGraph(
        view: View,
        events: List<PersistedInspectionEvent>,
        summary: InspectionSessionSummary,
    ) {
        val fusion = events.filter { it.eventType == "FusionEvent" || it.hasShock }
        val samples = if (fusion.isEmpty()) {
            events.filter { it.hasShock }.map { it.shockStrength }
        } else {
            fusion.map { if (it.hasShock) it.shockStrength else 0f }
        }
        val snap = com.example.cnv.profile.InspectionProfileCodec.decodeSnapshot(
            summary.inspectionProfileJson,
        )
        val thr = ShockUnits.asThresholdG(snap.sensor.minimumShockThreshold)
            .takeIf { it > 0f }
            ?: ShockUnits.recordingThresholdG()
        val state = ShockGraphState.fromSamples(
            samples = samples,
            threshold = thr,
            current = samples.lastOrNull() ?: 0f,
            average = if (samples.isEmpty()) {
                0f
            } else {
                samples.filter { it >= thr }.ifEmpty { samples }.average().toFloat()
            },
            maximum = summary.maximumShock.takeIf { it > 0f }
                ?: samples.maxOrNull()
                ?: 0f,
        )
        view.findViewById<ShockGraphView>(R.id.detail_shock_graph).bind(state)
        println(
            "LOG[ShockGraph][HISTORY] points=${state.samples.size} " +
                "peaks=${state.peakIndices.size} thr=$thr " +
                "max=${"%.2f".format(state.maximum)} avg=${"%.2f".format(state.average)} " +
                "y=${state.samples.take(24).joinToString(",") { "%.2f".format(it) }}",
        )
        println(
            "LOG[STEP20-20][HISTORY] heatJsonEmpty=${summary.heatPointsJson.isBlank()} " +
                "shockEvents=${events.count { it.hasShock }} graphPoints=${state.samples.size} " +
                "historyLoad=true",
        )
    }

    private fun bindShockClips(view: View, events: List<PersistedInspectionEvent>) {
        val clips = events.filter { it.hasShock && it.clipPath.isNotBlank() }
        val button = view.findViewById<MaterialButton>(R.id.button_play_shock_clips)
        if (clips.isEmpty()) {
            button.isVisible = false
            return
        }
        button.isVisible = true
        button.text = getString(R.string.history_play_shock_clips) + " (${clips.size})"
        button.setOnClickListener {
            val labels = clips.map { event ->
                getString(
                    R.string.history_shock_clip_item,
                    event.peakG.coerceAtLeast(event.shockStrength),
                    dateFmt.format(Date(event.timestampNs / 1_000_000L)),
                )
            }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.history_play_shock_clips)
                .setItems(labels) { _, which ->
                    playClip(clips[which].clipPath)
                }
                .show()
        }
    }

    private fun playClip(path: String) {
        val file = ShockClipStorage.resolveClipFile(path)
        if (file == null) {
            Toast.makeText(requireContext(), R.string.history_clip_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(requireContext(), R.string.history_clip_missing, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindHeatMap(
        view: View,
        summary: InspectionSessionSummary,
        drawingId: String,
    ) {
        val preview = view.findViewById<HeatMapPreviewView>(R.id.detail_heatmap_preview)
        val meta = view.findViewById<TextView>(R.id.detail_heatmap_meta)
        val fromJson = HeatPointsCodec.decode(summary.heatPointsJson)
        if (fromJson.isNotEmpty()) {
            catalog.heatMaps.restoreSessionPoints(summary.sessionId, summary.heatPointsJson)
            preview.setHeatPoints(fromJson)
            meta.text = "HeatPoints=${fromJson.size} (session JSON)"
            println(
                "LOG[HeatMap][HISTORY] loaded=${fromJson.size} session=${summary.sessionId}",
            )
            return
        }
        // Fallback: regenerate from Room events + route snapshot when JSON missing.
        com.example.cnv.inspection.db.InspectionDbGate.execute {
            val session = catalog.inspections.loadSession(summary.sessionId)
            val route = session?.summary?.routeSnapshotJson?.let {
                com.example.cnv.inspection.RouteSnapshotCodec.decode(it)?.toRoute()
            } ?: catalog.routes.currentRoute()
            val mapper = catalog.routes.underlying().currentMapper()
            val points = if (session != null && route != null) {
                catalog.heatMaps.generateSessionPoints(drawingId, session, route, mapper)
            } else {
                emptyList()
            }
            if (points.isNotEmpty()) {
                // Persist recovered heat so next History open is instant.
                // finishSession path already stores JSON; this is repair-only.
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (!isAdded) return@post
                preview.setHeatPoints(points)
                meta.text = if (points.isEmpty()) {
                    "HeatMap unavailable — check session heatPointsJson / Route Snapshot"
                } else {
                    "HeatPoints=${points.size} (regenerated)"
                }
                println(
                    "LOG[HeatMap][HISTORY] regenerated=${points.size} " +
                        "session=${summary.sessionId} route=${route != null}",
                )
            }
        }
    }

    private fun bindAnalysis(view: View, a: com.example.cnv.analysis.InspectionAnalysisResult) {
        view.findViewById<TextView>(R.id.detail_statistics).text = getString(
            R.string.history_analysis_statistics,
            a.distance.totalDistanceMm,
            a.distance.averageDistanceMm,
            a.distance.maximumDeltaMm,
            a.distance.minimumDeltaMm,
            a.speed.averageSpeedMmPerSec,
            a.speed.maximumSpeedMmPerSec,
            a.speed.minimumSpeedMmPerSec,
            a.speed.nominalSpeedMPerMin ?: -1f,
            a.speed.speedDifferenceMmPerSec,
            a.tracking.averageConfidence,
            a.tracking.minimumConfidence,
            a.tracking.lowConfidenceCount,
            a.tracking.trackingLossCount,
            a.shock.shockCount,
            a.shock.maximumShock,
            a.shock.averageShock,
            a.shock.shockDensityPerMeter,
            a.coverage.drawingCoverage * 100f,
            a.coverage.routeCoverage * 100f,
            a.coverage.inspectionRatio * 100f,
            a.validationScore,
            a.zones.size,
        )
    }

    private fun bindActions(view: View, drawingId: String, sessionId: String) {
        view.findViewById<MaterialButton>(R.id.button_detail_excel).setOnClickListener {
            pendingExcelName = InspectionExcelReportGenerator.defaultFileName()
            createExcelLauncher.launch(ExcelExportUi.createDocumentIntent(pendingExcelName))
        }
        view.findViewById<MaterialButton>(R.id.button_detail_heatmap).setOnClickListener {
            val args = Bundle().apply {
                putString(NavArgs.DRAWING_ID, drawingId)
                putString(NavArgs.SESSION_ID, sessionId)
            }
            nav().navigate(CnvDestination.HEATMAP_VIEWER, args = args)
        }
        view.findViewById<MaterialButton>(R.id.button_detail_replay).setOnClickListener {
            catalog.replayMetadata.put(
                ReplayMetadataRepository.ReplayMeta(
                    drawingId = drawingId,
                    sessionId = sessionId,
                    label = "Replay · ${sessionId.take(8)}",
                ),
            )
            val args = Bundle().apply {
                putString(NavArgs.DRAWING_ID, drawingId)
                putString(NavArgs.SESSION_ID, sessionId)
            }
            nav().navigate(CnvDestination.REPLAY, args = args)
        }
        view.findViewById<MaterialButton>(R.id.button_detail_csv).setOnClickListener {
            pendingCsvName = InspectionCsvExportService.defaultFileName(sessionId)
            createCsvLauncher.launch(ExcelExportUi.createCsvDocumentIntent(pendingCsvName))
        }
        view.findViewById<MaterialButton>(R.id.button_detail_delete).setOnClickListener {
            confirmDelete(drawingId, sessionId)
        }
        view.findViewById<MaterialButton>(R.id.button_detail_back).setOnClickListener {
            nav().navigateBack()
        }
    }

    private fun bindDetail(view: View, s: InspectionSessionSummary) {
        val avgSpeedMPerMin = s.averageSpeedMmPerSec * 60f / 1000f
        view.findViewById<TextView>(R.id.detail_summary).text = getString(
            R.string.history_detail_summary,
            s.sessionId,
            dateFmt.format(Date(s.startTimeMs)),
            dateFmt.format(Date(s.endTimeMs.takeIf { it > 0L } ?: s.startTimeMs)),
            formatDuration(s.durationMs),
            s.totalDistanceMm,
            s.shockCount,
            s.coverage * 100f,
            avgSpeedMPerMin,
            s.speedValidation.validationScore,
            s.inspectionVersion,
            s.appVersion,
        )

        val conveyor = s.conveyorProfile
        val speed = conveyor.nominalSpeedMPerMin?.let { "%.2f m/min".format(it) }
            ?: getString(R.string.conveyor_nominal_unset)
        val snap = com.example.cnv.profile.InspectionProfileCodec.decodeSnapshot(
            s.inspectionProfileJson,
        )
        val sensor = snap.sensor
        val ruleSummary = if (snap.rule.entries.isEmpty()) {
            getString(R.string.profile_history_rules_empty)
        } else {
            snap.rule.entries.joinToString("\n") { e ->
                val thr = e.thresholdOverride?.toString() ?: "—"
                val sev = e.severityOverride?.name ?: "—"
                "${e.ruleId}: enabled=${e.enabled} thr=$thr sev=$sev"
            }
        }
        view.findViewById<TextView>(R.id.detail_profile).text = getString(
            R.string.history_detail_inspection_profile,
            speed,
            conveyor.speedTolerancePercent,
            conveyor.direction.name,
            conveyor.expectedFps,
            conveyor.motionProfile.name,
            sensor.gravityFilterAlpha,
            sensor.highPassAlpha,
            sensor.minimumShockThreshold,
            sensor.peakIntervalNs,
            sensor.movingAverageWindow,
            sensor.trackingConfidenceThreshold,
            snap.rule.catalogVersion,
            ruleSummary,
            snap.capturedAtMs.takeIf { it > 0L }?.let { dateFmt.format(Date(it)) } ?: "—",
        )

        view.findViewById<TextView>(R.id.detail_statistics).text = getString(
            R.string.history_detail_statistics,
            s.maximumShock,
            s.speedValidation.averageExpectedSpeedMPerMin,
            s.speedValidation.averageMeasuredSpeedMPerMin,
            s.speedValidation.maximumDifferenceMm,
            s.speedValidation.averageDifferenceMm,
            s.speedValidation.validationScore,
        )
    }

    private fun confirmDelete(drawingId: String, sessionId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.history_delete_session)
            .setMessage(R.string.history_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                catalog.deleteInspectionSessionAsync(drawingId, sessionId) {
                    if (!isAdded) return@deleteInspectionSessionAsync
                    Toast.makeText(requireContext(), R.string.history_deleted, Toast.LENGTH_SHORT).show()
                    nav().navigateBack()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }
}
