package com.example.cnv.heatmap

/**
 * Immutable filter criteria for HeatPoints (visualization only).
 */
data class HeatMapFilter(
    val sessionId: String? = null,
    val timeMinNs: Long = Long.MIN_VALUE,
    val timeMaxNs: Long = Long.MAX_VALUE,
    val minShock: Float = 0f,
    val maxShock: Float = Float.MAX_VALUE,
    val minConfidence: Float = 0f,
    val maxConfidence: Float = Float.MAX_VALUE,
    val segmentId: String? = null,
    val nodeId: String? = null,
) {
    fun accepts(point: HeatPoint): Boolean {
        if (sessionId != null && point.sessionId != sessionId) return false
        if (point.timestampNs < timeMinNs || point.timestampNs > timeMaxNs) return false
        if (point.shockLevel < minShock || point.shockLevel > maxShock) return false
        if (point.confidence < minConfidence || point.confidence > maxConfidence) return false
        if (segmentId != null && point.segmentId != segmentId) return false
        if (nodeId != null && point.nodeId != nodeId) return false
        return true
    }

    companion object {
        val ALL: HeatMapFilter = HeatMapFilter()
    }
}
