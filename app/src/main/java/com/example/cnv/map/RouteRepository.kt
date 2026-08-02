package com.example.cnv.map

import com.example.cnv.route.CoordinateMapper

/**
 * In-memory route store. [CoordinateMapper] carries Drawing-plane segment endpoints
 * produced by RouteGenerator — required so layouts do not collapse to a horizontal line.
 */
class RouteRepository {

    @Volatile
    private var route: Route? = null

    @Volatile
    private var mapper: CoordinateMapper? = null

    fun setRoute(route: Route, mapper: CoordinateMapper? = null) {
        this.route = route
        if (mapper != null) {
            this.mapper = mapper
        } else if (!covers(this.mapper, route)) {
            this.mapper = null
        }
    }

    fun clear() {
        route = null
        mapper = null
    }

    fun current(): Route? = route

    fun currentMapper(): CoordinateMapper? = mapper

    fun hasRoute(): Boolean = route != null

    private fun covers(mapper: CoordinateMapper?, route: Route): Boolean {
        if (mapper == null) return false
        val ids = mapper.segmentIds()
        return route.segments.isNotEmpty() && route.segments.all { it.id in ids }
    }
}
