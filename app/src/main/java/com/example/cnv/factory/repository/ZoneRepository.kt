package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Zone

/**
 * Zone store scoped to Drawing. Operation mode must not mutate zones (callers enforce).
 */
class ZoneRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Zone>()

    fun upsert(zone: Zone, persist: Boolean = true) {
        synchronized(lock) { items[zone.id] = zone }
        if (persist) SitePersistenceRepository.saveZoneAsync(zone)
    }

    fun replaceAll(list: List<Zone>) {
        synchronized(lock) {
            items.clear()
            list.forEach { items[it.id] = it }
        }
    }

    fun get(id: String): Zone? = synchronized(lock) { items[id] }

    fun delete(id: String, persist: Boolean = true): Boolean {
        val removed = synchronized(lock) { items.remove(id) != null }
        if (removed && persist) SitePersistenceRepository.deleteZoneAsync(id)
        return removed
    }

    fun forDrawing(drawingId: String): List<Zone> =
        synchronized(lock) { items.values.filter { it.drawingId == drawingId } }

    fun forRoute(routeId: String): List<Zone> =
        synchronized(lock) { items.values.filter { it.routeId == routeId } }

    fun current(context: CurrentContext = CurrentContext.get()): Zone? {
        val id = context.zoneId ?: return null
        return get(id)
    }

    fun listForCurrentDrawing(context: CurrentContext = CurrentContext.get()): List<Zone> {
        val drawingId = context.drawingId ?: return emptyList()
        return forDrawing(drawingId)
    }

    fun removeForDrawing(drawingId: String): Int = synchronized(lock) {
        val ids = items.values.filter { it.drawingId == drawingId }.map { it.id }
        ids.forEach { items.remove(it) }
        ids.size
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
