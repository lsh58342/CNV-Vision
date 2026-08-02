package com.example.cnv.report.excel

import com.example.cnv.inspection.InspectionRepository

/**
 * Links History Session to an exported Excel file URI (STEP 19-1 Archive / STEP 20-3).
 */
data class ExcelArchiveEntry(
    val sessionId: String,
    val drawingId: String,
    val fileUri: String,
    val fileName: String,
    val exportedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Excel Archive — Session ↔ Report File Path.
 * Memory cache + Room session columns (STEP 20-3).
 */
class ExcelArchiveRepository {

    private val lock = Any()
    private val bySession = LinkedHashMap<String, ExcelArchiveEntry>()

    fun put(entry: ExcelArchiveEntry) {
        synchronized(lock) {
            bySession[entry.sessionId] = entry
        }
    }

    /**
     * Persist to Session entity (background thread) and memory.
     */
    fun putAndPersist(entry: ExcelArchiveEntry, inspections: InspectionRepository) {
        put(entry)
        inspections.saveExcelArchive(entry)
    }

    fun get(sessionId: String): ExcelArchiveEntry? =
        synchronized(lock) { bySession[sessionId] }

    /**
     * Background-thread: hydrate memory from Session columns when cache miss.
     */
    fun getOrLoad(sessionId: String, inspections: InspectionRepository): ExcelArchiveEntry? {
        get(sessionId)?.let { return it }
        val loaded = inspections.loadExcelArchive(sessionId) ?: return null
        put(loaded)
        return loaded
    }

    fun warm(entry: ExcelArchiveEntry?) {
        if (entry == null) return
        put(entry)
    }

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
