package com.example.cnv.replay.analysis

import com.example.cnv.factory.model.Zone
import com.example.cnv.replay.ReplayEngineApi
import com.example.cnv.replay.ReplayFrame
import java.util.Locale

/**
 * Replay Analysis Tools (STEP 16-1 / 16-2).
 * Uses [ReplayEngineApi] only — never touches Engine internals, cache classes, or Room.
 */
class ReplayAnalysis(
    private val engine: ReplayEngineApi,
    private val config: ReplayAnalysisConfig = ReplayAnalysisConfig.DEFAULT,
) {

    data class JumpTarget(
        val label: String,
        val frameIndex: Int,
        val eventId: Long,
        val highlight: ReplayHighlightKind = ReplayHighlightKind.NONE,
    )

    data class ZoneJumpTarget(
        val zoneId: String,
        val zoneName: String,
        val frameIndex: Int,
        val startRouteMm: Float,
    )

    @Volatile
    private var filter: ReplayFilter = ReplayFilter.NONE

    @Volatile
    private var searchQuery: String = ""

    @Volatile
    private var highlightedZoneId: String? = null

    fun filter(): ReplayFilter = filter

    fun setFilter(next: ReplayFilter) {
        filter = next
    }

    fun searchQuery(): String = searchQuery

    fun setSearchQuery(query: String) {
        searchQuery = query
    }

    fun highlightedZoneId(): String? = highlightedZoneId

    fun clearZoneHighlight() {
        highlightedZoneId = null
    }

    fun lowConfidenceThreshold(): Float = config.lowConfidenceThreshold

    fun visibleFrames(): List<ReplayFrame> {
        var list = engine.events()
        list = applyFilter(list, filter)
        list = applySearch(list, searchQuery)
        return list
    }

    fun shockTargets(): List<JumpTarget> =
        engine.events()
            .filter { it.hasShock }
            .map {
                JumpTarget(
                    label = "Shock #${it.index} · t=${formatElapsed(it.elapsedMs)} · str=${"%.2f".format(it.shockStrength)}",
                    frameIndex = it.index,
                    eventId = it.eventId,
                    highlight = ReplayHighlightKind.SHOCK,
                )
            }

    fun lowConfidenceTargets(): List<JumpTarget> {
        val threshold = config.lowConfidenceThreshold
        return engine.events()
            .filter { it.trackingConfidence in 0f..threshold }
            .map {
                JumpTarget(
                    label = "Low conf #${it.index} · ${"%.2f".format(it.trackingConfidence)} · t=${formatElapsed(it.elapsedMs)}",
                    frameIndex = it.index,
                    eventId = it.eventId,
                    highlight = ReplayHighlightKind.LOW_CONFIDENCE,
                )
            }
    }

    fun zoneTargets(zones: List<Zone>): List<ZoneJumpTarget> {
        val frames = engine.events()
        if (frames.isEmpty()) return emptyList()
        return zones.mapNotNull { zone ->
            val frame = frames.firstOrNull { it.zoneId == zone.id } ?: return@mapNotNull null
            ZoneJumpTarget(
                zoneId = zone.id,
                zoneName = zone.name,
                frameIndex = frame.index,
                startRouteMm = frame.routePositionMm,
            )
        }
    }

    fun jumpToShock(target: JumpTarget): ReplayFrame? {
        highlightedZoneId = null
        engine.seek(target.frameIndex)
        return engine.currentEvent()
    }

    fun jumpToZone(target: ZoneJumpTarget): ReplayFrame? {
        highlightedZoneId = target.zoneId
        engine.seek(target.frameIndex)
        return engine.currentEvent()
    }

    fun jumpToLowConfidence(target: JumpTarget): ReplayFrame? {
        highlightedZoneId = null
        engine.seek(target.frameIndex)
        return engine.currentEvent()
    }

    fun jumpToTimestampNs(timestampNs: Long): ReplayFrame? {
        highlightedZoneId = null
        engine.seekToTimestampNs(timestampNs)
        return engine.currentEvent()
    }

    fun jumpToTimestampMs(timestampMs: Long, treatAsElapsed: Boolean = true): ReplayFrame? {
        val frames = engine.events()
        if (frames.isEmpty()) return null
        val targetNs = if (treatAsElapsed) {
            frames.first().timestampNs + timestampMs * 1_000_000L
        } else {
            timestampMs * 1_000_000L
        }
        return jumpToTimestampNs(targetNs)
    }

    fun jumpToRoutePositionMm(routeMm: Float): ReplayFrame? {
        highlightedZoneId = null
        engine.seekToRoutePositionMm(routeMm)
        return engine.currentEvent()
    }

    fun previousEvent(): ReplayFrame? {
        val frames = visibleFrames().ifEmpty { engine.events() }
        if (frames.isEmpty()) return null
        stepVisible(frames, -1)
        return engine.currentEvent()
    }

    fun nextEvent(): ReplayFrame? {
        val frames = visibleFrames().ifEmpty { engine.events() }
        if (frames.isEmpty()) return null
        stepVisible(frames, +1)
        return engine.currentEvent()
    }

    fun previousShock(): ReplayFrame? = stepMatching(-1) { it.hasShock }

    fun nextShock(): ReplayFrame? = stepMatching(+1) { it.hasShock }

    fun previousZoneBoundary(zones: List<Zone>): ReplayFrame? {
        val targets = zoneTargets(zones).sortedBy { it.frameIndex }
        val cur = engine.currentIndex()
        val prev = targets.lastOrNull { it.frameIndex < cur } ?: return null
        return jumpToZone(prev)
    }

    fun nextZoneBoundary(zones: List<Zone>): ReplayFrame? {
        val targets = zoneTargets(zones).sortedBy { it.frameIndex }
        val cur = engine.currentIndex()
        val next = targets.firstOrNull { it.frameIndex > cur } ?: return null
        return jumpToZone(next)
    }

    fun statistics(): ReplayStatistics {
        val frame = engine.currentEvent() ?: return ReplayStatistics.EMPTY
        val summary = engine.currentSession()
        val wallMs = if (frame.timestampNs > 0L) frame.timestampNs / 1_000_000L else 0L
        return ReplayStatistics(
            currentTimeMs = wallMs,
            elapsedMs = frame.elapsedMs,
            distanceMm = frame.distanceMm,
            currentZoneName = frame.zoneName?.takeIf { it.isNotBlank() } ?: "—",
            hasShock = frame.hasShock,
            shockStrength = frame.shockStrength,
            trackingConfidence = frame.trackingConfidence,
            validationScore = summary?.speedValidation?.validationScore ?: 0f,
            routePositionMm = frame.routePositionMm,
            drawingX = frame.drawingX,
            drawingY = frame.drawingY,
            timestampNs = frame.timestampNs,
        )
    }

    fun currentHighlight(): ReplayHighlightKind {
        val frame = engine.currentEvent() ?: return ReplayHighlightKind.NONE
        return when {
            frame.hasShock -> ReplayHighlightKind.SHOCK
            frame.trackingConfidence in 0f..config.lowConfidenceThreshold ->
                ReplayHighlightKind.LOW_CONFIDENCE
            else -> ReplayHighlightKind.CURRENT
        }
    }

    private fun stepVisible(frames: List<ReplayFrame>, delta: Int) {
        val ordered = frames.map { it.index }
        val cur = engine.currentIndex()
        val pos = ordered.indexOf(cur).takeIf { it >= 0 }
            ?: ordered.indexOfFirst { it >= cur }.takeIf { it >= 0 }
            ?: 0
        val nextPos = (pos + delta).coerceIn(0, ordered.lastIndex)
        engine.seek(ordered[nextPos])
    }

    private fun stepMatching(delta: Int, predicate: (ReplayFrame) -> Boolean): ReplayFrame? {
        val frames = engine.events()
        if (frames.isEmpty()) return null
        val cur = engine.currentIndex()
        val matches = frames.filter(predicate)
        if (matches.isEmpty()) return null
        val target = if (delta < 0) {
            matches.lastOrNull { it.index < cur } ?: matches.first()
        } else {
            matches.firstOrNull { it.index > cur } ?: matches.last()
        }
        highlightedZoneId = null
        engine.seek(target.index)
        return engine.currentEvent()
    }

    private fun applyFilter(frames: List<ReplayFrame>, f: ReplayFilter): List<ReplayFrame> {
        var list = frames
        if (f.shocksOnly) list = list.filter { it.hasShock }
        if (f.lowConfidenceOnly) {
            val t = config.lowConfidenceThreshold
            list = list.filter { it.trackingConfidence in 0f..t }
        }
        if (!f.zoneId.isNullOrBlank()) {
            list = list.filter { it.zoneId == f.zoneId }
        }
        if (f.timeFromNs != null) {
            list = list.filter { it.timestampNs >= f.timeFromNs }
        }
        if (f.timeToNs != null) {
            list = list.filter { it.timestampNs <= f.timeToNs }
        }
        return list
    }

    private fun applySearch(frames: List<ReplayFrame>, query: String): List<ReplayFrame> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return frames
        val sessionId = engine.sessionId().orEmpty().lowercase(Locale.US)
        return frames.filter { frame ->
            sessionId.contains(q) ||
                frame.sessionId.lowercase(Locale.US).contains(q) ||
                frame.zoneName.orEmpty().lowercase(Locale.US).contains(q) ||
                frame.elapsedMs.toString().contains(q) ||
                (frame.timestampNs / 1_000_000L).toString().contains(q) ||
                "%.0f".format(frame.routePositionMm).contains(q) ||
                "%.1f".format(frame.routePositionMm).contains(q) ||
                frame.index.toString() == q
        }
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }
}
