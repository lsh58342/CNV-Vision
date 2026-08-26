package com.example.cnv.factory.repository

import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper

/**
 * In-memory Drawing → Route + world [CoordinateMapper] (restored from Room on startup).
 */
class DrawingRouteRepository {
    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, StoredDrawingRoute>()

    data class StoredDrawingRoute(
        val route: Route,
        val mapper: CoordinateMapper? = null,
    )

    fun put(drawingId: String, route: Route, mapper: CoordinateMapper? = null) {
        synchronized(lock) {
            val existing = byDrawing[drawingId]
            val keepMapper = mapper ?: existing?.mapper?.takeIf { covers(it, route) }
            byDrawing[drawingId] = StoredDrawingRoute(route, keepMapper)
        }
    }

    fun get(drawingId: String): Route? = synchronized(lock) { byDrawing[drawingId]?.route }

    fun getMapper(drawingId: String): CoordinateMapper? =
        synchronized(lock) { byDrawing[drawingId]?.mapper }

    /**
     * Find any stored mapper that covers [route] segment IDs.
     * Used when a Drawing was hydrated without geometry but a sibling Drawing
     * (same generated route) still has world endpoints.
     */
    fun findCompatibleMapper(route: Route): CoordinateMapper? = synchronized(lock) {
        for ((_, stored) in byDrawing) {
            val mapper = stored.mapper ?: continue
            if (covers(mapper, route)) return mapper
        }
        return null
    }

    fun remove(drawingId: String) {
        synchronized(lock) { byDrawing.remove(drawingId) }
    }

    fun replaceAll(map: Map<String, StoredDrawingRoute>) {
        synchronized(lock) {
            byDrawing.clear()
            byDrawing.putAll(map)
        }
    }

    /** Legacy topology-only hydrate (mapper may be filled later from JSON geometry). */
    fun replaceAllRoutes(map: Map<String, Route>) {
        synchronized(lock) {
            byDrawing.clear()
            map.forEach { (id, route) ->
                byDrawing[id] = StoredDrawingRoute(route, null)
            }
        }
    }

    fun clear() {
        synchronized(lock) { byDrawing.clear() }
    }

    private fun covers(mapper: CoordinateMapper, route: Route): Boolean {
        val ids = mapper.segmentIds()
        return route.segments.isNotEmpty() && route.segments.all { it.id in ids }
    }
}
