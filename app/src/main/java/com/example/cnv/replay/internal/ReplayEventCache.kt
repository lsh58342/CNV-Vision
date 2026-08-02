package com.example.cnv.replay.internal

import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.replay.ReplayFrame

/**
 * In-memory Inspection Event cache — loaded once per session.
 * Playback never re-queries Room.
 */
internal class ReplayEventCache {

    @Volatile
    private var summary: InspectionSessionSummary? = null

    @Volatile
    private var frames: List<ReplayFrame> = emptyList()

    fun put(summary: InspectionSessionSummary, frames: List<ReplayFrame>) {
        this.summary = summary
        this.frames = frames.toList()
    }

    fun clear() {
        summary = null
        frames = emptyList()
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
        if (frames.isEmpty()) return -1
        return frames.minBy { kotlin.math.abs(it.timestampNs - timestampNs) }.index
    }

    fun nearestIndexByRouteMm(routeMm: Float): Int {
        if (frames.isEmpty()) return -1
        return frames.minBy { kotlin.math.abs(it.routePositionMm - routeMm) }.index
    }
}
