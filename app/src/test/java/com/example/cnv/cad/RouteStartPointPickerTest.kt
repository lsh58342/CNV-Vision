package com.example.cnv.cad

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RouteSegment
import com.example.cnv.route.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStartPointPickerTest {

    @Test
    fun pick_projectsOntoStartSegment() {
        val route = sampleRoute()
        val layout = CADRenderer.Layout(
            nodeWorld = mapOf(
                "N0" to WorldCoordinate(0.0, 0.0),
                "N1" to WorldCoordinate(100.0, 0.0),
            ),
            segmentWorld = mapOf(
                "S0" to (WorldCoordinate(0.0, 0.0) to WorldCoordinate(100.0, 0.0)),
            ),
            branchNodeIds = emptySet(),
            startNodeId = "N0",
            endNodeId = "N1",
        )
        val camera = CADCamera().apply { setTransform(1f, 0f, 0f) }
        // Tap near midpoint of start segment in view space.
        val pick = RouteStartPointPicker.pick(
            viewX = 50f,
            viewY = 2f,
            route = route,
            layout = layout,
            camera = camera,
        )
        assertNotNull(pick)
        assertEquals(0.5f, pick!!.progressOnStartSegment, 0.05f)
        assertEquals(50.0, pick.world.x, 1.0)
        assertEquals(0.0, pick.world.y, 0.1)
    }

    @Test
    fun worldAtStartProgress_lerpsEndpoints() {
        val route = sampleRoute()
        val layout = CADRenderer.Layout(
            nodeWorld = mapOf(
                "N0" to WorldCoordinate(0.0, 0.0),
                "N1" to WorldCoordinate(100.0, 0.0),
            ),
            segmentWorld = mapOf(
                "S0" to (WorldCoordinate(0.0, 0.0) to WorldCoordinate(100.0, 0.0)),
            ),
            branchNodeIds = emptySet(),
            startNodeId = "N0",
            endNodeId = "N1",
        )
        val world = RouteStartPointPicker.worldAtStartProgress(route, layout, 0.25f)
        assertNotNull(world)
        assertEquals(25.0, world!!.x, 0.01)
        assertTrue(world.y == 0.0)
    }

    private fun sampleRoute(): Route {
        val n0 = RouteNode(id = "N0", type = RouteNodeType.START)
        val n1 = RouteNode(id = "N1", type = RouteNodeType.END)
        val seg = RouteSegment(
            id = "S0",
            fromNodeId = "N0",
            toNodeId = "N1",
            lengthMm = 100f,
        )
        return Route(
            id = "R1",
            name = "test",
            nodes = listOf(n0, n1),
            segments = listOf(seg),
            edges = listOf(
                RouteEdge(
                    id = "E0",
                    fromNodeId = "N0",
                    toNodeId = "N1",
                    segmentId = "S0",
                ),
            ),
            startNodeId = "N0",
            startSegmentId = "S0",
        )
    }
}
