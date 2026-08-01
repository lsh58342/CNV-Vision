package com.example.cnv.map

import com.example.cnv.core.model.RouteDirection
import kotlin.math.abs

/**
 * Rule-based progress along [Route] using incremental travel distance (mm).
 * No GPS / PLC / AI.
 */
class PositionEstimator(
    private val config: MapConfig = MapConfig.DEFAULT,
) {

    private data class State(
        val segmentId: String,
        val distanceFromSegmentStart: Float,
        val direction: RouteDirection,
    )

    private var state: State? = null

    fun reset(route: Route) {
        state = State(
            segmentId = route.startSegmentId,
            distanceFromSegmentStart = 0f,
            direction = RouteDirection.FORWARD,
        )
    }

    fun clear() {
        state = null
    }

    /**
     * @param deltaDistanceMm signed travel from Fusion (positive ≈ forward along camera motion).
     */
    fun estimate(
        route: Route,
        deltaDistanceMm: Float,
        timestampNs: Long,
        confidence: Float,
    ): RoutePosition? {
        if (confidence < config.minimumConfidence) {
            return null
        }
        val current = ensureState(route)

        val direction = when {
            deltaDistanceMm < 0f -> RouteDirection.BACKWARD
            else -> RouteDirection.FORWARD
        }
        val travel = abs(deltaDistanceMm)
        if (travel <= 0f) {
            return buildPosition(route, current.copy(direction = direction), timestampNs, confidence)
        }

        val advanced = if (direction == RouteDirection.FORWARD) {
            advanceForward(route, current, travel)
        } else {
            advanceBackward(route, current, travel)
        } ?: return null

        state = advanced
        return buildPosition(route, advanced, timestampNs, confidence)
    }

    private fun ensureState(route: Route): State {
        val existing = state
        if (existing != null) return existing
        reset(route)
        return state ?: State(
            segmentId = route.startSegmentId,
            distanceFromSegmentStart = 0f,
            direction = RouteDirection.FORWARD,
        )
    }

    private fun advanceForward(route: Route, current: State, travelMm: Float): State? {
        var remaining = travelMm
        var segmentId = current.segmentId
        var distance = current.distanceFromSegmentStart

        var guard = 0
        while (remaining > 0f && guard < MAX_SEGMENT_HOPS) {
            guard++
            val segment = route.segment(segmentId) ?: return null
            val room = (segment.lengthMm - distance).coerceAtLeast(0f)

            if (remaining <= room + config.matchingToleranceMm) {
                val proposed = distance + remaining
                if (proposed - segment.lengthMm > config.maximumPositionErrorMm) {
                    return null
                }
                return State(
                    segmentId = segmentId,
                    distanceFromSegmentStart = proposed.coerceIn(0f, segment.lengthMm),
                    direction = RouteDirection.FORWARD,
                )
            }

            remaining -= room
            val nextEdge = route.preferredOutgoingEdge(segment.toNodeId)
            if (nextEdge == null || nextEdge.segmentId == segmentId) {
                return State(segmentId, segment.lengthMm, RouteDirection.FORWARD)
            }
            if (remaining > 0f && remaining <= config.branchToleranceMm) {
                remaining = 0f
            }
            segmentId = nextEdge.segmentId
            distance = 0f
        }
        return State(segmentId, distance, RouteDirection.FORWARD)
    }

    private fun advanceBackward(route: Route, current: State, travelMm: Float): State? {
        var remaining = travelMm
        var segmentId = current.segmentId
        var distance = current.distanceFromSegmentStart

        var guard = 0
        while (remaining > 0f && guard < MAX_SEGMENT_HOPS) {
            guard++
            val segment = route.segment(segmentId) ?: return null
            if (remaining <= distance + config.matchingToleranceMm) {
                val nextDistance = (distance - remaining).coerceAtLeast(0f)
                return State(segmentId, nextDistance, RouteDirection.BACKWARD)
            }

            remaining -= distance.coerceAtLeast(0f)
            val previous = findIncomingPreferred(route, segment.fromNodeId) ?: return State(
                segmentId,
                0f,
                RouteDirection.BACKWARD,
            )
            val prevSegment = route.segment(previous.segmentId) ?: return null
            segmentId = previous.segmentId
            distance = prevSegment.lengthMm
            if (remaining <= config.nodeRadiusMm) {
                remaining = 0f
            }
        }
        return State(segmentId, distance, RouteDirection.BACKWARD)
    }

    private fun findIncomingPreferred(route: Route, toNodeId: String): RouteEdge? {
        val incoming = route.edges.filter { it.toNodeId == toNodeId }
        return incoming.firstOrNull { it.preferred } ?: incoming.firstOrNull()
    }

    private fun buildPosition(
        route: Route,
        state: State,
        timestampNs: Long,
        confidence: Float,
    ): RoutePosition? {
        val segment = route.segment(state.segmentId) ?: return null
        val progress = if (segment.lengthMm <= 0f) {
            0f
        } else {
            (state.distanceFromSegmentStart / segment.lengthMm).coerceIn(0f, 1f)
        }
        val nearStart = state.distanceFromSegmentStart <= config.nodeRadiusMm
        val nearEnd =
            segment.lengthMm - state.distanceFromSegmentStart <= config.nodeRadiusMm
        val nodeId = when {
            nearStart -> segment.fromNodeId
            nearEnd -> segment.toNodeId
            else -> segment.fromNodeId
        }
        return RoutePosition(
            segmentId = state.segmentId,
            nodeId = nodeId,
            distanceFromSegmentStart = state.distanceFromSegmentStart,
            progress = progress,
            direction = state.direction,
            timestampNs = timestampNs,
            confidence = confidence,
        )
    }

    companion object {
        private const val MAX_SEGMENT_HOPS = 32
    }
}
