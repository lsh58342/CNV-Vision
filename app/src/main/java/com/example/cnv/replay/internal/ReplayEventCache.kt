package com.example.cnv.replay.internal

import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.production.ProductionMetrics
import com.example.cnv.replay.ReplayFrame

/**
 * In-memory Inspection Event cache — loaded once per session.
 * Playback never re-queries Room.
 * STEP 20: binary seek + metrics (no Replay calculation change).
 */
internal class ReplayEventCache {

    @Volatile
    private var summary: InspectionSessionSummary? = null

    @Volatile
    private var frames: List<ReplayFrame> = emptyList()

    fun put(summary: InspectionSessionSummary, frames: List<ReplayFrame>) {
        this.summary = summary
        this.frames = frames.toList()
        ProductionMetrics.setReplayCacheSize(this.frames.size)
    }

    fun clear() {
        summary = null
        frames = emptyList()
        ProductionMetrics.setReplayCacheSize(0)
    }

    fun isEmpty(): Boolean = frames.isEmpty()

    fun summary(): InspectionSessionSummary? = summary

    fun sessionId(): String? = summary?.sessionId

    fun frames(): List<ReplayFrame> = frames

    fun frameCount(): Int = frames.size

    fun get(index: Int): ReplayFrame? = frames.getOrNull(index)

    fun indexOfEventId(eventId: Long): Int =
        frames.indexOfFirst { it.eventId == eventId }

    fun nearestIndexByTimestamp(timestampNs: Long): Int {
        val list = frames
        if (list.isEmpty()) return -1
        var lo = 0
        var hi = list.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].timestampNs < timestampNs) lo = mid + 1 else hi = mid
        }
        val cand = lo
        if (cand == 0) return 0
        val prev = cand - 1
        return if (
            kotlin.math.abs(list[cand].timestampNs - timestampNs) <
            kotlin.math.abs(list[prev].timestampNs - timestampNs)
        ) {
            cand
        } else {
            prev
        }
    }

    fun nearestIndexByRouteMm(routeMm: Float): Int {
        val list = frames
        if (list.isEmpty()) return -1
        // Route mm is non-decreasing along the session timeline — binary search.
        var lo = 0
        var hi = list.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].routePositionMm < routeMm) lo = mid + 1 else hi = mid
        }
        val cand = lo
        if (cand == 0) return 0
        val prev = cand - 1
        return if (
            kotlin.math.abs(list[cand].routePositionMm - routeMm) <
            kotlin.math.abs(list[prev].routePositionMm - routeMm)
        ) {
            cand
        } else {
            prev
        }
    }
}
