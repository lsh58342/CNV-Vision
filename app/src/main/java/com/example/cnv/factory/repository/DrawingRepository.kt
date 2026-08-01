package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Drawing

/**
 * Drawing store. Current Context uses [CurrentContext.floorId] / [CurrentContext.drawingId].
 */
class DrawingRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Drawing>()

    fun upsert(drawing: Drawing) {
        synchronized(lock) { items[drawing.id] = drawing }
    }

    fun get(id: String): Drawing? = synchronized(lock) { items[id] }

    fun delete(id: String): Boolean = synchronized(lock) { items.remove(id) != null }

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
