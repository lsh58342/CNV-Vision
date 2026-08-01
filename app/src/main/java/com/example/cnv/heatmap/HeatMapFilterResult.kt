package com.example.cnv.heatmap

/**
 * Filtered HeatPoint list for aggregation/render. No extra scoring.
 */
data class HeatMapFilterResult(
    val points: List<HeatPoint>,
    val filter: HeatMapFilter,
    val sourcePointCount: Int,
) {
    val visiblePointCount: Int get() = points.size

    companion object {
        val EMPTY: HeatMapFilterResult = HeatMapFilterResult(
            points = emptyList(),
            filter = HeatMapFilter.ALL,
            sourcePointCount = 0,
        )
    }
}
