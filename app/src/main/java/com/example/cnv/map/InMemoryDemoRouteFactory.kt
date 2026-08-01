package com.example.cnv.map

/**
 * In-memory linear demo route for debug / wiring until STEP 10-3 loaders exist.
 * Not a DWG/JSON importer.
 */
object InMemoryDemoRouteFactory {

    const val ROUTE_ID = "demo-linear"
    const val SEGMENT_A_B = "seg-a-b"
    const val SEGMENT_B_C = "seg-b-c"
    const val NODE_A = "node-a"
    const val NODE_B = "node-b"
    const val NODE_C = "node-c"

    fun create(
        segmentLengthMm: Float = DEFAULT_SEGMENT_LENGTH_MM,
    ): Route {
        val nodes = listOf(
            RouteNode(NODE_A, RouteNodeType.START, "Start"),
            RouteNode(NODE_B, RouteNodeType.WAYPOINT, "Mid"),
            RouteNode(NODE_C, RouteNodeType.END, "End"),
        )
        val segments = listOf(
            RouteSegment(SEGMENT_A_B, NODE_A, NODE_B, segmentLengthMm),
            RouteSegment(SEGMENT_B_C, NODE_B, NODE_C, segmentLengthMm),
        )
        val edges = listOf(
            RouteEdge("edge-a-b", NODE_A, NODE_B, SEGMENT_A_B, preferred = true),
            RouteEdge("edge-b-c", NODE_B, NODE_C, SEGMENT_B_C, preferred = true),
        )
        return Route(
            id = ROUTE_ID,
            name = "Demo Linear Conveyor",
            nodes = nodes,
            segments = segments,
            edges = edges,
            startNodeId = NODE_A,
            startSegmentId = SEGMENT_A_B,
        )
    }

    const val DEFAULT_SEGMENT_LENGTH_MM = 5_000f
}
