package com.example.cnv.ui.screen.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.repository.CsvMetadataRepository
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.ReplayMetadataRepository
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspection Session Detail — summary / profile / actions (STEP 15-3 / 15-4).
 * Session ids arrive via [NavArgs]; detail uses Session Snapshot only (not live Drawing profile).
 */
class SessionDetailScreen : BaseScreen() {

    private val catalog = FactoryCatalog.get()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_session_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sessionId = arguments?.getString(NavArgs.SESSION_ID)
        val drawingId = arguments?.getString(NavArgs.DRAWING_ID)
        if (sessionId.isNullOrBlank() || drawingId.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }

        catalog.inspections.loadSessionAsync(sessionId) { persisted ->
            if (!isAdded) return@loadSessionAsync
            val summary = persisted?.summary?.takeIf { it.drawingId == drawingId }
            if (summary == null) {
                Toast.makeText(requireContext(), R.string.history_session_missing, Toast.LENGTH_SHORT).show()
                nav().navigateBack()
                return@loadSessionAsync
            }
            bindDetail(view, summary)
            bindActions(view, drawingId, sessionId)
        }
    }

    private fun bindActions(view: View, drawingId: String, sessionId: String) {
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
            catalog.csvMetadata.put(
                CsvMetadataRepository.CsvMeta(
                    drawingId = drawingId,
                    label = "CSV export pending · $sessionId",
                ),
            )
            Toast.makeText(requireContext(), R.string.history_csv_export_toast, Toast.LENGTH_SHORT).show()
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

        // Always Session Snapshot — never live Drawing Conveyor Profile.
        val profile = s.conveyorProfile
        val speed = profile.nominalSpeedMPerMin?.let { "%.2f m/min".format(it) }
            ?: getString(R.string.conveyor_nominal_unset)
        view.findViewById<TextView>(R.id.detail_profile).text = getString(
            R.string.history_detail_profile,
            speed,
            profile.speedTolerancePercent,
            profile.direction.name,
            profile.expectedFps,
            profile.motionProfile.name,
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
