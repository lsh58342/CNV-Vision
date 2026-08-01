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
        inspectionRepository.save(result)
        synchronized(lock) {
            val q = zoneToSessionIds.getOrPut(zoneId) { ArrayDeque() }
            q.addLast(result.sessionId)
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
