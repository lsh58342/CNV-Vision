package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.core.event.BaseEvent

/**
 * Drawing-scoped inspection history index + Room persistence facade (STEP 13).
 * Does not alter InspectionSession algorithms.
 */
class ZoneInspectionRepository(
    private val inspectionRepository: InspectionRepository = InspectionRepository(),
) {

    private val lock = Any()
    private val drawingToSessionIds = LinkedHashMap<String, ArrayDeque<String>>()

    fun save(drawingId: String, result: InspectionResult) {
        val exists = inspectionRepository.all().any { it.sessionId == result.sessionId }
        if (!exists) {
            inspectionRepository.save(result)
        }
        index(drawingId, result.sessionId)
    }

    /**
     * Persist finished session + events to Room (Drawing-scoped).
     */
    fun finishSession(
        drawingId: String,
        result: InspectionResult,
        events: List<BaseEvent>,
        appVersion: String,
        inspectionVersion: String = "1",
    ): InspectionSessionSummary {
        val summary = inspectionRepository.finishSession(
            drawingId = drawingId,
            result = result,
            events = events,
            appVersion = appVersion,
            inspectionVersion = inspectionVersion,
        )
        index(drawingId, result.sessionId)
        return summary
    }

    fun createSession(
        drawingId: String,
        sessionId: String,
        startTimeMs: Long,
        appVersion: String,
        routeVersion: String = "",
        calibrationVersion: Int = 0,
        conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
    ) {
        inspectionRepository.createSession(
            sessionId = sessionId,
            drawingId = drawingId,
            startTimeMs = startTimeMs,
            appVersion = appVersion,
            routeVersion = routeVersion,
            calibrationVersion = calibrationVersion,
            conveyorProfile = conveyorProfile,
        )
        index(drawingId, sessionId)
    }

    fun loadSession(sessionId: String): PersistedInspectionSession? =
        inspectionRepository.loadSession(sessionId)

    fun deleteSession(sessionId: String) {
        inspectionRepository.deleteSession(sessionId)
        synchronized(lock) {
            drawingToSessionIds.values.forEach { q -> q.remove(sessionId) }
        }
    }

    fun loadHistorySummaries(drawingId: String): List<InspectionSessionSummary> =
        inspectionRepository.loadHistory(drawingId)

    /** Link an already-saved inspection result to a Drawing (no algorithm change). */
    fun index(drawingId: String, sessionId: String) {
        synchronized(lock) {
            val q = drawingToSessionIds.getOrPut(drawingId) { ArrayDeque() }
            if (!q.contains(sessionId)) {
                q.addLast(sessionId)
            }
            while (q.size > 50) q.removeFirst()
        }
    }

    fun historyForDrawing(drawingId: String): List<InspectionResult> {
        val fromRoom = inspectionRepository.historyAsResults(drawingId)
        if (fromRoom.isNotEmpty()) return fromRoom
        val ids = synchronized(lock) { drawingToSessionIds[drawingId]?.toList().orEmpty() }
        if (ids.isEmpty()) return emptyList()
        val all = inspectionRepository.all().associateBy { it.sessionId }
        return ids.mapNotNull { all[it] }
    }

    fun latestForDrawing(drawingId: String): InspectionResult? =
        historyForDrawing(drawingId).lastOrNull()

    fun historyForCurrentDrawing(context: CurrentContext = CurrentContext.get()): List<InspectionResult> {
        val drawingId = context.drawingId ?: return emptyList()
        return historyForDrawing(drawingId)
    }

    /** @deprecated Prefer [historyForDrawing]. Kept for gradual call-site migration. */
    fun historyForZone(zoneId: String): List<InspectionResult> {
        return historyForDrawing(zoneId)
    }

    fun historyForCurrentZone(context: CurrentContext = CurrentContext.get()): List<InspectionResult> =
        historyForCurrentDrawing(context)

    fun removeForDrawing(drawingId: String) {
        inspectionRepository.deleteForDrawing(drawingId)
        synchronized(lock) {
            drawingToSessionIds.remove(drawingId)
        }
    }

    fun underlying(): InspectionRepository = inspectionRepository
}
