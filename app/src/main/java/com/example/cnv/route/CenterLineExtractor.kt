package com.example.cnv.route

import com.example.cnv.dwg.DWGConfig
import com.example.cnv.dwg.Point2d
import com.example.cnv.dwg.PolylineModel
import kotlin.math.abs
import kotlin.math.min

/**
 * Rule-based center-line extraction from merged conveyor polylines.
 * Parallel pair → midpoint polyline; single stroke → itself.
 */
class CenterLineExtractor(
    private val config: DWGConfig = DWGConfig.DEFAULT,
) {

    data class CenterLine(
        val id: String,
        val points: List<Point2d>,
        val sourceCount: Int,
    ) {
        fun length(): Double {
            if (points.size < 2) return 0.0
            var sum = 0.0
            for (i in 0 until points.lastIndex) {
                sum += points[i].distanceTo(points[i + 1])
            }
            return sum
        }
    }

    fun extract(mergedPolylines: List<PolylineModel>): List<CenterLine> {
        if (mergedPolylines.isEmpty()) return emptyList()
        val remaining = mergedPolylines.toMutableList()
        val result = mutableListOf<CenterLine>()
        var index = 0

        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val partnerIndex = remaining.indexOfFirst { isParallelPartner(first, it) }
            if (partnerIndex >= 0) {
                val partner = remaining.removeAt(partnerIndex)
                result.add(
                    CenterLine(
                        id = "cl-${index++}",
                        points = averagePolylines(first, partner),
                        sourceCount = 2,
                    ),
                )
            } else {
                result.add(
                    CenterLine(
                        id = "cl-${index++}",
                        points = first.points,
                        sourceCount = 1,
                    ),
                )
            }
        }
        return result.filter { it.length() >= config.minimumPolylineLength }
    }

    private fun isParallelPartner(a: PolylineModel, b: PolylineModel): Boolean {
        if (a.points.size < 2 || b.points.size < 2) return false
        val startGap = a.points.first().distanceTo(b.points.first())
        val endGap = a.points.last().distanceTo(b.points.last())
        val startGapRev = a.points.first().distanceTo(b.points.last())
        val endGapRev = a.points.last().distanceTo(b.points.first())
        val aligned = abs(startGap - endGap) <= config.centerLineTolerance &&
            startGap <= config.centerLineTolerance * 2 &&
            endGap <= config.centerLineTolerance * 2
        val reversed = abs(startGapRev - endGapRev) <= config.centerLineTolerance &&
            startGapRev <= config.centerLineTolerance * 2 &&
            endGapRev <= config.centerLineTolerance * 2
        return aligned || reversed
    }

    private fun averagePolylines(a: PolylineModel, b: PolylineModel): List<Point2d> {
        val bPoints = orientToMatch(a, b)
        val count = min(a.points.size, bPoints.size)
        val averaged = ArrayList<Point2d>(count)
        for (i in 0 until count) {
            val p = a.points[i]
            val q = bPoints[i]
            averaged.add(Point2d((p.x + q.x) / 2.0, (p.y + q.y) / 2.0))
        }
        return averaged
    }

    private fun orientToMatch(reference: PolylineModel, other: PolylineModel): List<Point2d> {
        val forwardStart = reference.points.first().distanceTo(other.points.first())
        val reverseStart = reference.points.first().distanceTo(other.points.last())
        return if (reverseStart < forwardStart) other.points.asReversed() else other.points
    }
}
