package com.example.cnv.heatmap

/**
 * Owns timeline range only (Start/End). No playback / replay.
 */
class HeatMapTimelineController {

    @Volatile
    private var timeline: HeatMapTimeline = HeatMapTimeline.EMPTY

    fun timeline(): HeatMapTimeline = timeline

    /**
     * Bind full data extent from points.
     * Preserves relative selection when the same stream expands.
     */
    fun bindDataExtent(points: List<HeatPoint>) {
        if (points.isEmpty()) {
            timeline = HeatMapTimeline.EMPTY
            return
        }
        val start = points.minOf { it.timestampNs }
        val end = points.maxOf { it.timestampNs }.coerceAtLeast(start)
        val prev = timeline
        if (prev.hasData && prev.dataStartNs == start && prev.dataEndNs == end) {
            return
        }
        if (prev.hasData && prev.dataStartNs == start && end >= prev.dataEndNs) {
            val oldSpan = (prev.dataEndNs - prev.dataStartNs).coerceAtLeast(1L)
            val start01 = ((prev.rangeStartNs - prev.dataStartNs).toDouble() / oldSpan).coerceIn(0.0, 1.0)
            val end01 = ((prev.rangeEndNs - prev.dataStartNs).toDouble() / oldSpan).coerceIn(start01, 1.0)
            val newSpan = (end - start).coerceAtLeast(1L)
            timeline = HeatMapTimeline(
                dataStartNs = start,
                dataEndNs = end,
                rangeStartNs = start + (newSpan * start01).toLong(),
                rangeEndNs = start + (newSpan * end01).toLong(),
            )
            return
        }
        timeline = HeatMapTimeline(
            dataStartNs = start,
            dataEndNs = end,
            rangeStartNs = start,
            rangeEndNs = end,
        )
    }

    /**
     * Slider progress 0..1 mapped to end of range (start fixed at data start).
     */
    fun setEndProgress(progress01: Float) {
        val t = timeline
        if (!t.hasData) return
        val p = progress01.coerceIn(0f, 1f)
        val end = t.dataStartNs + ((t.dataEndNs - t.dataStartNs) * p).toLong()
        timeline = t.copy(rangeStartNs = t.dataStartNs, rangeEndNs = end.coerceAtLeast(t.dataStartNs))
    }

    /** Dual-range: start and end as 0..1 of data span. */
    fun setRangeProgress(start01: Float, end01: Float) {
        val t = timeline
        if (!t.hasData) return
        val a = start01.coerceIn(0f, 1f)
        val b = end01.coerceIn(a, 1f)
        val span = t.dataEndNs - t.dataStartNs
        timeline = t.copy(
            rangeStartNs = t.dataStartNs + (span * a).toLong(),
            rangeEndNs = t.dataStartNs + (span * b).toLong(),
        )
    }

    fun startProgress01(): Float {
        val t = timeline
        if (!t.hasData) return 0f
        val span = (t.dataEndNs - t.dataStartNs).coerceAtLeast(1L)
        return ((t.rangeStartNs - t.dataStartNs).toDouble() / span).toFloat().coerceIn(0f, 1f)
    }

    fun endProgress01(): Float {
        val t = timeline
        if (!t.hasData) return 1f
        val span = (t.dataEndNs - t.dataStartNs).coerceAtLeast(1L)
        return ((t.rangeEndNs - t.dataStartNs).toDouble() / span).toFloat().coerceIn(0f, 1f)
    }

    fun formatCurrentRange(): String {
        val t = timeline
        if (!t.hasData) return "—"
        val span = (t.dataEndNs - t.dataStartNs).coerceAtLeast(1L)
        val a = ((t.rangeStartNs - t.dataStartNs).toDouble() / span * 100.0)
        val b = ((t.rangeEndNs - t.dataStartNs).toDouble() / span * 100.0)
        return "Range %.0f%% – %.0f%%".format(a, b)
    }

    fun formatStartEnd(): Pair<String, String> {
        val t = timeline
        if (!t.hasData) return "Start —" to "End —"
        val span = (t.dataEndNs - t.dataStartNs).coerceAtLeast(1L)
        val a = ((t.rangeStartNs - t.dataStartNs).toDouble() / span * 100.0)
        val b = ((t.rangeEndNs - t.dataStartNs).toDouble() / span * 100.0)
        return "Start %.0f%%".format(a) to "End %.0f%%".format(b)
    }

    fun reset() {
        val t = timeline
        if (!t.hasData) return
        timeline = t.copy(rangeStartNs = t.dataStartNs, rangeEndNs = t.dataEndNs)
    }
}
