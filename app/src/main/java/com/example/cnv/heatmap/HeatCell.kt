package com.example.cnv.heatmap

/**
 * Grid aggregation of Shock [HeatPoint]s. Aggregation only — does not recompute shock.
 */
data class HeatCell(
    val gridX: Int,
    val gridY: Int,
    val worldMinX: Double,
    val worldMinY: Double,
    val worldMaxX: Double,
    val worldMaxY: Double,
    val pointCount: Int,
    val shockSum: Float,
    val maxShock: Float,
) {
    /** Aggregated display intensity = average of recorded shock levels. */
    val intensity: Float
        get() = if (pointCount <= 0) 0f else shockSum / pointCount
}
