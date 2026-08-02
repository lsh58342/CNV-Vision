package com.example.cnv.ui.screen.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.repository.CsvMetadataRepository
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.ReplayMetadataRepository
import com.example.cnv.rule.InspectionIssue
import com.example.cnv.rule.InspectionRuleZoneSummary
import com.example.cnv.rule.InspectionWarning
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspection Review & Action Center (STEP 17-1).
 * Displays Analysis Result + Rule Result + HeatMap Repository preview.
 * Does not analyze events, regenerate HeatMap, or re-run rules.
 */
class InspectionReviewScreen : BaseScreen() {

    private val vm: InspectionReviewViewModel by viewModels { InspectionReviewViewModel.Factory() }
    private val catalog = FactoryCatalog.get()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var sessionId: String? = null
    private var drawingId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_inspection_review, container, false)

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
    }

    private fun bindActions(view: View, drawingId: String, sessionId: String) {
        view.findViewById<MaterialButton>(R.id.button_review_replay).setOnClickListener {
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
        view.findViewById<MaterialButton>(R.id.button_review_heatmap).setOnClickListener {
            nav().navigate(
                CnvDestination.HEATMAP_VIEWER,
                args = Bundle().apply {
                    putString(NavArgs.DRAWING_ID, drawingId)
                    putString(NavArgs.SESSION_ID, sessionId)
                },
            )
        }
        view.findViewById<MaterialButton>(R.id.button_review_report).setOnClickListener {
            Toast.makeText(requireContext(), R.string.review_report_toast, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_review_csv).setOnClickListener {
            catalog.csvMetadata.put(
                CsvMetadataRepository.CsvMeta(
                    drawingId = drawingId,
                    label = "CSV export pending · $sessionId",
                ),
            )
            Toast.makeText(requireContext(), R.string.history_csv_export_toast, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_review_detail).setOnClickListener {
            nav().navigate(
                CnvDestination.INSPECTION_SESSION_DETAIL,
                args = Bundle().apply {
                    putString(NavArgs.DRAWING_ID, drawingId)
                    putString(NavArgs.SESSION_ID, sessionId)
                },
            )
        }
        view.findViewById<MaterialButton>(R.id.button_review_back).setOnClickListener {
            nav().navigateBack()
        }
    }

    private fun bindState(view: View, state: InspectionReviewViewModel.UiState) {
        val status = view.findViewById<TextView>(R.id.review_status)
        when {
            state.loading -> status.setText(R.string.review_loading)
            state.errorMessage != null -> status.text = state.errorMessage
            else -> status.text = getString(R.string.review_ready)
        }

        val analysis = state.analysis
        view.findViewById<TextView>(R.id.review_summary).text =
            if (analysis != null) formatSummary(analysis) else "—"

        view.findViewById<TextView>(R.id.review_warnings).text =
            formatWarnings(state.warnings)

        view.findViewById<HeatMapPreviewView>(R.id.review_heatmap_preview)
            .setHeatPoints(state.heatPoints)
        view.findViewById<TextView>(R.id.review_heatmap_meta).text =
            getString(R.string.review_heatmap_meta, state.heatPointCount)

        bindIssues(view, state.issues)
        bindZones(view, state.zoneSummaries)
    }

    private fun formatSummary(a: InspectionAnalysisResult): String {
        val start = a.summary.startTimeMs
        val date = if (start > 0L) dateFmt.format(Date(start)) else "—"
        val time = if (start > 0L) timeFmt.format(Date(start)) else "—"
        val avgSpeedMPerMin = a.speed.averageSpeedMmPerSec * 60f / 1000f
        return getString(
            R.string.review_summary_format,
            date,
            time,
            formatDuration(a.summary.durationMs),
            a.distance.totalDistanceMm,
            maxOf(a.coverage.drawingCoverage, a.coverage.routeCoverage) * 100f,
            a.validationScore,
            avgSpeedMPerMin,
            a.shock.maximumShock,
            a.shock.shockCount,
        )
    }

    private fun formatWarnings(warnings: List<InspectionWarning>): String {
        if (warnings.isEmpty()) return getString(R.string.review_warning_none)
        return warnings.joinToString("\n") { w ->
            "• ${w.label} [${w.severity.name}]" +
                if (w.detail.isNotBlank()) " — ${w.detail}" else ""
        }
    }

    private fun bindIssues(view: View, issues: List<InspectionIssue>) {
        val list = view.findViewById<LinearLayout>(R.id.review_issue_list)
        val empty = view.findViewById<TextView>(R.id.review_issue_empty)
        list.removeAllViews()
        if (issues.isEmpty()) {
            empty.isVisible = true
            return
        }
        empty.isVisible = false
        for (issue in issues) {
            val row = layoutInflater.inflate(R.layout.item_review_issue, list, false)
            row.findViewById<TextView>(R.id.review_issue_title).text = issue.zoneName
            row.findViewById<TextView>(R.id.review_issue_body).text = getString(
                R.string.review_issue_format,
                issue.issueType.name,
                issue.severity.name,
                issue.occurrenceCount,
            )
            list.addView(row)
        }
    }

    private fun bindZones(view: View, zones: List<InspectionRuleZoneSummary>) {
        val list = view.findViewById<LinearLayout>(R.id.review_zone_list)
        val empty = view.findViewById<TextView>(R.id.review_zone_empty)
        list.removeAllViews()
        if (zones.isEmpty()) {
            empty.isVisible = true
            return
        }
        empty.isVisible = false
        for (zone in zones) {
            val row = layoutInflater.inflate(R.layout.item_review_zone, list, false)
            row.findViewById<TextView>(R.id.review_zone_title).text = zone.zoneName
            row.findViewById<TextView>(R.id.review_zone_body).text = getString(
                R.string.review_zone_format,
                zone.distanceMm,
                zone.shockCount,
                zone.coverage * 100f,
                zone.validationScore,
            )
            list.addView(row)
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }
}
