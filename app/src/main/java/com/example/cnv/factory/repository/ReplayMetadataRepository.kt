package com.example.cnv.factory.repository

/**
 * Drawing-scoped Replay metadata (references only — no Replay engine in this phase).
 */
class ReplayMetadataRepository {

    data class ReplayMeta(
        val drawingId: String,
        val sessionId: String,
        val label: String,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, ArrayDeque<ReplayMeta>>()

    fun put(meta: ReplayMeta) {
        synchronized(lock) {
            val q = byDrawing.getOrPut(meta.drawingId) { ArrayDeque() }
            q.addLast(meta)
            while (q.size > 20) q.removeFirst()
        }
    }

    fun forDrawing(drawingId: String): List<ReplayMeta> =
        synchronized(lock) { byDrawing[drawingId]?.toList().orEmpty() }

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) { byDrawing.remove(drawingId) }
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }
}
