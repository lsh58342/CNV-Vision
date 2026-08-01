package com.example.cnv.heatmap

/**
 * Session-scoped HeatMap statistics (display only — does not recompute shock/fusion).
 */
data class HeatSessionStatistics(
    val sessionId: String = "",
    val maximumShock: Float = 0f,
    val averageShock: Float = 0f,
    val maximumConfidence: Float = 0f,
    val averageConfidence: Float = 0f,
    val coveredDistanceMm: Float = 0f,
    val coverageRate: Float = 0f,
    val heatPointCount: Int = 0,
    val heatCellCount: Int = 0,
    val inspectionDurationMs: Long = 0L,
) {
    fun summary(): String = buildString {
        append("pts=%d cells=%d ".format(heatPointCount, heatCellCount))
        append("shockMax=%.2f avg=%.2f ".format(maximumShock, averageShock))
        append("confMax=%.2f avg=%.2f ".format(maximumConfidence, averageConfidence))
        append("cov=%.0fmm (%.0f%%) ".format(coveredDistanceMm, coverageRate * 100f))
        append("dur=%dms".format(inspectionDurationMs))
    }

    companion object {
        val EMPTY: HeatSessionStatistics = HeatSessionStatistics()
    }
}
