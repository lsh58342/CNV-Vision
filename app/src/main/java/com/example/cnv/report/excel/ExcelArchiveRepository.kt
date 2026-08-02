package com.example.cnv.report.excel

/**
 * Links History Session to an exported Excel file URI (STEP 19-1 Archive).
 */
data class ExcelArchiveEntry(
    val sessionId: String,
    val drawingId: String,
    val fileUri: String,
    val fileName: String,
    val exportedAtMs: Long = System.currentTimeMillis(),
)

/**
 * In-memory Excel Archive — Session ↔ Report File Path (STEP 19-1).
 */
class ExcelArchiveRepository {

    private val lock = Any()
    private val bySession = LinkedHashMap<String, ExcelArchiveEntry>()

    fun put(entry: ExcelArchiveEntry) {
        synchronized(lock) {
            bySession[entry.sessionId] = entry
        }
    }

    fun get(sessionId: String): ExcelArchiveEntry? =
        synchronized(lock) { bySession[sessionId] }

    fun forDrawing(drawingId: String): List<ExcelArchiveEntry> =
        synchronized(lock) { bySession.values.filter { it.drawingId == drawingId } }

    fun remove(sessionId: String) {
        synchronized(lock) { bySession.remove(sessionId) }
    }

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) {
            val ids = bySession.filterValues { it.drawingId == drawingId }.keys.toList()
            ids.forEach { bySession.remove(it) }
        }
    }

    fun clear() {
        synchronized(lock) { bySession.clear() }
    }
}
