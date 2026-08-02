package com.example.cnv.map

import com.example.cnv.core.model.RouteDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STEP 20-22: small optical-flow deltas must hop past segment 0.
 */
class PositionEstimatorTransitionTest {

    @Test
    fun smallDeltasAdvancePastFirstSegment() {
        val route = threeSegmentRoute(segLenMm = 100f)
        val estimator = PositionEstimator(MapConfig.DEFAULT)
        estimator.reset(route)

        // 60 × 2mm = 120mm — must enter segment 1 (old bug: clamped at seg0 forever).
        var last: RoutePosition? = null
        repeat(60) {
            last = estimator.estimate(
                route = route,
                deltaDistanceMm = 2f,
                timestampNs = it * 10_000_000L,
                confidence = 0.9f,
            )
            assertNotNull(last)
        }
        assertEquals("s-1", last!!.segmentId)
        assertTrue(last!!.progress < 1f)
    }

    @Test
    fun endOfRouteClampsWithoutRejecting() {
        val route = threeSegmentRoute(segLenMm = 50f)
        val estimator = PositionEstimator()
        estimator.reset(route)
        var last: RoutePosition? = null
        repeat(200) {
            last = estimator.estimate(route, 5f, it * 1_000_000L, 0.9f)
        }
        assertEquals("s-2", last!!.segmentId)
        assertEquals(1f, last!!.progress, 0.001f)
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
