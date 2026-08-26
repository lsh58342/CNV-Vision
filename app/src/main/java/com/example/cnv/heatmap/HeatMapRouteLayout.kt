package com.example.cnv.heatmap

import com.example.cnv.core.model.RouteDirection
import com.example.cnv.map.Route
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * Builds a Drawing-plane layout from Route + optional world [CoordinateMapper].
 *
 * STEP 20-19: When [worldMapper] is provided, segment endpoints keep true DXF/world
 * geometry (ㄷ shape). The old Y=0 “straighten” fallback is used only when no mapper
 * is available.
 */
object HeatMapRouteLayout {

    data class LayoutResult(
        val mapper: CoordinateMapper,
        val segmentStartMm: Map<String, Float>,
        val totalLengthMm: Float,
    )

    fun build(
        route: Route,
        worldMapper: CoordinateMapper? = null,
        scale: Double = 1.0,
        allowHorizontalFallback: Boolean = false,
    ): LayoutResult? {
        if (route.segments.isEmpty()) return null
        val ordered = orderSegments(route)
        if (ordered.isEmpty()) return null

        if (worldMapper != null) {
            buildFromWorldMapper(route, ordered, worldMapper)?.let { return it }
            println(
                "LOG[HeatMapRouteLayout] worldMapper incomplete — " +
                    "refusing horizontal collapse for Inspection geometry",
            )
            // Prefer incomplete world geometry over forcing a horizontal line.
            return null
        }

        if (!allowHorizontalFallback) {
            println(
                "LOG[HeatMapRouteLayout] no worldMapper — refusing horizontal fallback " +
                    "(segments=${ordered.size})",
            )
            return null
        }
        return buildHorizontalFallback(route, ordered, scale)
    }

    /**
     * Preserve DXF / RouteGenerator world coordinates (ㄷ, corners, etc.).
     */
    private fun buildFromWorldMapper(
        route: Route,
        ordered: List<String>,
        worldMapper: CoordinateMapper,
    ): LayoutResult? {
        val geometry = LinkedHashMap<String, CoordinateMapper.SegmentGeometry>()
        val segmentStartMm = LinkedHashMap<String, Float>()
        var cursorMm = 0f
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (segmentId in ordered) {
            val segment = route.segment(segmentId) ?: continue
            val start = worldMapper.toWorld(position(segmentId, segment.fromNodeId, 0f, 0f))
            val end = worldMapper.toWorld(
                position(segmentId, segment.toNodeId, 1f, segment.lengthMm),
            )
            if (start == null || end == null) {
                println(
                    "LOG[HeatMapRouteLayout] skip $segmentId — missing world endpoints",
                )
                continue
            }
            geometry[segmentId] = CoordinateMapper.SegmentGeometry(
                segmentId = segmentId,
                start = start,
                end = end,
            )
            segmentStartMm[segmentId] = cursorMm
            cursorMm += segment.lengthMm
            minY = minOf(minY, start.y, end.y)
            maxY = maxOf(maxY, start.y, end.y)
            println(
                "LOG[HeatMapRouteLayout][WORLD] $segmentId " +
                    "(${start.x}, ${start.y}) → (${end.x}, ${end.y})",
            )
        }
        if (geometry.isEmpty()) return null
        println(
            "LOG[HeatMapRouteLayout][WORLD] segments=${geometry.size} " +
                "ySpan=${maxY - minY} (non-zero expected for ㄷ)",
        )
        return LayoutResult(
            mapper = CoordinateMapper(segmentGeometry = geometry),
            segmentStartMm = segmentStartMm,
            totalLengthMm = cursorMm,
        )
    }

    /** Legacy 1D unwrap — horizontal only. Do not use when Drawing geometry is required. */
    private fun buildHorizontalFallback(
        route: Route,
        ordered: List<String>,
        scale: Double,
    ): LayoutResult? {
        val geometry = LinkedHashMap<String, CoordinateMapper.SegmentGeometry>()
        val segmentStartMm = LinkedHashMap<String, Float>()
        var cursorX = 0.0
        var cursorMm = 0f

        for (segmentId in ordered) {
            val segment = route.segment(segmentId) ?: continue
            val start = WorldCoordinate(cursorX, 0.0)
            val lengthWorld = segment.lengthMm.toDouble() * scale
            val end = WorldCoordinate(cursorX + lengthWorld, 0.0)
            geometry[segmentId] = CoordinateMapper.SegmentGeometry(
                segmentId = segmentId,
                start = start,
                end = end,
            )
            segmentStartMm[segmentId] = cursorMm
            cursorX += lengthWorld
            cursorMm += segment.lengthMm
        }
        if (geometry.isEmpty()) return null
        println(
            "LOG[HeatMapRouteLayout][HORIZONTAL_FALLBACK] segments=${geometry.size} " +
                "(no worldMapper — route drawn as a line)",
        )
        return LayoutResult(
            mapper = CoordinateMapper(segmentGeometry = geometry),
            segmentStartMm = segmentStartMm,
            totalLengthMm = cursorMm,
        )
    }

    fun toRoutePosition(
        segmentId: String,
        nodeId: String,
        progress: Float,
        distanceFromSegmentStart: Float,
        timestampNs: Long,
        confidence: Float,
    ): RoutePosition = position(
        segmentId,
        nodeId,
        progress,
        distanceFromSegmentStart,
        timestampNs,
        confidence,
    )

    private fun position(
        segmentId: String,
        nodeId: String,
        progress: Float,
        distanceFromSegmentStart: Float,
        timestampNs: Long = 0L,
        confidence: Float = 1f,
    ): RoutePosition = RoutePosition(
        segmentId = segmentId,
        nodeId = nodeId,
        distanceFromSegmentStart = distanceFromSegmentStart,
        progress = progress.coerceIn(0f, 1f),
        direction = RouteDirection.FORWARD,
        timestampNs = timestampNs,
        confidence = confidence,
    )

    fun toDrawingCoordinate(
        layout: LayoutResult,
        segmentId: String,
        progress: Float,
    ): WorldCoordinate? {
        if (!layout.segmentStartMm.containsKey(segmentId)) return null
        return layout.mapper.toWorld(
            position(
                segmentId = segmentId,
                nodeId = "",
                progress = progress,
                distanceFromSegmentStart = 0f,
            ),
        )
    }

    fun absoluteRouteMm(layout: LayoutResult, segmentId: String, progress: Float): Float? {
        val start = layout.segmentStartMm[segmentId] ?: return null
        val entries = layout.segmentStartMm.entries.sortedBy { it.value }
        val idx = entries.indexOfFirst { it.key == segmentId }
        if (idx < 0) return null
        val endMm = entries.getOrNull(idx + 1)?.value ?: layout.totalLengthMm
        val length = (endMm - start).coerceAtLeast(0f)
        return start + length * progress.coerceIn(0f, 1f)
    }

    private fun orderSegments(route: Route): List<String> {
        val ordered = ArrayList<String>()
        val visited = HashSet<String>()
        var segmentId: String? = route.startSegmentId
        var guard = 0
        while (segmentId != null && segmentId !in visited && guard < route.segments.size + 2) {
            guard++
            visited.add(segmentId)
            ordered.add(segmentId)
            val seg = route.segment(segmentId) ?: break
            val nextEdge = route.preferredOutgoingEdge(seg.toNodeId)
            segmentId = nextEdge?.segmentId
            if (segmentId != null && segmentId in visited) break
        }
        route.segments.forEach { s ->
            if (s.id !in visited) ordered.add(s.id)
        }
        return ordered
    }
}
