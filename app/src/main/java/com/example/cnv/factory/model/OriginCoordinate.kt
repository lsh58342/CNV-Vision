package com.example.cnv.factory.model

import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.map.Route
import com.example.cnv.route.WorldCoordinate
import kotlin.math.abs

/**
 * Drawing Origin storage helpers (STEP 20-23).
 *
 * New format: [Drawing.originX] / [Drawing.originY] = world drawing coordinates.
 * Legacy format (STEP ≤20-22): equal values in \[0,1\] meant progress on [Route.startSegmentId].
 */
object OriginCoordinate {

    fun isLegacyProgressPair(originX: Float?, originY: Float?): Boolean {
        if (originX == null || originY == null) return false
        if (originX !in 0f..1f || originY !in 0f..1f) return false
        return abs(originX - originY) < 1e-5f
    }

    fun resolveWorld(
        drawing: Drawing,
        route: Route?,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): Pair<Double, Double>? {
        if (!drawing.originSet) return null
        val ox = drawing.originX ?: return null
        val oy = drawing.originY ?: return null
        if (isLegacyProgressPair(ox, oy) && route != null && layout != null) {
            val world = HeatMapRouteLayout.toDrawingCoordinate(
                layout,
                route.startSegmentId,
                ox.coerceIn(0f, 1f),
            ) ?: return null
            return world.x to world.y
        }
        return ox.toDouble() to oy.toDouble()
    }

    fun resolveWorldCoordinate(
        drawing: Drawing,
        route: Route?,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): WorldCoordinate? {
        val pair = resolveWorld(drawing, route, layout) ?: return null
        return WorldCoordinate(pair.first, pair.second)
    }
}
