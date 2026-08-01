package com.example.cnv.heatmap

/**
 * Computes [HeatMapAnalysis] and [HeatSessionStatistics] from filtered points/cells.
 * Does not regenerate HeatPoints or recompute shock.
 */
class HeatMapAnalyzer(
    private val hotSpotShockThreshold: Float = DEFAULT_HOT_SPOT_THRESHOLD,
    private val topN: Int = DEFAULT_TOP_N,
) {

    fun analyze(
        sessionId: String,
        points: List<HeatPoint>,
        cells: List<HeatCell>,
        coveredDistanceMm: Float,
        routeTotalDistanceMm: Float,
        inspectionDurationMs: Long,
        sourcePointCount: Int,
    ): Pair<HeatSessionStatistics, HeatMapAnalysis> {
        val sessionStats = computeSessionStatistics(
            sessionId = sessionId,
            points = points,
            cells = cells,
            coveredDistanceMm = coveredDistanceMm,
            routeTotalDistanceMm = routeTotalDistanceMm,
            inspectionDurationMs = inspectionDurationMs,
        )
        val analysis = computeAnalysis(
            points = points,
            cells = cells,
            coverageRate = sessionStats.coverageRate,
            sourcePointCount = sourcePointCount,
        )
        return sessionStats to analysis
    }

    fun computeSessionStatistics(
        sessionId: String,
        points: List<HeatPoint>,
        cells: List<HeatCell>,
        coveredDistanceMm: Float,
        routeTotalDistanceMm: Float,
        inspectionDurationMs: Long,
    ): HeatSessionStatistics {
        if (points.isEmpty()) {
            return HeatSessionStatistics(
                sessionId = sessionId,
                coveredDistanceMm = coveredDistanceMm,
                coverageRate = coverageRate(coveredDistanceMm, routeTotalDistanceMm),
                heatCellCount = cells.size,
                inspectionDurationMs = inspectionDurationMs,
            )
        }
        var maxShock = 0f
        var sumShock = 0f
        var maxConf = 0f
        var sumConf = 0f
        for (p in points) {
            sumShock += p.shockLevel
            if (p.shockLevel > maxShock) maxShock = p.shockLevel
            sumConf += p.confidence
            if (p.confidence > maxConf) maxConf = p.confidence
        }
        val n = points.size.toFloat()
        return HeatSessionStatistics(
            sessionId = sessionId,
            maximumShock = maxShock,
            averageShock = sumShock / n,
            maximumConfidence = maxConf,
            averageConfidence = sumConf / n,
            coveredDistanceMm = coveredDistanceMm,
            coverageRate = coverageRate(coveredDistanceMm, routeTotalDistanceMm),
            heatPointCount = points.size,
            heatCellCount = cells.size,
            inspectionDurationMs = inspectionDurationMs,
        )
    }

    fun computeAnalysis(
        points: List<HeatPoint>,
        cells: List<HeatCell>,
        coverageRate: Float,
        sourcePointCount: Int,
    ): HeatMapAnalysis {
        if (cells.isEmpty() && points.isEmpty()) return HeatMapAnalysis.EMPTY

        val hotSpots = cells.filter { it.maxShock >= hotSpotShockThreshold }
        val top = cells
            .sortedByDescending { it.maxShock }
            .take(topN)
            .map {
                HeatMapAnalysis.ShockArea(
                    gridX = it.gridX,
                    gridY = it.gridY,
                    maxShock = it.maxShock,
                    pointCount = it.pointCount,
                )
            }

        var bestSeg: String? = null
        var bestSegShock = -1f
        var bestNode: String? = null
        var bestNodeShock = -1f
        for (p in points) {
            val seg = p.segmentId
            if (seg != null && p.shockLevel > bestSegShock) {
                bestSegShock = p.shockLevel
                bestSeg = seg
            }
            val node = p.nodeId
            if (node != null && p.shockLevel > bestNodeShock) {
                bestNodeShock = p.shockLevel
                bestNode = node
            }
        }

        val completeness = when {
            sourcePointCount <= 0 -> 0f
            else -> (points.size.toFloat() / sourcePointCount).coerceIn(0f, 1f)
        }

        return HeatMapAnalysis(
            hotSpotCount = hotSpots.size,
            topShockAreas = top,
            highestShockSegmentId = bestSeg,
            highestShockNodeId = bestNode,
            coveragePercentage = (coverageRate * 100f).coerceIn(0f, 100f),
            inspectionCompleteness = completeness,
        )
    }

    private fun coverageRate(coveredMm: Float, routeTotalMm: Float): Float {
        if (routeTotalMm <= 1e-3f) return 0f
        return (coveredMm / routeTotalMm).coerceIn(0f, 1f)
    }

    companion object {
        const val DEFAULT_HOT_SPOT_THRESHOLD = 0.5f
        const val DEFAULT_TOP_N = 5
    }
}
