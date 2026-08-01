package com.example.cnv.heatmap

/**
 * Applies [HeatMapFilter] to HeatPoints. Does not mutate source lists.
 */
class HeatMapFilterController(
    private val state: HeatMapFilterState = HeatMapFilterState(),
) {
    fun state(): HeatMapFilterState = state

    fun setSessionId(sessionId: String?) {
        state.sessionId = sessionId?.takeIf { it.isNotBlank() }
    }

    fun setShockRange(min: Float, max: Float) {
        state.minShock = min.coerceAtLeast(0f)
        state.maxShock = max.coerceAtLeast(state.minShock)
    }

    fun setConfidenceRange(min: Float, max: Float) {
        state.minConfidence = min.coerceIn(0f, 1f)
        state.maxConfidence = max.coerceIn(state.minConfidence, 1f)
    }

    fun setSegmentId(segmentId: String?) {
        state.segmentId = segmentId?.takeIf { it.isNotBlank() }
    }

    fun setNodeId(nodeId: String?) {
        state.nodeId = nodeId?.takeIf { it.isNotBlank() }
    }

    fun reset() {
        state.reset()
    }

    /**
     * Filters [source] with current state + timeline. Provider algorithms are not invoked.
     */
    fun apply(
        source: List<HeatPoint>,
        timeline: HeatMapTimeline,
    ): HeatMapFilterResult {
        val filter = state.toFilter(timeline)
        val filtered = source.filter { filter.accepts(it) }
        return HeatMapFilterResult(
            points = filtered,
            filter = filter,
            sourcePointCount = source.size,
        )
    }
}
