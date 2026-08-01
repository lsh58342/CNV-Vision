package com.example.cnv.ui.screen.drawing

import android.content.Context
import com.example.cnv.R
import com.example.cnv.factory.model.Drawing
import com.example.cnv.ui.screen.commissioning.CommissioningWizardProgress

/**
 * UI-only Drawing status labels for List / Dashboard.
 * Does not change Repository / ViewModel / Core logic.
 */
object DrawingUiStatus {

    fun summaryLabel(context: Context, drawing: Drawing, zoneCount: Int = 0): String = when {
        !drawing.dwgRegistered -> context.getString(R.string.draw_status_dwg_required)
        !drawing.originSet -> context.getString(R.string.draw_status_origin_required)
        !drawing.calibrationReady -> context.getString(R.string.draw_status_calibration_required)
        drawing.routeId == null -> context.getString(R.string.draw_status_route_required)
        zoneCount == 0 -> context.getString(R.string.draw_status_zone_required)
        !drawing.routeLocked -> context.getString(R.string.draw_status_ready)
        else -> context.getString(R.string.draw_status_inspection_available)
    }

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

    /**
     * Phase 9: Inspection requires full Wizard completion including Route Lock.
     */
    fun canStartInspection(): Boolean =
        CommissioningWizardProgress.canStartInspection(CommissioningWizardProgress.snapshot())

    @Deprecated("Use canStartInspection() Phase 9 gate")
    fun canStartInspection(dwgReady: Boolean, calibrationReady: Boolean, routeReady: Boolean): Boolean =
        canStartInspection()

    fun commissioningComplete(
        dwgReady: Boolean,
        calibrationReady: Boolean,
        routeReady: Boolean,
        zoneCount: Int,
        routeLocked: Boolean,
    ): Boolean = dwgReady && calibrationReady && routeReady && zoneCount > 0 && routeLocked

    fun fileNameFromUri(uri: String?): String {
        if (uri.isNullOrBlank()) return "—"
        val trimmed = uri.substringAfterLast('/').substringAfterLast(':')
        return trimmed.ifBlank { uri }
    }
}
