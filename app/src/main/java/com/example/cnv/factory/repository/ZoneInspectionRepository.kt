package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.InspectionResult

/**
 * Drawing-scoped inspection history index.
 * Does not alter InspectionSession algorithms — only associates results to Drawing.
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
        // Zone-scoped lookups are no longer primary; return empty unless indexed under same key (legacy).
        return historyForDrawing(zoneId)
    }

    fun historyForCurrentZone(context: CurrentContext = CurrentContext.get()): List<InspectionResult> =
        historyForCurrentDrawing(context)

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) {
            val ids = drawingToSessionIds.remove(drawingId)?.toList().orEmpty()
            ids.forEach { sessionId ->
                // Leave underlying results; index only is Drawing-scoped for this phase.
            }
        }
    }

    fun underlying(): InspectionRepository = inspectionRepository
}
