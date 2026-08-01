package com.example.cnv.factory.repository

/**
 * Drawing-scoped CSV export/import metadata (references only).
 */
class CsvMetadataRepository {

    data class CsvMeta(
        val drawingId: String,
        val label: String,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, ArrayDeque<CsvMeta>>()

    fun put(meta: CsvMeta) {
        synchronized(lock) {
            val q = byDrawing.getOrPut(meta.drawingId) { ArrayDeque() }
            q.addLast(meta)
            while (q.size > 20) q.removeFirst()
        }
    }

    fun forDrawing(drawingId: String): List<CsvMeta> =
        synchronized(lock) { byDrawing[drawingId]?.toList().orEmpty() }

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) { byDrawing.remove(drawingId) }
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }
}
