package com.example.cnv.debug

/**
 * Snapshot for Inspection Tracking Debug Mode (STEP 20-22).
 */
data class TrackingDebugSnapshot(
    val routeSegmentIndex: Int = -1,
    val routeSegmentId: String = "",
    val routeSegmentLengthMm: Float = 0f,
    val routeSegmentProgress: Float = 0f,
    val routeTotalProgress: Float = 0f,
    val routeSegmentCount: Int = 0,
    val worldX: Double = 0.0,
    val worldY: Double = 0.0,
    val routePositionMm: Float = 0f,
    val gyroHeadingDeg: Float = 0f,
    val opticalFlowHeadingDeg: Float = 0f,
    val fusionHeadingDeg: Float = 0f,
    val routeHeadingDeg: Float = 0f,
    val trackingState: String = "STOPPED",
    val trackedFeatureCount: Int = 0,
    val lostFeatureCount: Int = 0,
    val reinitializeCount: Int = 0,
    val nearestSegmentId: String = "",
    val distanceToSegmentMm: Float = 0f,
    val currentCandidate: String = "",
    val mapMatchConfidence: Float = 0f,
    val gyroListenerRegistered: Boolean = false,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
) {
    fun formatHud(): String = buildString {
        appendLine("═══ TRACKING DEBUG ═══")
        appendLine("Route")
        appendLine("  SegIdx=$routeSegmentIndex / $routeSegmentCount  id=$routeSegmentId")
        appendLine("  SegLen=${fmt(routeSegmentLengthMm)}mm  SegProg=${pct(routeSegmentProgress)}")
        appendLine("  TotalProg=${pct(routeTotalProgress)}")
        appendLine("Position")
        appendLine("  World X=${"%.1f".format(worldX)}  Y=${"%.1f".format(worldY)}")
        appendLine("  RoutePos=${fmt(routePositionMm)}mm")
        appendLine("Heading")
        appendLine("  Gyro=${fmt(gyroHeadingDeg)}°  OF=${fmt(opticalFlowHeadingDeg)}°")
        appendLine("  Fusion=${fmt(fusionHeadingDeg)}°  Route=${fmt(routeHeadingDeg)}°")
        appendLine("Tracking")
        appendLine("  State=$trackingState")
        appendLine("Optical Flow")
        appendLine("  Tracked=$trackedFeatureCount  Lost=$lostFeatureCount  Reinit=$reinitializeCount")
        appendLine("Map Matching")
        appendLine("  Nearest=$nearestSegmentId  Dist=${fmt(distanceToSegmentMm)}mm")
        appendLine("  Candidate=$currentCandidate  Conf=${"%.2f".format(mapMatchConfidence)}")
        appendLine("Gyro Attitude")
        appendLine("  Listener=${if (gyroListenerRegistered) "REGISTERED" else "OFF"}")
        appendLine("  Yaw=${fmt(yawDeg)}° Pitch=${fmt(pitchDeg)}° Roll=${fmt(rollDeg)}°")
    }

    fun formatLogLine(): String =
        "LOG[TrackingDebug] state=$trackingState seg=$routeSegmentIndex/$routeSegmentCount" +
            "($routeSegmentId) prog=${pct(routeSegmentProgress)} total=${pct(routeTotalProgress)}" +
            " routeMm=${fmt(routePositionMm)} world=(${"%.1f".format(worldX)},${"%.1f".format(worldY)})" +
            " gyroH=${fmt(gyroHeadingDeg)} ofH=${fmt(opticalFlowHeadingDeg)}" +
            " fusH=${fmt(fusionHeadingDeg)} routeH=${fmt(routeHeadingDeg)}" +
            " feat=$trackedFeatureCount lost=$lostFeatureCount reinit=$reinitializeCount" +
            " mmConf=${"%.2f".format(mapMatchConfidence)} nearest=$nearestSegmentId" +
            " gyroReg=$gyroListenerRegistered ypr=${fmt(yawDeg)}/${fmt(pitchDeg)}/${fmt(rollDeg)}"

    private fun fmt(v: Float): String = "%.1f".format(v)
    private fun pct(v: Float): String = "%.1f%%".format(v * 100f)
}
