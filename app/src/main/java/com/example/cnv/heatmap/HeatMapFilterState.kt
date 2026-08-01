package com.example.cnv.heatmap

/**
 * Mutable UI/controller filter state. Renderer never reads this.
 */
data class HeatMapFilterState(
    var sessionId: String? = null,
    var minShock: Float = 0f,
    var maxShock: Float = Float.MAX_VALUE,
    var minConfidence: Float = 0f,
    var maxConfidence: Float = Float.MAX_VALUE,
    var segmentId: String? = null,
    var nodeId: String? = null,
) {
    fun toFilter(timeline: HeatMapTimeline): HeatMapFilter = HeatMapFilter(
        sessionId = sessionId,
        timeMinNs = timeline.rangeStartNs,
        timeMaxNs = timeline.rangeEndNs,
        minShock = minShock,
        maxShock = maxShock,
        minConfidence = minConfidence,
        maxConfidence = maxConfidence,
        segmentId = segmentId?.takeIf { it.isNotBlank() },
        nodeId = nodeId?.takeIf { it.isNotBlank() },
    )

    fun reset() {
        sessionId = null
        minShock = 0f
        maxShock = Float.MAX_VALUE
        minConfidence = 0f
        maxConfidence = Float.MAX_VALUE
        segmentId = null
        nodeId = null
    }

    fun summary(): String = buildString {
        append("shock=[%.1f,%.1f]".format(minShock, if (maxShock.isFinite()) maxShock else 999f))
        append(" conf=[%.2f,%.2f]".format(minConfidence, if (maxConfidence.isFinite()) maxConfidence else 1f))
        append(" seg=${segmentId ?: "*"}")
        append(" node=${nodeId ?: "*"}")
        append(" session=${sessionId?.take(8) ?: "*"}")
    }
}
