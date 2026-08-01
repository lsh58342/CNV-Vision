package com.example.cnv.ui.screen.commissioning

import android.content.Context
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper

/**
 * UI-only Commissioning Wizard progress / validation.
 * Does not modify Repository / ViewModel / Core logic.
 */
object CommissioningWizardProgress {

    const val TOTAL_STEPS = 9

    enum class Step(val index: Int, val titleRes: Int) {
        BUILDING(1, R.string.wiz_step_building),
        FLOOR(2, R.string.wiz_step_floor),
        DRAWING(3, R.string.wiz_step_drawing),
        DWG(4, R.string.wiz_step_dwg),
        ORIGIN(5, R.string.wiz_step_origin),
        CALIBRATION(6, R.string.wiz_step_calibration),
        ROUTE(7, R.string.wiz_step_route),
        ZONE(8, R.string.wiz_step_zone),
        VALIDATION(9, R.string.wiz_step_validation),
        ;

        companion object {
            fun fromIndex(index: Int): Step =
                entries.firstOrNull { it.index == index } ?: BUILDING
        }
    }

    data class Snapshot(
        val buildingOk: Boolean,
        val floorOk: Boolean,
        val drawingOk: Boolean,
        val dwgOk: Boolean,
        val originOk: Boolean,
        val calibrationOk: Boolean,
        val routeOk: Boolean,
        val zoneOk: Boolean,
        val validationOk: Boolean,
        val routeLocked: Boolean,
        val completedCount: Int,
        val drawing: Drawing?,
        val zoneCount: Int,
    )

    fun snapshot(
        catalog: FactoryCatalog = FactoryCatalog.get(),
        context: CurrentContext = CurrentContext.get(),
    ): Snapshot {
        val buildingOk = context.buildingId != null && catalog.buildings.current(context) != null
        val floorOk = context.floorId != null && catalog.floors.current(context) != null
        val drawing = catalog.drawings.current(context)
        val drawingOk = drawing != null
        val dwgOk = drawing?.dwgRegistered == true
        val originOk = drawing?.originSet == true
        val calibrationOk = drawing?.calibrationReady == true ||
            (drawing != null && catalog.calibrations.get(drawing.id)?.ready == true)
        val routeOk = drawing?.routeId != null && catalog.routes.hasRoute()
        val zoneCount = drawing?.let { catalog.zones.forDrawing(it.id).size } ?: 0
        val zoneOk = zoneCount > 0
        val validationOk = buildingOk && floorOk && drawingOk && dwgOk &&
            originOk && calibrationOk && routeOk && zoneOk
        val routeLocked = drawing?.routeLocked == true
        // completedCount: steps 1-8 by flags, step 9 complete when validationOk && routeLocked
        val completed = listOf(
            buildingOk, floorOk, drawingOk, dwgOk, originOk,
            calibrationOk, routeOk, zoneOk,
        ).count { it } + if (validationOk && routeLocked) 1 else 0
        return Snapshot(
            buildingOk = buildingOk,
            floorOk = floorOk,
            drawingOk = drawingOk,
            dwgOk = dwgOk,
            originOk = originOk,
            calibrationOk = calibrationOk,
            routeOk = routeOk,
            zoneOk = zoneOk,
            validationOk = validationOk,
            routeLocked = routeLocked,
            completedCount = completed.coerceAtMost(TOTAL_STEPS),
            drawing = drawing,
            zoneCount = zoneCount,
        )
    }

    fun isStepComplete(step: Step, snap: Snapshot): Boolean = when (step) {
        Step.BUILDING -> snap.buildingOk
        Step.FLOOR -> snap.floorOk
        Step.DRAWING -> snap.drawingOk
        Step.DWG -> snap.dwgOk
        Step.ORIGIN -> snap.originOk
        Step.CALIBRATION -> snap.calibrationOk
        Step.ROUTE -> snap.routeOk
        Step.ZONE -> snap.zoneOk
        Step.VALIDATION -> snap.validationOk
    }

    /** Next is allowed only when current step is complete. */
    fun canAdvance(from: Step, snap: Snapshot): Boolean = isStepComplete(from, snap)

    fun firstIncompleteStep(snap: Snapshot): Step =
        Step.entries.firstOrNull { !isStepComplete(it, snap) } ?: Step.VALIDATION

    fun progressLabel(context: Context, snap: Snapshot): String =
        context.getString(R.string.wiz_progress_format, snap.completedCount, TOTAL_STEPS)

    fun validationFailures(context: Context, snap: Snapshot): List<String> {
        val out = mutableListOf<String>()
        if (!snap.dwgOk) out.add(context.getString(R.string.wiz_fail_dwg))
        if (!snap.originOk) out.add(context.getString(R.string.wiz_fail_origin))
        if (!snap.calibrationOk) out.add(context.getString(R.string.wiz_fail_calibration))
        if (!snap.routeOk) out.add(context.getString(R.string.wiz_fail_route))
        if (!snap.zoneOk) out.add(context.getString(R.string.wiz_fail_zone))
        return out
    }

    fun canStartInspection(snap: Snapshot): Boolean =
        snap.validationOk && snap.routeLocked

    fun canLockRoute(snap: Snapshot): Boolean =
        snap.validationOk && !snap.routeLocked

    /** Zone name unique on Drawing (UI check before save). */
    fun isZoneNameUnique(name: String, existing: List<Zone>): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return existing.none { it.name.equals(trimmed, ignoreCase = true) }
    }

    /** Overlap if segment highlight sets intersect (UI check). */
    fun zonesOverlap(
        catalog: FactoryCatalog,
        start: RouteAnchor,
        end: RouteAnchor,
        existing: List<Zone>,
    ): Boolean {
        val route = catalog.routes.currentRoute() ?: return false
        val next = RouteHighlightHelper.segmentIdsBetween(route, start, end)
        if (next.isEmpty()) return false
        return existing.any { z ->
            val segs = RouteHighlightHelper.segmentIdsBetween(route, z.start, z.end)
            segs.intersect(next).isNotEmpty()
        }
    }

    fun zoneOnRoute(start: RouteAnchor, end: RouteAnchor): Boolean =
        start.isDefined() && end.isDefined()
}
