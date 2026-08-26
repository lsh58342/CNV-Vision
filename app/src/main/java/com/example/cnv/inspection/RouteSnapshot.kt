package com.example.cnv.inspection

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteSegment
import com.example.cnv.route.CoordinateMapper

/**
 * Immutable frozen copy of a Route for inspection / Drawing persistence.
 * Optional [segmentGeometry] carries DXF/world endpoints so CAD/HeatMap never
 * collapse to a horizontal line after restore.
 */
data class RouteSnapshot(
    val routeId: String,
    val routeName: String,
    val routeVersion: String,
    val routeHash: String,
    val nodes: List<RouteNode>,
    val segments: List<RouteSegment>,
    val edges: List<RouteEdge>,
    val startNodeId: String,
    val startSegmentId: String,
    val capturedAtMs: Long,
    val segmentGeometry: Map<String, CoordinateMapper.SegmentGeometry> = emptyMap(),
) {
    fun toRoute(): Route = Route(
        id = routeId,
        name = routeName,
        nodes = nodes,
        segments = segments,
        edges = edges,
        startNodeId = startNodeId,
        startSegmentId = startSegmentId,
    )

    fun toMapper(): CoordinateMapper? {
        if (segmentGeometry.isEmpty()) return null
        // Accept partial geometry — better a partial map than a blank CAD canvas.
        if (segments.isNotEmpty() && !segments.all { it.id in segmentGeometry }) {
            println(
                "LOG[RouteSnapshot][MAPPER] partial geometry " +
                    "${segmentGeometry.size}/${segments.size} — using available segments",
            )
        }
        return CoordinateMapper(segmentGeometry = segmentGeometry)
    }

    companion object {
        fun from(
            route: Route,
            capturedAtMs: Long = System.currentTimeMillis(),
            mapper: CoordinateMapper? = null,
        ): RouteSnapshot {
            val version = "v1-${route.id}"
            val hash = computeHash(route)
            val geometry = LinkedHashMap<String, CoordinateMapper.SegmentGeometry>()
            if (mapper != null) {
                for (segment in route.segments) {
                    val start = mapper.toWorld(
                        com.example.cnv.map.RoutePosition(
                            segmentId = segment.id,
                            nodeId = segment.fromNodeId,
                            distanceFromSegmentStart = 0f,
                            progress = 0f,
                            direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                            timestampNs = 0L,
                            confidence = 1f,
                        ),
                    ) ?: continue
                    val end = mapper.toWorld(
                        com.example.cnv.map.RoutePosition(
                            segmentId = segment.id,
                            nodeId = segment.toNodeId,
                            distanceFromSegmentStart = segment.lengthMm,
                            progress = 1f,
                            direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                            timestampNs = 0L,
                            confidence = 1f,
                        ),
                    ) ?: continue
                    geometry[segment.id] = CoordinateMapper.SegmentGeometry(
                        segmentId = segment.id,
                        start = start,
                        end = end,
                    )
                }
            }
            return RouteSnapshot(
                routeId = route.id,
                routeName = route.name,
                routeVersion = version,
                routeHash = hash,
                nodes = route.nodes.toList(),
                segments = route.segments.toList(),
                edges = route.edges.toList(),
                startNodeId = route.startNodeId,
                startSegmentId = route.startSegmentId,
                capturedAtMs = capturedAtMs,
                segmentGeometry = geometry,
            )
        }

        fun computeHash(route: Route): String {
            val payload = buildString {
                append(route.id).append('|').append(route.name).append('|')
                append(route.startNodeId).append('|').append(route.startSegmentId).append('|')
                route.nodes.forEach { append(it.id).append(',').append(it.type).append(';') }
                route.segments.forEach {
                    append(it.id).append(',')
                        .append(it.fromNodeId).append(',')
                        .append(it.toNodeId).append(',')
                        .append(it.lengthMm).append(';')
                }
                route.edges.forEach {
                    append(it.id).append(',')
                        .append(it.segmentId).append(',')
                        .append(it.preferred).append(';')
                }
            }
            return payload.hashCode().toUInt().toString(16)
        }
    }
}
