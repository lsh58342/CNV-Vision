package com.example.cnv.heatmap

/**
 * Time-range selection for HeatMap (not a replay player).
 */
data class HeatMapTimeline(
    val dataStartNs: Long = 0L,
    val dataEndNs: Long = 0L,
    val rangeStartNs: Long = Long.MIN_VALUE,
    val rangeEndNs: Long = Long.MAX_VALUE,
) {
    val hasData: Boolean get() = dataEndNs > dataStartNs

    fun summary(): String {
        if (!hasData) return "time=—"
        val span = (dataEndNs - dataStartNs).coerceAtLeast(1L)
        val a = ((rangeStartNs - dataStartNs).toDouble() / span * 100.0).coerceIn(0.0, 100.0)
        val b = ((rangeEndNs - dataStartNs).toDouble() / span * 100.0).coerceIn(0.0, 100.0)
        return "time=%.0f%%–%.0f%%".format(a, b)
    }

    companion object {
        val EMPTY: HeatMapTimeline = HeatMapTimeline()
    }
}
