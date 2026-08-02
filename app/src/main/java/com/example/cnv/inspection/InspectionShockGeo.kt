package com.example.cnv.inspection

import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.map.Route

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
                ?: HeatMapRouteLayout.build(route, worldMapper = null)
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
        return Resolved(
            routePositionMm = routeMm,
            worldX = world.x.toFloat(),
            worldY = world.y.toFloat(),
            zoneName = zone,
        )
    }
}
