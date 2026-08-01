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
 * UI-only Commissioning progress for Drawing Workspace (6 steps).
 * Building / Floor / Drawing / DWG are created before Workspace.
 */
object CommissioningWizardProgress {

    const val TOTAL_STEPS = 6

    enum class Step(val index: Int, val titleRes: Int) {
        ORIGIN(1, R.string.wiz_step_origin),
        CALIBRATION(2, R.string.wiz_step_calibration),
        ROUTE(3, R.string.wiz_step_route),
        ZONE(4, R.string.wiz_step_zone),
        VALIDATION(5, R.string.wiz_step_validation),
        ROUTE_LOCK(6, R.string.wiz_step_route_lock),
        ;

        companion object {
            fun fromIndex(index: Int): Step =
                entries.firstOrNull { it.index == index } ?: ORIGIN
        }
    }

    data class Snapshot(
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
        val drawing = catalog.drawings.current(context)
        val dwgOk = drawing?.dwgRegistered == true
        val originOk = drawing?.originSet == true
        val calibrationOk = drawing?.calibrationReady == true ||
            (drawing != null && catalog.calibrations.get(drawing.id)?.ready == true)
        val routeOk = drawing?.routeId != null && catalog.routes.hasRoute()
        val zoneCount = drawing?.let { catalog.zones.forDrawing(it.id).size } ?: 0
        val zoneOk = zoneCount > 0
        val validationOk = dwgOk && originOk && calibrationOk && routeOk && zoneOk
        val routeLocked = drawing?.routeLocked == true
        val completed = listOf(
            originOk, calibrationOk, routeOk, zoneOk, validationOk, routeLocked,
        ).count { it }
        return Snapshot(
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
        Step.ORIGIN -> snap.originOk
        Step.CALIBRATION -> snap.calibrationOk
        Step.ROUTE -> snap.routeOk
        Step.ZONE -> snap.zoneOk
        Step.VALIDATION -> snap.validationOk
        Step.ROUTE_LOCK -> snap.routeLocked
    }

    fun canAdvance(from: Step, snap: Snapshot): Boolean = isStepComplete(from, snap)

    fun firstIncompleteStep(snap: Snapshot): Step =
        Step.entries.firstOrNull { !isStepComplete(it, snap) } ?: Step.ROUTE_LOCK

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

    fun isZoneNameUnique(name: String, existing: List<Zone>): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return existing.none { it.name.equals(trimmed, ignoreCase = true) }
    }

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
