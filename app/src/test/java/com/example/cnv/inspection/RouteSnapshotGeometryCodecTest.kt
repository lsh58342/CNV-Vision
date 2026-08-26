package com.example.cnv.inspection

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RouteSegment
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route snapshot must carry world geometry so CAD never restores as a straight line.
 * (Codec JSONObject path is Android-instrumented; this covers in-memory geometry.)
 */
class RouteSnapshotGeometryCodecTest {

    @Test
    fun snapshotFromMapperKeepsUShapeMidpoint() {
        val route = Route(
            id = "r1",
            name = "u",
            nodes = listOf(
                RouteNode("n0", RouteNodeType.START, "n0"),
                RouteNode("n1", RouteNodeType.WAYPOINT, "n1"),
                RouteNode("n2", RouteNodeType.WAYPOINT, "n2"),
                RouteNode("n3", RouteNodeType.END, "n3"),
            ),
            segments = listOf(
                RouteSegment("s0", "n0", "n1", 100f),
                RouteSegment("s1", "n1", "n2", 100f),
                RouteSegment("s2", "n2", "n3", 100f),
            ),
            edges = listOf(
                RouteEdge("e0", "n0", "n1", "s0", true),
                RouteEdge("e1", "n1", "n2", "s1", true),
                RouteEdge("e2", "n2", "n3", "s2", true),
            ),
            startNodeId = "n0",
            startSegmentId = "s0",
        )
        val mapper = CoordinateMapper(
            segmentGeometry = mapOf(
                "s0" to CoordinateMapper.SegmentGeometry(
                    "s0", WorldCoordinate(0.0, 100.0), WorldCoordinate(100.0, 100.0),
                ),
                "s1" to CoordinateMapper.SegmentGeometry(
                    "s1", WorldCoordinate(100.0, 100.0), WorldCoordinate(100.0, 0.0),
                ),
                "s2" to CoordinateMapper.SegmentGeometry(
                    "s2", WorldCoordinate(100.0, 0.0), WorldCoordinate(0.0, 0.0),
                ),
            ),
        )
        val snap = RouteSnapshot.from(route, mapper = mapper)
        assertTrue(snap.segmentGeometry.isNotEmpty())
        val restored = snap.toMapper()
        assertNotNull(restored)
        val mid = restored!!.toWorld(
            com.example.cnv.map.RoutePosition(
                segmentId = "s1",
                nodeId = "n1",
                distanceFromSegmentStart = 50f,
                progress = 0.5f,
                direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                timestampNs = 0L,
                confidence = 1f,
            ),
        )
        assertNotNull(mid)
        assertEquals(100.0, mid!!.x, 0.1)
        assertEquals(50.0, mid.y, 0.1)
    }
}
