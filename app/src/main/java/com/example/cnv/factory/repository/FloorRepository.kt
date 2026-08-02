package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Floor

/**
 * Floor store. Current Context uses [CurrentContext.buildingId].
 */
class FloorRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Floor>()

    fun upsert(floor: Floor, persist: Boolean = true) {
        synchronized(lock) { items[floor.id] = floor }
        if (persist) SitePersistenceRepository.saveFloorAsync(floor)
    }

    fun replaceAll(list: List<Floor>) {
        synchronized(lock) {
            items.clear()
            list.forEach { items[it.id] = it }
        }
    }

    fun get(id: String): Floor? = synchronized(lock) { items[id] }

    fun delete(id: String, persist: Boolean = true): Boolean {
        val removed = synchronized(lock) { items.remove(id) != null }
        if (removed && persist) SitePersistenceRepository.deleteFloorAsync(id)
        return removed
    }

    fun forBuilding(buildingId: String): List<Floor> =
        synchronized(lock) { items.values.filter { it.buildingId == buildingId } }

    fun current(context: CurrentContext = CurrentContext.get()): Floor? {
        val id = context.floorId ?: return null
        return get(id)
    }

    fun listForCurrentBuilding(context: CurrentContext = CurrentContext.get()): List<Floor> {
        val buildingId = context.buildingId ?: return emptyList()
        return forBuilding(buildingId)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
