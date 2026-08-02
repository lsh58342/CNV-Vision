package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext

/**
 * Drawing-scoped calibration reference store.
 * Does not change CalibrationManager algorithms — stores version linkage only.
 */
class CalibrationRepository {

    data class CalibrationRef(
        val drawingId: String,
        val calibrationVersion: Int,
        val mmPerPixel: Float?,
        val ready: Boolean,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, CalibrationRef>()

    fun put(ref: CalibrationRef, persist: Boolean = true) {
        synchronized(lock) { byDrawing[ref.drawingId] = ref }
        if (persist) SitePersistenceRepository.saveCalibrationAsync(ref)
    }

    fun replaceAll(list: List<CalibrationRef>) {
        synchronized(lock) {
            byDrawing.clear()
            list.forEach { byDrawing[it.drawingId] = it }
        }
    }

    fun get(drawingId: String): CalibrationRef? = synchronized(lock) { byDrawing[drawingId] }

    fun current(context: CurrentContext = CurrentContext.get()): CalibrationRef? {
        val drawingId = context.drawingId ?: return null
        return get(drawingId)
    }

    fun removeForDrawing(drawingId: String, persist: Boolean = true) {
        synchronized(lock) { byDrawing.remove(drawingId) }
        // Drawing cascade deletes calibration row from Room.
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }
}
