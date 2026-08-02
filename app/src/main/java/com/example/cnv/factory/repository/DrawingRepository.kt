package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Drawing

/**
 * Drawing store. Current Context uses [CurrentContext.floorId] / [CurrentContext.drawingId].
 */
class DrawingRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Drawing>()

    fun upsert(drawing: Drawing, persist: Boolean = true) {
        synchronized(lock) { items[drawing.id] = drawing }
        if (persist) SitePersistenceRepository.saveDrawingAsync(drawing)
    }

    fun replaceAll(list: List<Drawing>) {
        synchronized(lock) {
            items.clear()
            list.forEach { items[it.id] = it }
        }
    }

    fun get(id: String): Drawing? = synchronized(lock) { items[id] }

    fun delete(id: String, persist: Boolean = true): Boolean {
        val removed = synchronized(lock) { items.remove(id) != null }
        if (removed && persist) SitePersistenceRepository.deleteDrawingCascadeAsync(id)
        return removed
    }

    fun forFloor(floorId: String): List<Drawing> =
        synchronized(lock) { items.values.filter { it.floorId == floorId } }

    fun current(context: CurrentContext = CurrentContext.get()): Drawing? {
        val id = context.drawingId ?: return null
        return get(id)
    }

    fun listForCurrentFloor(context: CurrentContext = CurrentContext.get()): List<Drawing> {
        val floorId = context.floorId ?: return emptyList()
        return forFloor(floorId)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
