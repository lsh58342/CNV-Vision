package com.example.cnv.ui.screen.drawing

import android.content.Context
import com.example.cnv.R
import com.example.cnv.factory.model.Drawing
import com.example.cnv.ui.screen.commissioning.CommissioningWizardProgress

/**
 * UI-only Drawing status helpers for Floor cards / Workspace Overview.
 * Does not change Repository / ViewModel / Core logic.
 */
object DrawingUiStatus {

    fun stateOf(drawing: Drawing?, zoneCount: Int = 0): DrawingState =
        DrawingState.resolve(drawing, zoneCount = zoneCount)

    fun stateOfSnapshot(snap: CommissioningWizardProgress.Snapshot = CommissioningWizardProgress.snapshot()): DrawingState =
        DrawingState.resolveFromSnapshot(snap)

    fun summaryLabel(context: Context, drawing: Drawing, zoneCount: Int = 0): String =
        stateOf(drawing, zoneCount).label(context)

    fun dwgLabel(context: Context, ready: Boolean): String =
        if (ready) context.getString(R.string.draw_status_dwg_registered)
        else context.getString(R.string.draw_status_missing)

    fun calibrationLabel(context: Context, ready: Boolean): String =
        if (ready) context.getString(R.string.draw_status_calibration_completed)
        else context.getString(R.string.draw_status_missing)

    fun routeLabel(context: Context, ready: Boolean): String =
        if (ready) context.getString(R.string.draw_status_route_generated)
        else context.getString(R.string.draw_status_missing)

    fun zoneLabel(context: Context, zoneCount: Int): String =
        if (zoneCount <= 0) context.getString(R.string.draw_status_missing)
        else context.getString(R.string.draw_status_zone_created, zoneCount)

    fun inspectionLabel(context: Context, inspectionReady: Boolean): String =
        if (inspectionReady) context.getString(R.string.draw_status_inspection_ready)
        else context.getString(R.string.draw_status_not_ready)

    fun routeLockLabel(context: Context, locked: Boolean): String =
        if (locked) context.getString(R.string.draw_card_route_locked)
        else context.getString(R.string.draw_card_route_unlocked)

    /** Inspection only when READY_FOR_INSPECTION (Route Lock complete). */
    fun canStartInspection(): Boolean =
        stateOfSnapshot().canStartInspection()

    fun inspectionBlockReason(context: Context): String? {
        val res = stateOfSnapshot().inspectionBlockReasonRes() ?: return null
        return context.getString(res)
    }

    fun overviewHighlights(context: Context, snap: CommissioningWizardProgress.Snapshot): List<String> {
        val state = DrawingState.resolveFromSnapshot(snap)
        val lines = mutableListOf(state.label(context))
        if (snap.calibrationOk) {
            lines.add(context.getString(R.string.draw_overview_calibration_complete))
        }
        if (snap.routeLocked) {
            lines.add(context.getString(R.string.draw_overview_route_locked))
        } else if (snap.validationOk) {
            lines.add(context.getString(R.string.draw_overview_needs_lock))
        }
        return lines
    }

    fun fileNameFromUri(uri: String?): String {
        if (uri.isNullOrBlank()) return "—"
        val trimmed = uri.substringAfterLast('/').substringAfterLast(':')
        return trimmed.ifBlank { uri }
    }
}
