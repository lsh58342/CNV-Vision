package com.example.cnv.debug

import com.example.cnv.fusion.FusionEngine
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.imu.IMUManager
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionState
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.Route
import com.example.cnv.map.RouteRepository
import com.example.cnv.opencv.OpticalFlowDebugHub
import com.example.cnv.route.CoordinateMapper

/**
 * Builds [TrackingDebugSnapshot] from live engines (STEP 20-22).
 * Read-only — does not mutate MapMatching / Fusion / OpenCV algorithms.
 */
class TrackingDebugSampler(
    private val routeRepository: RouteRepository,
    private val mapMatchingEngine: MapMatchingEngine,
    private val fusionEngine: FusionEngine,
    private val imuManager: IMUManager,
    private val inspectionManager: InspectionManager,
    private val attitudeProbe: TrackingAttitudeProbe,
    private val mapperProvider: () -> CoordinateMapper?,
    private val layoutProvider: () -> HeatMapRouteLayout.LayoutResult?,
) {

    fun sample(trackingLabel: String): TrackingDebugSnapshot {
        val route = routeRepository.current()
        val position = mapMatchingEngine.latestPosition()
        val fusion = fusionEngine.repository.latest()
        val ordered = route?.let { orderSegments(it) }.orEmpty()
        val segIndex = if (position != null && ordered.isNotEmpty()) {
            ordered.indexOf(position.segmentId)
        } else {
            -1
        }
        val segment = position?.segmentId?.let { route?.segment(it) }
        val layout = layoutProvider()
        val routeMm = if (layout != null && position != null) {
            HeatMapRouteLayout.absoluteRouteMm(layout, position.segmentId, position.progress) ?: 0f
        } else {
            position?.distanceFromSegmentStart ?: 0f
        }
        val world = if (layout != null && position != null) {
            HeatMapRouteLayout.toDrawingCoordinate(layout, position.segmentId, position.progress)
        } else {
            null
        }
        val totalLen = layout?.totalLengthMm
            ?: route?.segments?.sumOf { it.lengthMm.toDouble() }?.toFloat()
            ?: 0f
        val totalProg = if (totalLen > 0f) (routeMm / totalLen).coerceIn(0f, 1f) else 0f
        val routeHeading = routeHeadingDeg(route, position?.segmentId, mapperProvider())
        val gyroH = attitudeProbe.gyroIntegratedHeadingDeg
        val ofH = OpticalFlowDebugHub.headingDeg
        val fusionH = TrackingAttitudeProbe.blendHeading(gyroH, ofH, 0.65f)
        val state = when {
            inspectionManager.state() != InspectionState.RUNNING -> "STOPPED"
            trackingLabel == "LOST" -> "LOST"
            trackingLabel == "SEARCHING" -> "SEARCHING"
            trackingLabel == "GOOD" || position != null -> "GOOD"
            else -> "SEARCHING"
        }
        val candidate = fusion?.eventType?.name
            ?: if (position != null) "POSITION" else "NONE"
        return TrackingDebugSnapshot(
            routeSegmentIndex = segIndex,
            routeSegmentId = position?.segmentId.orEmpty(),
            routeSegmentLengthMm = segment?.lengthMm ?: 0f,
            routeSegmentProgress = position?.progress ?: 0f,
            routeTotalProgress = totalProg,
            routeSegmentCount = ordered.size,
            worldX = world?.x ?: 0.0,
            worldY = world?.y ?: 0.0,
            routePositionMm = routeMm,
            gyroHeadingDeg = gyroH,
            opticalFlowHeadingDeg = ofH,
            fusionHeadingDeg = fusionH,
            routeHeadingDeg = routeHeading,
            trackingState = state,
            trackedFeatureCount = OpticalFlowDebugHub.trackedFeatureCount
                .takeIf { it > 0 }
                ?: (fusion?.trackingCount ?: 0),
            lostFeatureCount = OpticalFlowDebugHub.lostFeatureCount,
            reinitializeCount = OpticalFlowDebugHub.reinitializeCount,
            nearestSegmentId = position?.segmentId.orEmpty(),
            distanceToSegmentMm = 0f,
            currentCandidate = candidate,
            mapMatchConfidence = position?.confidence ?: fusion?.confidence ?: 0f,
            gyroListenerRegistered = attitudeProbe.gyroRegistered || imuManager.isRunning(),
            yawDeg = attitudeProbe.yawDeg,
            pitchDeg = attitudeProbe.pitchDeg,
            rollDeg = attitudeProbe.rollDeg,
        )
    }

    private fun orderSegments(route: Route): List<String> {
        val ordered = ArrayList<String>()
        val visited = HashSet<String>()
        var segmentId: String? = route.startSegmentId
        var guard = 0
        while (segmentId != null && segmentId !in visited && guard < route.segments.size + 2) {
            guard++
            visited.add(segmentId)
            ordered.add(segmentId)
            val seg = route.segment(segmentId) ?: break
            segmentId = route.preferredOutgoingEdge(seg.toNodeId)?.segmentId
        }
        if (ordered.isEmpty()) {
            return route.segments.map { it.id }
        }
        return ordered
    }

    private fun routeHeadingDeg(
        route: Route?,
        segmentId: String?,
        mapper: CoordinateMapper?,
    ): Float {
        if (route == null || segmentId.isNullOrBlank() || mapper == null) return 0f
        val seg = route.segment(segmentId) ?: return 0f
        val start = mapper.toWorld(
            com.example.cnv.map.RoutePosition(
                segmentId = segmentId,
                nodeId = seg.fromNodeId,
                distanceFromSegmentStart = 0f,
                progress = 0f,
                direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                timestampNs = 0L,
                confidence = 1f,
            ),
        ) ?: return 0f
        val end = mapper.toWorld(
            com.example.cnv.map.RoutePosition(
                segmentId = segmentId,
                nodeId = seg.toNodeId,
                distanceFromSegmentStart = seg.lengthMm,
                progress = 1f,
                direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                timestampNs = 0L,
                confidence = 1f,
            ),
        ) ?: return 0f
        return TrackingAttitudeProbe.headingFromDelta(end.x - start.x, end.y - start.y)
    }
}
