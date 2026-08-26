package com.example.cnv.vio

import com.example.cnv.core.model.RouteDirection
import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RoutePosition
import com.example.cnv.map.RouteSegment
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ㄷ-shaped route projection + corner force-hop conditions.
 */
class RouteConstrainedMatcherTest {

    @Test
    fun matchProjectsOntoUShapeGeometry() {
        val route = uShapeRoute()
        val mapper = uShapeMapper()
        val matcher = RouteConstrainedMatcher()
        val pos = RoutePosition(
            segmentId = "s-1",
            nodeId = "n-1",
            distanceFromSegmentStart = 50f,
            progress = 0.5f,
            direction = RouteDirection.FORWARD,
            timestampNs = 0L,
            confidence = 1f,
        )
        val match = matcher.matchFromRoutePosition(
            route = route,
            mapper = mapper,
            position = pos,
            deviceHeadingDeg = -90f,
            absoluteRouteMm = 150f,
        )
        assertNotNull(match)
        // Mid of vertical segment: X=100, Y=50 (not collapsed).
        assertEquals(100.0, match!!.projectedX, 1.0)
        assertEquals(50.0, match.projectedY, 1.0)
        assertEquals(150f, match.routeProgressMm, 0.1f)
        assertTrue(match.headingErrorDeg < 15f)
    }

    @Test
    fun forceHopWhenHeadingPrefersNextAtCorner() {
        val route = uShapeRoute()
        val mapper = uShapeMapper()
        val matcher = RouteConstrainedMatcher()
        // Near end of horizontal s-0, device yawed toward downward vertical (-90°).
        assertTrue(
            matcher.shouldForceNextSegment(
                route = route,
                mapper = mapper,
                currentSegmentId = "s-0",
                progress = 0.90f,
                deviceHeadingDeg = -90f,
            ),
        )
        assertFalse(
            matcher.shouldForceNextSegment(
                route = route,
                mapper = mapper,
                currentSegmentId = "s-0",
                progress = 0.90f,
                deviceHeadingDeg = 0f,
            ),
        )
    }

    private fun uShapeRoute(): Route {
        val nodes = listOf(
            RouteNode("n-0", RouteNodeType.START, "n0"),
            RouteNode("n-1", RouteNodeType.WAYPOINT, "n1"),
            RouteNode("n-2", RouteNodeType.WAYPOINT, "n2"),
            RouteNode("n-3", RouteNodeType.END, "n3"),
        )
        val segments = listOf(
            RouteSegment("s-0", "n-0", "n-1", 100f),
            RouteSegment("s-1", "n-1", "n-2", 100f),
            RouteSegment("s-2", "n-2", "n-3", 100f),
        )
        val edges = listOf(
            RouteEdge("e-0", "n-0", "n-1", "s-0", preferred = true),
            RouteEdge("e-1", "n-1", "n-2", "s-1", preferred = true),
            RouteEdge("e-2", "n-2", "n-3", "s-2", preferred = true),
        )
        return Route(
            id = "u-test",
            name = "u",
            nodes = nodes,
            segments = segments,
            edges = edges,
            startNodeId = "n-0",
            startSegmentId = "s-0",
        )
    }

    private fun uShapeMapper(): CoordinateMapper {
        // ┌── s-0 ──┐
        // │         s-1 (down)
        // └── s-2 ──┘
        return CoordinateMapper(
            segmentGeometry = mapOf(
                "s-0" to CoordinateMapper.SegmentGeometry(
                    "s-0",
                    WorldCoordinate(0.0, 100.0),
                    WorldCoordinate(100.0, 100.0),
                ),
                "s-1" to CoordinateMapper.SegmentGeometry(
                    "s-1",
                    WorldCoordinate(100.0, 100.0),
                    WorldCoordinate(100.0, 0.0),
                ),
                "s-2" to CoordinateMapper.SegmentGeometry(
                    "s-2",
                    WorldCoordinate(100.0, 0.0),
                    WorldCoordinate(0.0, 0.0),
                ),
            ),
        )
    }
}
