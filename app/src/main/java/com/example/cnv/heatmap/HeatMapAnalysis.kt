package com.example.cnv.heatmap

/**
 * Read-only HeatMap analysis for a filtered session view.
 * Display only — never mutates HeatMap / Route / Shock.
 */
data class HeatMapAnalysis(
    val hotSpotCount: Int = 0,
    val topShockAreas: List<ShockArea> = emptyList(),
    val highestShockSegmentId: String? = null,
    val highestShockNodeId: String? = null,
    val coveragePercentage: Float = 0f,
    val inspectionCompleteness: Float = 0f,
) {
    data class ShockArea(
        val gridX: Int,
        val gridY: Int,
        val maxShock: Float,
        val pointCount: Int,
    )

    fun summary(): String = buildString {
        append("hot=%d ".format(hotSpotCount))
        append("top=%d ".format(topShockAreas.size))
        append("seg=${highestShockSegmentId ?: "—"} ")
        append("node=${highestShockNodeId ?: "—"} ")
        append("cov=%.0f%% ".format(coveragePercentage))
        append("complete=%.0f%%".format(inspectionCompleteness * 100f))
    }

    companion object {
        val EMPTY: HeatMapAnalysis = HeatMapAnalysis()
    }
}
