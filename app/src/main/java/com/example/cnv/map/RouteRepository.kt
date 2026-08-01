package com.example.cnv.map

/**
 * In-memory route store. Future STEP 10-3 loaders call [setRoute] after [RouteLoader.load].
 */
class RouteRepository {

    @Volatile
    private var route: Route? = null

    fun setRoute(route: Route) {
        this.route = route
    }

    fun clear() {
        route = null
    }

    fun current(): Route? = route

    fun hasRoute(): Boolean = route != null
}
