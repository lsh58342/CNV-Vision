package com.example.cnv.heatmap

/**
 * Shock HeatMap statistics (visualization only).
 */
data class HeatStatistics(
    val maximumShock: Float = 0f,
    val averageShock: Float = 0f,
    val heatPointCount: Int = 0,
    val heatCellCount: Int = 0,
    val coveredDistanceMm: Float = 0f,
) {
    companion object {
        val EMPTY: HeatStatistics = HeatStatistics()
    }
}
