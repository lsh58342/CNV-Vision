package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Zone

/**
 * Zone store. Zone is the top-level inspection unit.
 * Operation mode must not mutate zones (enforced by callers / editors).
 */
class ZoneRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Zone>()

    fun upsert(zone: Zone) {
        synchronized(lock) { items[zone.id] = zone }
    }

    fun get(id: String): Zone? = synchronized(lock) { items[id] }

    fun delete(id: String): Boolean = synchronized(lock) { items.remove(id) != null }

    fun forFloor(floorId: String): List<Zone> =
        synchronized(lock) { items.values.filter { it.floorId == floorId } }

    fun forRoute(routeId: String): List<Zone> =
        synchronized(lock) { items.values.filter { it.routeId == routeId } }

    fun current(context: CurrentContext = CurrentContext.get()): Zone? {
        val id = context.zoneId ?: return null
        return get(id)
    }

    fun listForCurrentFloor(context: CurrentContext = CurrentContext.get()): List<Zone> {
        val floorId = context.floorId ?: return emptyList()
        return forFloor(floorId)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
