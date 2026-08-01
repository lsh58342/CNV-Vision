package com.example.cnv.heatmap

/**
 * Applies HeatMap filter facets in fixed Session-first order.
 * Does not mutate [HeatMapFilter] / FilterController.
 *
 * Order: Session → Timeline → Segment → Node → Shock → Confidence
 */
object HeatMapFilterPipeline {

    fun applyOrdered(
        source: List<HeatPoint>,
        filter: HeatMapFilter,
    ): HeatMapFilterResult {
        var points = source
        // 1. Session
        if (filter.sessionId != null) {
            val id = filter.sessionId
            points = points.filter { it.sessionId == id }
        }
        // 2. Timeline (time range)
        points = points.filter {
            it.timestampNs >= filter.timeMinNs && it.timestampNs <= filter.timeMaxNs
        }
        // 3. Route Segment
        if (filter.segmentId != null) {
            val seg = filter.segmentId
            points = points.filter { it.segmentId == seg }
        }
        // 4. Route Node
        if (filter.nodeId != null) {
            val node = filter.nodeId
            points = points.filter { it.nodeId == node }
        }
        // 5. Shock Level
        points = points.filter {
            it.shockLevel >= filter.minShock && it.shockLevel <= filter.maxShock
        }
        // 6. Confidence
        points = points.filter {
            it.confidence >= filter.minConfidence && it.confidence <= filter.maxConfidence
        }
        return HeatMapFilterResult(
            points = points,
            filter = filter,
            sourcePointCount = source.size,
        )
    }

    fun cacheKey(filter: HeatMapFilter): String = buildString {
        append(filter.sessionId ?: "*")
        append('|')
        append(filter.timeMinNs)
        append('-')
        append(filter.timeMaxNs)
        append('|')
        append(filter.segmentId ?: "*")
        append('|')
        append(filter.nodeId ?: "*")
        append('|')
        append(filter.minShock)
        append('-')
        append(if (filter.maxShock.isFinite()) filter.maxShock else "inf")
        append('|')
        append(filter.minConfidence)
        append('-')
        append(if (filter.maxConfidence.isFinite()) filter.maxConfidence else "inf")
    }
}
