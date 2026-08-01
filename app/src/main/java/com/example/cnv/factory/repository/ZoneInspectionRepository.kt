package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.InspectionResult

/**
 * Zone-scoped inspection history index.
 * Does not alter InspectionSession algorithms — only associates results to Zone.
 */
class ZoneInspectionRepository(
    private val inspectionRepository: InspectionRepository = InspectionRepository(),
) {

    private val lock = Any()
    private val zoneToSessionIds = LinkedHashMap<String, ArrayDeque<String>>()

    fun save(zoneId: String, result: InspectionResult) {
        val exists = inspectionRepository.all().any { it.sessionId == result.sessionId }
        if (!exists) {
            inspectionRepository.save(result)
        }
        index(zoneId, result.sessionId)
    }

    /** Link an already-saved inspection result to a Zone (no algorithm change). */
    fun index(zoneId: String, sessionId: String) {
        synchronized(lock) {
            val q = zoneToSessionIds.getOrPut(zoneId) { ArrayDeque() }
            if (!q.contains(sessionId)) {
                q.addLast(sessionId)
            }
            while (q.size > 50) q.removeFirst()
        }
    }

    fun historyForZone(zoneId: String): List<InspectionResult> {
        val ids = synchronized(lock) { zoneToSessionIds[zoneId]?.toList().orEmpty() }
        if (ids.isEmpty()) return emptyList()
        val all = inspectionRepository.all().associateBy { it.sessionId }
        return ids.mapNotNull { all[it] }
    }

    fun latestForZone(zoneId: String): InspectionResult? =
        historyForZone(zoneId).lastOrNull()

    fun historyForCurrentZone(context: CurrentContext = CurrentContext.get()): List<InspectionResult> {
        val zoneId = context.zoneId ?: return emptyList()
        return historyForZone(zoneId)
    }

    fun underlying(): InspectionRepository = inspectionRepository
}
