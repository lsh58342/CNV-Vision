package com.example.cnv.ui.screen.inspection

/**
 * Read-only overlay snapshot for [LiveRouteViewer] (STEP 20-16).
 * Coordinates match Live Dashboard (HeatMapRouteLayout drawing plane).
 */
enum class LiveTrackingVisual {
    GOOD,
    SEARCHING,
    LOST,
    STOPPED,
}

data class LiveRouteSegmentDraw(
    val id: String,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
)

data class LiveRouteOverlayState(
    val segments: List<LiveRouteSegmentDraw> = emptyList(),
    val traversedSegmentIds: Set<String> = emptySet(),
    val currentSegmentId: String? = null,
    val currentProgress: Float = 0f,
    /** Absolute route progress 0f..1f (coverage), for Progress % label. */
    val routeProgressPercent: Float = 0f,
    val markerX: Double? = null,
    val markerY: Double? = null,
    val directionRad: Float = 0f,
    val originX: Double? = null,
    val originY: Double? = null,
    val zoneSegmentIds: Set<String> = emptySet(),
    val tracking: LiveTrackingVisual = LiveTrackingVisual.STOPPED,
)
