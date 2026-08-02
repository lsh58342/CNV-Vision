package com.example.cnv.factory.repository

import com.example.cnv.map.Route

/**
 * In-memory Drawing → Route map (restored from Room on startup).
 */
class DrawingRouteRepository {
    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, Route>()

    fun put(drawingId: String, route: Route) {
        synchronized(lock) { byDrawing[drawingId] = route }
    }

    fun get(drawingId: String): Route? = synchronized(lock) { byDrawing[drawingId] }

    fun remove(drawingId: String) {
        synchronized(lock) { byDrawing.remove(drawingId) }
    }

    fun replaceAll(map: Map<String, Route>) {
        synchronized(lock) {
            byDrawing.clear()
            byDrawing.putAll(map)
        }
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }
}
