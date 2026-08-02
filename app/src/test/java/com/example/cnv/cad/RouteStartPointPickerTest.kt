package com.example.cnv.cad

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RouteSegment
import com.example.cnv.route.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStartPointPickerTest {

    @Test
    fun pick_usesNearestPointOnTouchedSegment() {
        val route = threeSegmentRoute()
        val layout = multiSegLayout()
        val camera = CADCamera().apply { setTransform(1f, 0f, 0f) }
        // Tap near midpoint of middle segment S1 (y≈50 at x=100).
        val pick = RouteStartPointPicker.pick(
            viewX = 102f,
            viewY = 50f,
            route = route,
            layout = layout,
            camera = camera,
        )
        assertNotNull(pick)
        assertEquals("S1", pick!!.nearestSegmentId)
        assertEquals(100.0, pick.world.x, 2.0)
        assertEquals(50.0, pick.world.y, 2.0)
    }

    @Test
    fun pick_differentTaps_yieldDifferentOrigins() {
        val route = threeSegmentRoute()
        val layout = multiSegLayout()
        val camera = CADCamera().apply { setTransform(1f, 0f, 0f) }
        val a = RouteStartPointPicker.pick(10f, 1f, route, layout, camera)!!
        val b = RouteStartPointPicker.pick(90f, 1f, route, layout, camera)!!
        val c = RouteStartPointPicker.pick(101f, 90f, route, layout, camera)!!
        assertEquals("S0", a.nearestSegmentId)
        assertEquals("S0", b.nearestSegmentId)
        assertEquals("S1", c.nearestSegmentId)
        assertNotEquals(a.world.x, b.world.x, 1.0)
        assertTrue(c.world.y > 40.0)
    }

    @Test
    fun pick_offRouteSnapsToNearest() {
        val route = threeSegmentRoute()
        val layout = multiSegLayout()
        val camera = CADCamera().apply { setTransform(1f, 0f, 0f) }
        // Far above S2 (y=100). Nearest is S2, not S0.
        val pick = RouteStartPointPicker.pick(50f, 200f, route, layout, camera)
        assertNotNull(pick)
        assertEquals("S2", pick!!.nearestSegmentId)
        assertEquals(50.0, pick.world.x, 2.0)
        assertEquals(100.0, pick.world.y, 0.5)
    }

    @Test
    fun worldAtStartProgress_lerpsEndpoints() {
        val route = threeSegmentRoute()
        val layout = multiSegLayout()
        val world = RouteStartPointPicker.worldAtStartProgress(route, layout, 0.25f)
        assertNotNull(world)
        assertEquals(25.0, world!!.x, 0.01)
        assertTrue(world.y == 0.0)
    }

    private fun multiSegLayout() = CADRenderer.Layout(
        nodeWorld = mapOf(
            "N0" to WorldCoordinate(0.0, 0.0),
            "N1" to WorldCoordinate(100.0, 0.0),
            "N2" to WorldCoordinate(100.0, 100.0),
            "N3" to WorldCoordinate(0.0, 100.0),
        ),
        segmentWorld = mapOf(
            "S0" to (WorldCoordinate(0.0, 0.0) to WorldCoordinate(100.0, 0.0)),
            "S1" to (WorldCoordinate(100.0, 0.0) to WorldCoordinate(100.0, 100.0)),
            "S2" to (WorldCoordinate(100.0, 100.0) to WorldCoordinate(0.0, 100.0)),
        ),
        branchNodeIds = emptySet(),
        startNodeId = "N0",
        endNodeId = "N3",
    )

    private fun threeSegmentRoute(): Route {
        val nodes = listOf(
            RouteNode("N0", RouteNodeType.START),
            RouteNode("N1", RouteNodeType.WAYPOINT),
            RouteNode("N2", RouteNodeType.WAYPOINT),
            RouteNode("N3", RouteNodeType.END),
        )
        val segments = listOf(
            RouteSegment("S0", "N0", "N1", 100f),
            RouteSegment("S1", "N1", "N2", 100f),
            RouteSegment("S2", "N2", "N3", 100f),
        )
        val edges = listOf(
            RouteEdge("E0", "N0", "N1", "S0", preferred = true),
            RouteEdge("E1", "N1", "N2", "S1", preferred = true),
            RouteEdge("E2", "N2", "N3", "S2", preferred = true),
        )
        return Route(
            id = "R1",
            name = "test",
            nodes = nodes,
            segments = segments,
            edges = edges,
            startNodeId = "N0",
            startSegmentId = "S0",
        )
    }
}
