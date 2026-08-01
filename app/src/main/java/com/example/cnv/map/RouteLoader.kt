package com.example.cnv.map

/**
 * Pluggable route source for STEP 10-3 (Route.json / DWG-derived topology).
 * Implementations are not provided in this STEP.
 */
interface RouteLoader {
    /**
     * Loads a [Route] from an opaque source identifier (path, URI, asset key).
     * @return null when the source cannot be loaded.
     */
    fun load(source: String): Route?
}
