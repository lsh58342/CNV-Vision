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
) {

    fun setRoute(route: Route) {
        routeRepository.setRoute(route)
        CurrentContext.get().selectRoute(route.id)
    }

    fun currentRoute(): Route? = routeRepository.current()

    fun currentRouteId(context: CurrentContext = CurrentContext.get()): String? =
        context.routeId ?: routeRepository.current()?.id

    fun hasRoute(): Boolean = routeRepository.hasRoute()

    fun underlying(): RouteRepository = routeRepository
}
