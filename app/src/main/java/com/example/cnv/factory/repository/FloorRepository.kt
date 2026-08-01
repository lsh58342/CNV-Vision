package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Floor

/**
 * Floor store. Current Context uses [CurrentContext.buildingId].
 */
class FloorRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Floor>()

    fun upsert(floor: Floor) {
        synchronized(lock) { items[floor.id] = floor }
    }

    fun get(id: String): Floor? = synchronized(lock) { items[id] }

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
