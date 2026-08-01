package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext

/**
 * Zone-scoped calibration reference store.
 * Does not change CalibrationManager algorithms — stores version linkage only.
 */
class CalibrationRepository {

    data class CalibrationRef(
        val zoneId: String,
        val calibrationVersion: Int,
        val mmPerPixel: Float?,
        val ready: Boolean,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    private val byZone = LinkedHashMap<String, CalibrationRef>()

    fun put(ref: CalibrationRef) {
        synchronized(lock) { byZone[ref.zoneId] = ref }
    }

    fun get(zoneId: String): CalibrationRef? = synchronized(lock) { byZone[zoneId] }

    fun current(context: CurrentContext = CurrentContext.get()): CalibrationRef? {
        val zoneId = context.zoneId ?: return null
        return get(zoneId)
    }

    fun clear() {
        synchronized(lock) { byZone.clear() }
    }
}
