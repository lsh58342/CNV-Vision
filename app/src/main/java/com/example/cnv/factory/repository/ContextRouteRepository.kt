package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.map.Route
import com.example.cnv.map.RouteRepository
import com.example.cnv.route.CoordinateMapper

/**
 * Context-scoped facade over existing [RouteRepository].
 * Always carries world [CoordinateMapper] so CAD never falls back to a horizontal line.
 */
class ContextRouteRepository(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val drawingRoutes: DrawingRouteRepository = DrawingRouteRepository(),
) {

    fun drawingRoutes(): DrawingRouteRepository = drawingRoutes

    fun setRoute(
        route: Route,
        drawingId: String? = CurrentContext.get().drawingId,
        mapper: CoordinateMapper? = null,
    ) {
        val resolvedMapper = mapper
            ?: drawingId?.let { drawingRoutes.getMapper(it) }
            ?: routeRepository.currentMapper()
        routeRepository.setRoute(route, resolvedMapper)
        CurrentContext.get().selectRoute(route.id)
        if (drawingId != null) {
            drawingRoutes.put(drawingId, route, resolvedMapper)
            SitePersistenceRepository.saveDrawingRouteAsync(drawingId, route, resolvedMapper)
        }
        println(
            "LOG[RouteRepo][SET] drawingId=$drawingId route=${route.id} " +
                "segments=${route.segments.size} hasMapper=${resolvedMapper != null}",
        )
    }

    fun activateForDrawing(drawingId: String): Boolean {
        val route = drawingRoutes.get(drawingId) ?: return false
        val mapper = drawingRoutes.getMapper(drawingId)
            ?: drawingRoutes.findCompatibleMapper(route)
        if (mapper != null && drawingRoutes.getMapper(drawingId) == null) {
            // Persist recovered geometry onto this Drawing for next hydrate.
            drawingRoutes.put(drawingId, route, mapper)
            SitePersistenceRepository.saveDrawingRouteAsync(drawingId, route, mapper)
            println(
                "LOG[RouteRepo][ACTIVATE] recovered mapper for drawingId=$drawingId " +
                    "from sibling Drawing",
            )
        }
        routeRepository.setRoute(route, mapper)
        CurrentContext.get().selectRoute(route.id)
        println(
            "LOG[RouteRepo][ACTIVATE] drawingId=$drawingId route=${route.id} " +
                "hasMapper=${mapper != null}",
        )
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
