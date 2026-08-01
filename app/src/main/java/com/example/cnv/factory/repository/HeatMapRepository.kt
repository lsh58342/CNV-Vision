package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext

/**
 * Drawing-scoped HeatMap artifact index (references only).
 * Does not change HeatMap generation / render algorithms.
 */
class HeatMapRepository {

    data class HeatMapRef(
        val drawingId: String,
        val sessionId: String,
        val label: String,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, ArrayDeque<HeatMapRef>>()

    fun put(ref: HeatMapRef) {
        synchronized(lock) {
            val q = byDrawing.getOrPut(ref.drawingId) { ArrayDeque() }
            q.addLast(ref)
            while (q.size > 20) q.removeFirst()
        }
    }

    fun forDrawing(drawingId: String): List<HeatMapRef> =
        synchronized(lock) { byDrawing[drawingId]?.toList().orEmpty() }

    fun latestForDrawing(drawingId: String): HeatMapRef? =
        forDrawing(drawingId).lastOrNull()

    fun forCurrentDrawing(context: CurrentContext = CurrentContext.get()): List<HeatMapRef> {
        val drawingId = context.drawingId ?: return emptyList()
        return forDrawing(drawingId)
    }

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) { byDrawing.remove(drawingId) }
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }
}
