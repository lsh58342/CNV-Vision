package com.example.cnv.cad

import com.example.cnv.map.Route
import com.example.cnv.route.WorldCoordinate
import kotlin.math.hypot

/**
 * Picks a Route Start Point (Origin) from a CAD view tap.
 * Projects onto the nearest route segment, then maps progress onto [Route.startSegmentId]
 * so Drawing.originX matches existing HeatMap / validation consumers.
 */
object RouteStartPointPicker {

    data class Pick(
        val progressOnStartSegment: Float,
        val world: WorldCoordinate,
        val nearestSegmentId: String,
        val nearestProgress: Float,
    )

    fun pick(
        viewX: Float,
        viewY: Float,
        route: Route,
        layout: CADRenderer.Layout,
        camera: CADCamera,
        maxDistancePx: Float = 48f,
    ): Pick? {
        val nearest = nearestOnRoute(viewX, viewY, layout, camera, maxDistancePx) ?: return null
        val startPair = layout.segmentWorld[route.startSegmentId]
            ?: return Pick(
                progressOnStartSegment = nearest.progress,
                world = nearest.world,
                nearestSegmentId = nearest.segmentId,
                nearestProgress = nearest.progress,
            )
        val progress = projectProgress(nearest.world, startPair)
        val worldOnStart = lerp(startPair.first, startPair.second, progress)
        return Pick(
            progressOnStartSegment = progress,
            world = worldOnStart,
            nearestSegmentId = nearest.segmentId,
            nearestProgress = nearest.progress,
        )
    }

    fun worldAtStartProgress(
        route: Route,
        layout: CADRenderer.Layout,
        progress: Float,
    ): WorldCoordinate? {
        val pair = layout.segmentWorld[route.startSegmentId] ?: return null
        return lerp(pair.first, pair.second, progress.coerceIn(0f, 1f))
    }

    private data class Nearest(
        val segmentId: String,
        val progress: Float,
        val world: WorldCoordinate,
        val distancePx: Float,
    )

    private fun nearestOnRoute(
        viewX: Float,
        viewY: Float,
        layout: CADRenderer.Layout,
        camera: CADCamera,
        maxDistancePx: Float,
    ): Nearest? {
        var best: Nearest? = null
        for ((id, pair) in layout.segmentWorld) {
            val x1 = camera.worldToViewX(pair.first.x)
            val y1 = camera.worldToViewY(pair.first.y)
            val x2 = camera.worldToViewX(pair.second.x)
            val y2 = camera.worldToViewY(pair.second.y)
            val projected = projectView(viewX, viewY, x1, y1, x2, y2)
            if (projected.distance > maxDistancePx) continue
            if (best == null || projected.distance < best.distancePx) {
                best = Nearest(
                    segmentId = id,
                    progress = projected.t,
                    world = lerp(pair.first, pair.second, projected.t),
                    distancePx = projected.distance,
                )
            }
        }
        return best
    }

    private fun projectProgress(
        world: WorldCoordinate,
        pair: Pair<WorldCoordinate, WorldCoordinate>,
    ): Float {
        val dx = pair.second.x - pair.first.x
        val dy = pair.second.y - pair.first.y
        if (dx == 0.0 && dy == 0.0) return 0f
        val t = ((world.x - pair.first.x) * dx + (world.y - pair.first.y) * dy) / (dx * dx + dy * dy)
        return t.toFloat().coerceIn(0f, 1f)
    }

    private fun projectView(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): ViewProjection {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            return ViewProjection(0f, hypot(px - x1, py - y1))
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val dist = hypot(px - (x1 + t * dx), py - (y1 + t * dy))
        return ViewProjection(t, dist)
    }

    private fun lerp(a: WorldCoordinate, b: WorldCoordinate, t: Float): WorldCoordinate {
        val tt = t.toDouble()
        return WorldCoordinate(
            x = a.x + (b.x - a.x) * tt,
            y = a.y + (b.y - a.y) * tt,
        )
    }

    private data class ViewProjection(val t: Float, val distance: Float)
}
