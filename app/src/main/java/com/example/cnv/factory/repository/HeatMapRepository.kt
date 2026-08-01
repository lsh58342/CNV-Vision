package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext

/**
 * Zone-scoped HeatMap artifact index (references only).
 * Does not change HeatMap generation / render algorithms.
 */
class HeatMapRepository {

    data class HeatMapRef(
        val zoneId: String,
        val sessionId: String,
        val label: String,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byZone = LinkedHashMap<String, ArrayDeque<HeatMapRef>>()

    fun put(ref: HeatMapRef) {
        synchronized(lock) {
            val q = byZone.getOrPut(ref.zoneId) { ArrayDeque() }
            q.addLast(ref)
            while (q.size > 20) q.removeFirst()
        }
    }

    fun forZone(zoneId: String): List<HeatMapRef> =
        synchronized(lock) { byZone[zoneId]?.toList().orEmpty() }

    fun latestForZone(zoneId: String): HeatMapRef? =
        forZone(zoneId).lastOrNull()

    fun forCurrentZone(context: CurrentContext = CurrentContext.get()): List<HeatMapRef> {
        val zoneId = context.zoneId ?: return emptyList()
        return forZone(zoneId)
    }

    fun clear() {
        synchronized(lock) { byZone.clear() }
    }
}
