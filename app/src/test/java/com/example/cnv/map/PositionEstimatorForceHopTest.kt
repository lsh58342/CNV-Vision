package com.example.cnv.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * VIO corner: forceNextSegment hops even with zero travel delta.
 */
class PositionEstimatorForceHopTest {

    @Test
    fun forceNextSegmentHopsWithoutTravel() {
        val route = threeSegmentRoute(100f)
        val estimator = PositionEstimator()
        estimator.reset(route)
        estimator.estimate(route, 50f, 1L, 0.9f)
        val hopped = estimator.estimate(
            route = route,
            deltaDistanceMm = 0f,
            timestampNs = 2L,
            confidence = 0.9f,
            forceNextSegment = true,
        )
        assertNotNull(hopped)
        assertEquals("s-1", hopped!!.segmentId)
        assertEquals(0f, hopped.distanceFromSegmentStart, 0.01f)
    }

    private fun threeSegmentRoute(segLenMm: Float): Route {
        val nodes = listOf(
            RouteNode("n-0", RouteNodeType.START, "n0"),
            RouteNode("n-1", RouteNodeType.WAYPOINT, "n1"),
            RouteNode("n-2", RouteNodeType.WAYPOINT, "n2"),
            RouteNode("n-3", RouteNodeType.END, "n3"),
        )
        val segments = listOf(
            RouteSegment("s-0", "n-0", "n-1", segLenMm),
            RouteSegment("s-1", "n-1", "n-2", segLenMm),
            RouteSegment("s-2", "n-2", "n-3", segLenMm),
        )
        val edges = listOf(
            RouteEdge("e-0", "n-0", "n-1", "s-0", preferred = true),
            RouteEdge("e-1", "n-1", "n-2", "s-1", preferred = true),
            RouteEdge("e-2", "n-2", "n-3", "s-2", preferred = true),
        )
        return Route(
            id = "r-test",
            name = "test",
            nodes = nodes,
            segments = segments,
            edges = edges,
            startNodeId = "n-0",
            startSegmentId = "s-0",
        )
    }
}
