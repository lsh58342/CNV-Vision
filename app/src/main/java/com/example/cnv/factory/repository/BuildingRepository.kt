package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Building

/**
 * Building store. Current Context uses [CurrentContext.factoryId].
 */
class BuildingRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Building>()

    fun upsert(building: Building) {
        synchronized(lock) { items[building.id] = building }
    }

    fun get(id: String): Building? = synchronized(lock) { items[id] }

    fun delete(id: String): Boolean = synchronized(lock) { items.remove(id) != null }

    fun forFactory(factoryId: String): List<Building> =
        synchronized(lock) { items.values.filter { it.factoryId == factoryId } }

    fun current(context: CurrentContext = CurrentContext.get()): Building? {
        val id = context.buildingId ?: return null
        return get(id)
    }

    fun listForCurrentFactory(context: CurrentContext = CurrentContext.get()): List<Building> {
        val factoryId = context.factoryId ?: return emptyList()
        return forFactory(factoryId)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
