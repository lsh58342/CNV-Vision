package com.example.cnv.vio

import com.example.cnv.debug.TrackingAttitudeProbe
import com.example.cnv.map.Route
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Projects MapMatching route position onto DXF geometry and scores heading alignment.
 * Does not flatten the route onto a single X axis.
 */
class RouteConstrainedMatcher(
    private val config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
) {

    data class MatchResult(
        val segmentId: String,
        val projectedX: Double,
        val projectedY: Double,
        val routeProgressMm: Float,
        val distanceToRouteMm: Float,
        val routeHeadingDeg: Float,
        val deviceHeadingDeg: Float,
        val headingErrorDeg: Float,
        val progressAlongSegmentMm: Float,
        val segmentProgress: Float,
    )

    fun matchFromRoutePosition(
        route: Route,
        mapper: CoordinateMapper,
        position: RoutePosition,
        deviceHeadingDeg: Float,
        absoluteRouteMm: Float,
    ): MatchResult? {
        val segment = route.segment(position.segmentId) ?: return null
        val world = mapper.toWorld(position) ?: return null
        val start = mapper.toWorld(
            stubPosition(segment.id, segment.fromNodeId, 0f, 0f),
        ) ?: return null
        val end = mapper.toWorld(
            stubPosition(segment.id, segment.toNodeId, segment.lengthMm, 1f),
        ) ?: return null
        val routeHeading = TrackingAttitudeProbe.headingFromDelta(end.x - start.x, end.y - start.y)
        val headingError = abs(
            TrackingAttitudeProbe.normalizeDeg(deviceHeadingDeg - routeHeading),
        )
        val dist = pointToSegmentDistanceMm(world, start, end)
        return MatchResult(
            segmentId = position.segmentId,
            projectedX = world.x,
            projectedY = world.y,
            routeProgressMm = absoluteRouteMm,
            distanceToRouteMm = dist.toFloat(),
            routeHeadingDeg = routeHeading,
            deviceHeadingDeg = deviceHeadingDeg,
            headingErrorDeg = headingError,
            progressAlongSegmentMm = position.distanceFromSegmentStart,
            segmentProgress = position.progress,
        )
    }

    /**
     * Whether MapMatching should force-hop to the next segment (corner / ㄷ turn).
     */
    fun shouldForceNextSegment(
        route: Route,
        mapper: CoordinateMapper,
        currentSegmentId: String,
        progress: Float,
        deviceHeadingDeg: Float,
    ): Boolean {
        if (progress < config.cornerProgressThreshold) return false
        val current = route.segment(currentSegmentId) ?: return false
        val nextId = route.preferredOutgoingEdge(current.toNodeId)?.segmentId ?: return false
        val currentHeading = segmentHeading(route, mapper, currentSegmentId) ?: return false
        val nextHeading = segmentHeading(route, mapper, nextId) ?: return false
        val errCurrent = abs(
            TrackingAttitudeProbe.normalizeDeg(deviceHeadingDeg - currentHeading),
        )
        val errNext = abs(
            TrackingAttitudeProbe.normalizeDeg(deviceHeadingDeg - nextHeading),
        )
        return errCurrent >= config.cornerHeadingErrorCurrentDeg &&
            errNext <= config.cornerHeadingErrorNextDeg
    }

    fun segmentHeading(
        route: Route,
        mapper: CoordinateMapper,
        segmentId: String,
    ): Float? {
        val segment = route.segment(segmentId) ?: return null
        val start = mapper.toWorld(
            stubPosition(segment.id, segment.fromNodeId, 0f, 0f),
        ) ?: return null
        val end = mapper.toWorld(
            stubPosition(segment.id, segment.toNodeId, segment.lengthMm, 1f),
        ) ?: return null
        return TrackingAttitudeProbe.headingFromDelta(end.x - start.x, end.y - start.y)
    }

    private fun stubPosition(
        segmentId: String,
        nodeId: String,
        distanceFromStart: Float,
        progress: Float,
    ): RoutePosition = RoutePosition(
        segmentId = segmentId,
        nodeId = nodeId,
        distanceFromSegmentStart = distanceFromStart,
        progress = progress,
        direction = com.example.cnv.core.model.RouteDirection.FORWARD,
        timestampNs = 0L,
        confidence = 1f,
    )

    companion object {
        fun pointToSegmentDistanceMm(
            p: WorldCoordinate,
            a: WorldCoordinate,
            b: WorldCoordinate,
        ): Double {
            val abx = b.x - a.x
            val aby = b.y - a.y
            val len2 = abx * abx + aby * aby
            if (len2 < 1e-12) return hypot(p.x - a.x, p.y - a.y)
            var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2
            t = t.coerceIn(0.0, 1.0)
            val qx = a.x + abx * t
            val qy = a.y + aby * t
            return hypot(p.x - qx, p.y - qy)
        }
    }
}
