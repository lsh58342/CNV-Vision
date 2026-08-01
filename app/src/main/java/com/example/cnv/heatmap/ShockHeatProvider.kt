package com.example.cnv.heatmap

import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.inspection.InspectionSession
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * Shock HeatMap provider — same behavior as STEP 12-1 processor extraction.
 * intensity = shockLevel (no extra scoring).
 */
class ShockHeatProvider(
    private val mapperProvider: () -> CoordinateMapper?,
) : HeatMapProvider {

    override val providerName: String = "ShockHeatProvider"

    override fun generateHeatPoints(session: InspectionSession): List<HeatPoint> {
        val mapper = mapperProvider() ?: return emptyList()
        val events = session.recorder().snapshot()
        if (events.isEmpty()) return emptyList()

        val out = ArrayList<HeatPoint>()
        var lastWorld: WorldCoordinate? = null
        var lastSegmentId: String? = null
        var lastNodeId: String? = null
        val sessionId = session.sessionId

        for (event in events) {
            when (event) {
                is PositionEvent -> {
                    val routePos = RoutePosition(
                        segmentId = event.segmentId,
                        nodeId = event.nodeId,
                        distanceFromSegmentStart = event.distanceFromSegmentStart,
                        progress = event.progress,
                        direction = event.direction,
                        timestampNs = event.timestampNs,
                        confidence = event.confidence,
                    )
                    lastWorld = mapper.toWorld(routePos) ?: lastWorld
                    lastSegmentId = event.segmentId
                    lastNodeId = event.nodeId
                }
                is FusionEvent -> {
                    if (event.shockLevel <= 0f) continue
                    val world = lastWorld ?: continue
                    out.add(
                        HeatPoint(
                            position = world,
                            shockLevel = event.shockLevel,
                            timestampNs = event.timestampNs,
                            confidence = event.confidence,
                            sessionId = sessionId,
                            intensity = event.shockLevel,
                            segmentId = lastSegmentId,
                            nodeId = lastNodeId,
                        ),
                    )
                }
                else -> Unit
            }
        }
        return out
    }
}
