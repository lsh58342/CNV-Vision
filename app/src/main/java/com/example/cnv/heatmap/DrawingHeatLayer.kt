package com.example.cnv.heatmap

/**
 * Drawing-plane heat sample produced by [HeatMapGenerator] (STEP 14).
 * Viewer must not compute these — only display [DrawingHeatLayer].
 */
data class DrawingHeatPoint(
    val drawingX: Double,
    val drawingY: Double,
    val shockStrength: Float,
    val intensity: HeatIntensity,
    val timestampNs: Long,
    val routePositionMm: Float,
    val routePositionLabel: String,
    val sessionId: String,
)

/**
 * Drawing-scoped heat overlay layer (one per Drawing).
 */
data class DrawingHeatLayer(
    val drawingId: String,
    val points: List<DrawingHeatPoint>,
    val sourceSessionIds: List<String>,
    val generatedAtMs: Long = System.currentTimeMillis(),
) {
    val pointCount: Int get() = points.size

    companion object {
        fun empty(drawingId: String) = DrawingHeatLayer(
            drawingId = drawingId,
            points = emptyList(),
            sourceSessionIds = emptyList(),
        )
    }
}
