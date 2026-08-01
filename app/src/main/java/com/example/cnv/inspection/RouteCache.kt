package com.example.cnv.inspection

/**
 * Read-only cache of the frozen route snapshot used by the active inspection.
 * Does not validate or mutate Route topology.
 */
class RouteCache(
    private val limit: Int = InspectionConfig.DEFAULT_CACHE_LIMIT,
) {

    private val lock = Any()
    private var current: RouteSnapshot? = null
    private val history = ArrayDeque<RouteSnapshot>()

    fun put(snapshot: RouteSnapshot) {
        synchronized(lock) {
            current = snapshot
            history.addLast(snapshot)
            while (history.size > limit) {
                history.removeFirst()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            current = null
            history.clear()
        }
    }

    fun current(): RouteSnapshot? = synchronized(lock) { current }

    fun routeVersion(): String? = current()?.routeVersion

    fun routeHash(): String? = current()?.routeHash

    fun history(): List<RouteSnapshot> = synchronized(lock) { history.toList() }
}
