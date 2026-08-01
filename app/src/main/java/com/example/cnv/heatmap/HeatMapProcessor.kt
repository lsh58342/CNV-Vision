package com.example.cnv.heatmap

import kotlin.math.floor

/**
 * Aggregates [HeatPoint]s into [HeatCell]s and statistics.
 * Does not generate points (that is [HeatMapProvider] responsibility) and does not render.
 */
class HeatMapProcessor(
    private val cellSizeWorld: Double = DEFAULT_CELL_SIZE,
) {

    data class ProcessResult(
        val points: List<HeatPoint>,
        val cells: List<HeatCell>,
        val statistics: HeatStatistics,
    )

    fun process(
        points: List<HeatPoint>,
        coveredDistanceMmHint: Float = 0f,
    ): ProcessResult {
        val cells = aggregate(points)
        val stats = computeStats(points, cells, coveredDistanceMmHint)
        return ProcessResult(points, cells, stats)
    }

    fun aggregate(points: List<HeatPoint>): List<HeatCell> {
        if (points.isEmpty()) return emptyList()
        data class Acc(
            var count: Int = 0,
            var shockSum: Float = 0f,
            var maxShock: Float = 0f,
        )
        fun key(gx: Int, gy: Int): Long =
            (gx.toLong() shl 32) xor (gy.toLong() and 0xffffffffL)

        val buckets = HashMap<Long, Acc>()
        val keyToCoord = HashMap<Long, Pair<Int, Int>>()
        for (p in points) {
            val gx = floor(p.position.x / cellSizeWorld).toInt()
            val gy = floor(p.position.y / cellSizeWorld).toInt()
            val k = key(gx, gy)
            keyToCoord[k] = gx to gy
            val acc = buckets.getOrPut(k) { Acc() }
            acc.count++
            acc.shockSum += p.shockLevel
            if (p.shockLevel > acc.maxShock) acc.maxShock = p.shockLevel
        }

        val cells = ArrayList<HeatCell>(buckets.size)
        for ((k, acc) in buckets) {
            val (gx, gy) = keyToCoord[k] ?: continue
            val minX = gx * cellSizeWorld
            val minY = gy * cellSizeWorld
            cells.add(
                HeatCell(
                    gridX = gx,
                    gridY = gy,
                    worldMinX = minX,
                    worldMinY = minY,
                    worldMaxX = minX + cellSizeWorld,
                    worldMaxY = minY + cellSizeWorld,
                    pointCount = acc.count,
                    shockSum = acc.shockSum,
                    maxShock = acc.maxShock,
                ),
            )
        }
        return cells
    }

    fun computeStats(
        points: List<HeatPoint>,
        cells: List<HeatCell>,
        coveredDistanceMmHint: Float,
    ): HeatStatistics {
        if (points.isEmpty()) {
            return HeatStatistics(coveredDistanceMm = coveredDistanceMmHint)
        }
        var maxShock = 0f
        var sum = 0f
        for (p in points) {
            sum += p.shockLevel
            if (p.shockLevel > maxShock) maxShock = p.shockLevel
        }
        return HeatStatistics(
            maximumShock = maxShock,
            averageShock = sum / points.size,
            heatPointCount = points.size,
            heatCellCount = cells.size,
            coveredDistanceMm = coveredDistanceMmHint,
        )
    }

    companion object {
        const val DEFAULT_CELL_SIZE = 40.0
    }
}
