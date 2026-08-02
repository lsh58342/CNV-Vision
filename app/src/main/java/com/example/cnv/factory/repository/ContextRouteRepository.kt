package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.map.Route
import com.example.cnv.map.RouteRepository

/**
 * Context-scoped facade over existing [RouteRepository].
 * Does not change Route generation / map-matching algorithms.
 */
class ContextRouteRepository(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val drawingRoutes: DrawingRouteRepository = DrawingRouteRepository(),
) {

    fun drawingRoutes(): DrawingRouteRepository = drawingRoutes

    fun setRoute(route: Route, drawingId: String? = CurrentContext.get().drawingId) {
        routeRepository.setRoute(route)
        CurrentContext.get().selectRoute(route.id)
        if (drawingId != null) {
            drawingRoutes.put(drawingId, route)
            SitePersistenceRepository.saveDrawingRouteAsync(drawingId, route)
        }
    }

    fun activateForDrawing(drawingId: String): Boolean {
        val route = drawingRoutes.get(drawingId) ?: return false
        routeRepository.setRoute(route)
        CurrentContext.get().selectRoute(route.id)
        return true
    }

    fun clearActive() {
        routeRepository.clear()
    }

    fun currentRoute(): Route? = routeRepository.current()

    fun currentRouteId(context: CurrentContext = CurrentContext.get()): String? =
        context.routeId ?: routeRepository.current()?.id

    fun hasRoute(): Boolean = routeRepository.hasRoute()

    fun underlying(): RouteRepository = routeRepository
}
