package com.example.cnv.route

import com.example.cnv.dwg.DWGConfig
import com.example.cnv.dwg.GeometryModel
import com.example.cnv.dwg.PolylineModel

/**
 * Conveyor route candidate extraction (Rule Base only).
 * Stops at [RouteCandidate] — does not build map.Route or write RouteRepository.
 */
class RouteExtractor(
    private val config: DWGConfig = DWGConfig.DEFAULT,
    private val centerLineExtractor: CenterLineExtractor = CenterLineExtractor(config),
) {

    data class ExtractionResult(
        val selectedLayer: String,
        val sourcePolylines: List<PolylineModel>,
        val mergedPolylines: List<PolylineModel>,
        val centerLines: List<CenterLineExtractor.CenterLine>,
        val candidates: List<RouteCandidate>,
    )

    fun extract(
        geometry: GeometryModel,
        layerName: String = config.layerFilter,
    ): ExtractionResult {
        val layerPolylines = collectLayerPolylines(geometry, layerName)
            .filter { it.length() >= config.minimumPolylineLength }
        val merged = mergePolylines(layerPolylines)
        val centerLines = centerLineExtractor.extract(merged)
        val candidates = centerLines.mapIndexed { index, centerLine ->
            RouteCandidate(
                id = "rc-$index",
                layerName = layerName,
                centerLine = centerLine.points,
                length = centerLine.length(),
                sourcePolylineCount = centerLine.sourceCount,
                confidence = confidenceFor(centerLine.sourceCount, centerLine.length()),
            )
        }
        return ExtractionResult(
            selectedLayer = layerName,
            sourcePolylines = layerPolylines,
            mergedPolylines = merged,
            centerLines = centerLines,
            candidates = candidates,
        )
    }

    private fun collectLayerPolylines(
        geometry: GeometryModel,
        layerName: String,
    ): List<PolylineModel> {
        val fromPolylines = geometry.polylinesOnLayer(layerName)
        val fromLines = geometry.linesOnLayer(layerName).map { it.toPolyline() }
        return fromPolylines + fromLines
    }

    /**
     * Greedy endpoint merge within [DWGConfig.mergeTolerance].
     */
    fun mergePolylines(polylines: List<PolylineModel>): List<PolylineModel> {
        if (polylines.isEmpty()) return emptyList()
        val open = polylines.map { it.points.toMutableList() }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in open.indices) {
                for (j in open.indices) {
                    if (i == j) continue
                    val a = open[i]
                    val b = open[j]
                    when {
                        a.last().distanceTo(b.first()) <= config.mergeTolerance -> {
                            a.addAll(b.drop(1))
                            open.removeAt(j)
                            changed = true
                            break@outer
                        }
                        a.last().distanceTo(b.last()) <= config.mergeTolerance -> {
                            a.addAll(b.asReversed().drop(1))
                            open.removeAt(j)
                            changed = true
                            break@outer
                        }
                        a.first().distanceTo(b.last()) <= config.mergeTolerance -> {
                            val merged = b.toMutableList()
                            merged.addAll(a.drop(1))
                            open[i] = merged
                            open.removeAt(j)
                            changed = true
                            break@outer
                        }
                        a.first().distanceTo(b.first()) <= config.mergeTolerance -> {
                            val merged = b.asReversed().toMutableList()
                            merged.addAll(a.drop(1))
                            open[i] = merged
                            open.removeAt(j)
                            changed = true
                            break@outer
                        }
                    }
                }
            }
        }
        return open.mapIndexed { index, points ->
            PolylineModel(
                id = "merged-$index",
                layerName = polylines.firstOrNull()?.layerName.orEmpty(),
                points = points.toList(),
            )
        }.filter { it.length() >= config.minimumPolylineLength }
    }

    private fun confidenceFor(sourceCount: Int, length: Double): Float {
        val pairBonus = if (sourceCount >= 2) 0.25f else 0f
        val lengthScore = (length / (config.minimumPolylineLength * 4.0)).toFloat().coerceIn(0f, 0.75f)
        return (0.5f + pairBonus + lengthScore * 0.25f).coerceIn(0f, 1f)
    }
}
