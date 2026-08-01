package com.example.cnv.factory.repository

/**
 * Per-floor commissioning setup flags (DWG / Route lock).
 * Does not change Route / DWG algorithms.
 */
class FloorSetupRepository {

    data class FloorSetup(
        val floorId: String,
        val dwgRegistered: Boolean = false,
        val routeLocked: Boolean = false,
    )

    private val lock = Any()
    private val items = LinkedHashMap<String, FloorSetup>()

    fun get(floorId: String): FloorSetup =
        synchronized(lock) { items[floorId] ?: FloorSetup(floorId) }

    fun setDwgRegistered(floorId: String, registered: Boolean) {
        synchronized(lock) {
            val cur = items[floorId] ?: FloorSetup(floorId)
            items[floorId] = cur.copy(dwgRegistered = registered)
        }
    }

    fun setRouteLocked(floorId: String, locked: Boolean) {
        synchronized(lock) {
            val cur = items[floorId] ?: FloorSetup(floorId)
            items[floorId] = cur.copy(routeLocked = locked)
        }
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
