package com.example.cnv.inspection

import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.map.Route
import com.example.cnv.vio.VioStateHub

/**
 * Session-scoped geometry helpers for Shock Event persistence (STEP 20-20).
 * Bound by InspectionPipeline during RUNNING — does not touch CAD / Route engines.
 */
object InspectionShockGeo {

    data class Resolved(
        val routePositionMm: Float,
        val worldX: Float,
        val worldY: Float,
        val zoneName: String,
        val segmentId: String = "",
        val headingDeg: Float = 0f,
        val distanceToRouteMm: Float = 0f,
        val trackingState: String = "",
    )

    @Volatile
    private var layout: HeatMapRouteLayout.LayoutResult? = null

    @Volatile
    private var zones: List<Pair<String, ClosedFloatingPointRange<Float>>> = emptyList()

    fun bind(
        route: Route?,
        worldMapper: com.example.cnv.route.CoordinateMapper?,
        zoneRanges: List<Pair<String, ClosedFloatingPointRange<Float>>> = emptyList(),
    ) {
        layout = if (route != null) {
            HeatMapRouteLayout.build(route, worldMapper = worldMapper)
        } else {
            null
        }
        zones = zoneRanges
    }

    fun clear() {
        layout = null
        zones = emptyList()
    }

    fun resolve(segmentId: String, progress: Float): Resolved? {
        val lay = layout ?: return null
        val routeMm = HeatMapRouteLayout.absoluteRouteMm(lay, segmentId, progress) ?: return null
        val world = HeatMapRouteLayout.toDrawingCoordinate(lay, segmentId, progress) ?: return null
        val zone = zones.firstOrNull { routeMm in it.second }?.first.orEmpty()
        val useHub = VioStateHub.segmentId == segmentId && VioStateHub.routeProgressMm > 0f
        return Resolved(
            routePositionMm = if (useHub) VioStateHub.routeProgressMm else routeMm,
            worldX = if (useHub) VioStateHub.projectedX.toFloat() else world.x.toFloat(),
            worldY = if (useHub) VioStateHub.projectedY.toFloat() else world.y.toFloat(),
            zoneName = zone,
            segmentId = segmentId,
            headingDeg = VioStateHub.deviceHeadingDeg,
            distanceToRouteMm = VioStateHub.distanceToRouteMm,
            trackingState = VioStateHub.quality.name,
        )
    }
}
