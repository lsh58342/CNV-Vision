package com.example.cnv.heatmap

/**
 * STEP 14-1 — Coordinate validation debug models (no HeatMap color/blur).
 */
enum class CoordinateDebugPointKind {
    EVENT,
    SHOCK,
    CURRENT,
    ORIGIN,
}

data class CoordinateDebugPoint(
    val drawingX: Double,
    val drawingY: Double,
    val routePositionMm: Float,
    val routePositionLabel: String,
    val timestampNs: Long,
    val kind: CoordinateDebugPointKind,
    val sessionId: String,
    val onRoute: Boolean,
)

data class CoordinateValidationStats(
    val currentRoutePositionMm: Float = 0f,
    val currentDrawingX: Double = 0.0,
    val currentDrawingY: Double = 0.0,
    val inspectionDistanceMm: Float = 0f,
    val eventCount: Int = 0,
    val shockCount: Int = 0,
    val offRouteCount: Int = 0,
    val directionLabel: String = "—",
) {
    fun summaryLines(): List<String> = listOf(
        "Route Pos: %.1f mm".format(currentRoutePositionMm),
        "Drawing: %.1f, %.1f".format(currentDrawingX, currentDrawingY),
        "Distance: %.1f mm".format(inspectionDistanceMm),
        "Events: %d".format(eventCount),
        "Shocks: %d".format(shockCount),
        "Off-route: %d".format(offRouteCount),
        "Direction: %s".format(directionLabel),
    )
}

data class CoordinateValidationSnapshot(
    val drawingId: String,
    val points: List<CoordinateDebugPoint>,
    val polyline: List<Pair<Double, Double>>,
    val stats: CoordinateValidationStats,
    val sessionId: String?,
) {
    companion object {
        fun empty(drawingId: String) = CoordinateValidationSnapshot(
            drawingId = drawingId,
            points = emptyList(),
            polyline = emptyList(),
            stats = CoordinateValidationStats(),
            sessionId = null,
        )
    }
}
