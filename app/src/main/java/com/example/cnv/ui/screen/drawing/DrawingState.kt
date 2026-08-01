package com.example.cnv.ui.screen.drawing

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.cnv.R
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.screen.commissioning.CommissioningWizardProgress

/**
 * UI-only Drawing lifecycle state.
 * Derived from Drawing flags — does not mutate Repository / Core.
 *
 * Transition:
 * NOT_CONFIGURED → DWG_IMPORTED → ORIGIN_SET → CALIBRATED →
 * ROUTE_CREATED → ZONE_CREATED → VALIDATED → LOCKED → READY_FOR_INSPECTION
 */
enum class DrawingState(
    @param:StringRes val labelRes: Int,
    @param:ColorRes val colorRes: Int,
) {
    NOT_CONFIGURED(R.string.draw_state_not_configured, R.color.cnv_state_gray),
    DWG_IMPORTED(R.string.draw_state_dwg_imported, R.color.cnv_state_blue),
    ORIGIN_SET(R.string.draw_state_origin_set, R.color.cnv_state_blue),
    CALIBRATED(R.string.draw_state_calibrated, R.color.cnv_state_yellow),
    ROUTE_CREATED(R.string.draw_state_route_created, R.color.cnv_state_yellow),
    ZONE_CREATED(R.string.draw_state_zone_created, R.color.cnv_state_orange),
    VALIDATED(R.string.draw_state_validated, R.color.cnv_state_green),
    LOCKED(R.string.draw_state_locked, R.color.cnv_state_green_dark),
    READY_FOR_INSPECTION(R.string.draw_state_ready_for_inspection, R.color.cnv_state_green),
    ;

    fun label(context: Context): String = context.getString(labelRes)

    fun color(context: Context): Int = context.getColor(colorRes)

    fun canStartInspection(): Boolean =
        this == READY_FOR_INSPECTION || this == LOCKED

    /** Why Inspection is blocked (null when ready). */
    @StringRes
    fun inspectionBlockReasonRes(): Int? = when (this) {
        NOT_CONFIGURED -> R.string.draw_block_dwg
        DWG_IMPORTED -> R.string.draw_block_origin
        ORIGIN_SET -> R.string.draw_block_calibration
        CALIBRATED -> R.string.draw_block_route
        ROUTE_CREATED -> R.string.draw_block_zone
        ZONE_CREATED -> R.string.draw_block_validation
        VALIDATED -> R.string.draw_block_lock
        LOCKED, READY_FOR_INSPECTION -> null
    }

    companion object {
        /**
         * Resolve current state from Drawing + zone count.
         * After Route Lock, state is READY_FOR_INSPECTION (LOCKED is the lock milestone).
         */
        fun resolve(drawing: Drawing?, zoneCount: Int = 0): DrawingState {
            if (drawing == null || !drawing.dwgRegistered) return NOT_CONFIGURED
            if (!drawing.originSet) return DWG_IMPORTED
            val calibrationReady = drawing.calibrationReady ||
                FactoryCatalog.get().calibrations.get(drawing.id)?.ready == true
            if (!calibrationReady) return ORIGIN_SET
            if (drawing.routeId == null) return CALIBRATED
            if (zoneCount <= 0) return ROUTE_CREATED
            if (!drawing.routeLocked) return VALIDATED
            return READY_FOR_INSPECTION
        }

        fun resolveFromSnapshot(snap: CommissioningWizardProgress.Snapshot): DrawingState {
            if (!snap.dwgOk) return NOT_CONFIGURED
            if (!snap.originOk) return DWG_IMPORTED
            if (!snap.calibrationOk) return ORIGIN_SET
            if (!snap.routeOk) return CALIBRATED
            if (!snap.zoneOk) return ROUTE_CREATED
            if (!snap.validationOk) return ZONE_CREATED
            if (!snap.routeLocked) return VALIDATED
            return READY_FOR_INSPECTION
        }
    }
}
