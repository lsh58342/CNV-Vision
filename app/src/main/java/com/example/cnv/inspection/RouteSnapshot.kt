package com.example.cnv.inspection

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteSegment

/**
 * Immutable frozen copy of a Route for inspection. Not editable.
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

    companion object {
        fun from(route: Route, capturedAtMs: Long = System.currentTimeMillis()): RouteSnapshot {
            val version = "v1-${route.id}"
            val hash = computeHash(route)
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
