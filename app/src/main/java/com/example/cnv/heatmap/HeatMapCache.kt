package com.example.cnv.heatmap

/**
 * Per-session HeatMap caches. Invalidates only the layers that changed.
 */
class HeatMapCache {

    data class SourceLayer(
        val eventCount: Int,
        val mode: HeatMapMode,
        val points: List<HeatPoint>,
    )

    data class FilterLayer(
        val filterKey: String,
        val points: List<HeatPoint>,
        val cells: List<HeatCell>,
        val statistics: HeatStatistics,
        val sessionStatistics: HeatSessionStatistics,
        val analysis: HeatMapAnalysis,
    )

    data class SessionBucket(
        var source: SourceLayer? = null,
        var filtered: FilterLayer? = null,
    )

    private val sessions = LinkedHashMap<String, SessionBucket>(8, 0.75f, true)

    private var hits: Long = 0
    private var misses: Long = 0

    fun metrics(): HeatMapCacheMetrics {
        val total = hits + misses
        val rate = if (total == 0L) 0.0 else hits.toDouble() / total
        val cachedCells = sessions.values.sumOf { it.filtered?.cells?.size ?: 0 }
        return HeatMapCacheMetrics(
            hits = hits,
            misses = misses,
            hitRate = rate,
            sessionCount = sessions.size,
            cachedCellCount = cachedCells,
            cachedPointCount = sessions.values.sumOf { it.source?.points?.size ?: 0 },
        )
    }

    fun clear() {
        sessions.clear()
        hits = 0
        misses = 0
    }

    fun clearSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun getSource(
        sessionId: String,
        eventCount: Int,
        mode: HeatMapMode,
    ): List<HeatPoint>? {
        val bucket = sessions[sessionId] ?: run {
            misses++
            return null
        }
        val src = bucket.source
        if (src != null && src.eventCount == eventCount && src.mode == mode) {
            hits++
            return src.points
        }
        misses++
        return null
    }

    fun putSource(
        sessionId: String,
        eventCount: Int,
        mode: HeatMapMode,
        points: List<HeatPoint>,
    ) {
        val bucket = sessions.getOrPut(sessionId) { SessionBucket() }
        bucket.source = SourceLayer(eventCount, mode, points)
        // Source change invalidates filter layer.
        bucket.filtered = null
        trim()
    }

    fun getFilterLayer(sessionId: String, filterKey: String): FilterLayer? {
        val bucket = sessions[sessionId] ?: run {
            misses++
            return null
        }
        val layer = bucket.filtered
        if (layer != null && layer.filterKey == filterKey) {
            hits++
            return layer
        }
        misses++
        return null
    }

    fun putFilterLayer(sessionId: String, layer: FilterLayer) {
        val bucket = sessions.getOrPut(sessionId) { SessionBucket() }
        bucket.filtered = layer
        trim()
    }

    private fun trim() {
        while (sessions.size > MAX_SESSIONS) {
            val eldest = sessions.entries.iterator()
            if (!eldest.hasNext()) break
            eldest.next()
            eldest.remove()
        }
    }

    companion object {
        private const val MAX_SESSIONS = 4
    }
}

data class HeatMapCacheMetrics(
    val hits: Long = 0,
    val misses: Long = 0,
    val hitRate: Double = 0.0,
    val sessionCount: Int = 0,
    val cachedCellCount: Int = 0,
    val cachedPointCount: Int = 0,
)
