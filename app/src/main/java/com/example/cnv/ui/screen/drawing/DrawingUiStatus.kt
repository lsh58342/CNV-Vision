package com.example.cnv.ui.screen.drawing

import android.content.Context
import com.example.cnv.R
import com.example.cnv.factory.model.Drawing

/**
 * UI-only Drawing status labels for List / Dashboard.
 * Does not change Repository / ViewModel / Core logic.
 */
object DrawingUiStatus {

    fun summaryLabel(context: Context, drawing: Drawing, zoneCount: Int = 0): String = when {
        !drawing.dwgRegistered -> context.getString(R.string.draw_status_dwg_required)
        !drawing.calibrationReady -> context.getString(R.string.draw_status_calibration_required)
        drawing.routeId == null -> context.getString(R.string.draw_status_route_required)
        zoneCount == 0 -> context.getString(R.string.draw_status_zone_required)
        drawing.routeLocked -> context.getString(R.string.draw_status_inspection_available)
        else -> context.getString(R.string.draw_status_ready)
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

    /** Phase 7: Inspection when DWG + Calibration + Route are complete. */
    fun canStartInspection(dwgReady: Boolean, calibrationReady: Boolean, routeReady: Boolean): Boolean =
        dwgReady && calibrationReady && routeReady

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
