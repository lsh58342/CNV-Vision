package com.example.cnv.heatmap

import com.example.cnv.core.model.RouteDirection
import com.example.cnv.map.Route
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * Builds a Drawing-plane [CoordinateMapper] from Route topology (STEP 14).
 * Lays segments end-to-end along the preferred path — does not mutate Route.
 * Points outside known segments return null (no off-route heat).
 */
object HeatMapRouteLayout {

    data class LayoutResult(
        val mapper: CoordinateMapper,
        val segmentStartMm: Map<String, Float>,
        val totalLengthMm: Float,
    )

    fun build(route: Route, scale: Double = 1.0): LayoutResult? {
        if (route.segments.isEmpty()) return null
        val ordered = orderSegments(route)
        if (ordered.isEmpty()) return null

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
    ): RoutePosition = RoutePosition(
        segmentId = segmentId,
        nodeId = nodeId,
        distanceFromSegmentStart = distanceFromSegmentStart,
        progress = progress.coerceIn(0f, 1f),
        direction = RouteDirection.FORWARD,
        timestampNs = timestampNs,
        confidence = confidence,
    )

    /**
     * Resolve drawing coordinate; null if segment is not on the laid-out route.
     */
    fun toDrawingCoordinate(
        layout: LayoutResult,
        segmentId: String,
        progress: Float,
    ): WorldCoordinate? {
        if (!layout.segmentStartMm.containsKey(segmentId)) return null
        return layout.mapper.toWorld(
            toRoutePosition(
                segmentId = segmentId,
                nodeId = "",
                progress = progress,
                distanceFromSegmentStart = 0f,
                timestampNs = 0L,
                confidence = 1f,
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
        // Append any remaining segments (branches) without inventing topology connections.
        route.segments.forEach { s ->
            if (s.id !in visited) ordered.add(s.id)
        }
        return ordered
    }
}
