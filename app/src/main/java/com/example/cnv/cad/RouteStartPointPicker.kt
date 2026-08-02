package com.example.cnv.cad

import com.example.cnv.map.Route
import com.example.cnv.route.WorldCoordinate
import kotlin.math.hypot

/**
 * Picks Drawing Origin from a CAD view tap (STEP 20-23).
 *
 * Origin = **nearest point on any Route segment** to the touch (not Route start,
 * not remapped onto [Route.startSegmentId]).
 * Off-route taps still snap to the nearest segment point.
 */
object RouteStartPointPicker {

    data class Pick(
        val world: WorldCoordinate,
        val nearestSegmentId: String,
        val nearestSegmentIndex: Int,
        val nearestProgress: Float,
        val nearestDistancePx: Float,
        val screenX: Float,
        val screenY: Float,
    )

    fun pick(
        viewX: Float,
        viewY: Float,
        route: Route,
        layout: CADRenderer.Layout,
        camera: CADCamera,
        /** Unused for gating — kept for API compat; off-route always snaps to nearest. */
        maxDistancePx: Float = Float.MAX_VALUE,
    ): Pick? {
        val nearest = nearestOnRoute(viewX, viewY, route, layout, camera) ?: return null
        println(
            "LOG[OriginPick][HIT]\n" +
                "Screen X=${"%.1f".format(viewX)}\n" +
                "Screen Y=${"%.1f".format(viewY)}\n" +
                "World X=${"%.2f".format(nearest.world.x)}\n" +
                "World Y=${"%.2f".format(nearest.world.y)}\n" +
                "Nearest Segment Index=${nearest.segmentIndex}\n" +
                "Nearest Segment Id=${nearest.segmentId}\n" +
                "Nearest Progress=${"%.3f".format(nearest.progress)}\n" +
                "Nearest Distance=${"%.1f".format(nearest.distancePx)}px",
        )
        // maxDistancePx reserved for future soft UX hints only.
        @Suppress("UNUSED_EXPRESSION")
        maxDistancePx
        return Pick(
            world = nearest.world,
            nearestSegmentId = nearest.segmentId,
            nearestSegmentIndex = nearest.segmentIndex,
            nearestProgress = nearest.progress,
            nearestDistancePx = nearest.distancePx,
            screenX = viewX,
            screenY = viewY,
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
        val segmentIndex: Int,
        val progress: Float,
        val world: WorldCoordinate,
        val distancePx: Float,
    )

    private fun nearestOnRoute(
        viewX: Float,
        viewY: Float,
        route: Route,
        layout: CADRenderer.Layout,
        camera: CADCamera,
    ): Nearest? {
        var best: Nearest? = null
        val orderedIds = orderedSegmentIds(route, layout)
        orderedIds.forEachIndexed { index, id ->
            val pair = layout.segmentWorld[id] ?: return@forEachIndexed
            val x1 = camera.worldToViewX(pair.first.x)
            val y1 = camera.worldToViewY(pair.first.y)
            val x2 = camera.worldToViewX(pair.second.x)
            val y2 = camera.worldToViewY(pair.second.y)
            val projected = projectView(viewX, viewY, x1, y1, x2, y2)
            if (best == null || projected.distance < best!!.distancePx) {
                best = Nearest(
                    segmentId = id,
                    segmentIndex = index,
                    progress = projected.t,
                    world = lerp(pair.first, pair.second, projected.t),
                    distancePx = projected.distance,
                )
            }
        }
        return best
    }

    private fun orderedSegmentIds(route: Route, layout: CADRenderer.Layout): List<String> {
        val ordered = ArrayList<String>()
        val visited = HashSet<String>()
        var segmentId: String? = route.startSegmentId
        var guard = 0
        while (segmentId != null && segmentId !in visited && guard < route.segments.size + 2) {
            guard++
            visited.add(segmentId)
            if (layout.segmentWorld.containsKey(segmentId)) {
                ordered.add(segmentId)
            }
            val seg = route.segment(segmentId) ?: break
            segmentId = route.preferredOutgoingEdge(seg.toNodeId)?.segmentId
        }
        for (id in layout.segmentWorld.keys) {
            if (id !in visited) ordered.add(id)
        }
        return ordered
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
