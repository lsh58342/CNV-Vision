package com.example.cnv.zone.editor

import com.example.cnv.factory.model.Zone
import com.example.cnv.map.Route
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper

/**
 * Resolves Zone polyline/segment ids with backward compatibility.
 */
object ZonePolylineResolver {
    fun resolvedIds(zone: Zone, route: Route): List<String> {
        if (zone.polylineIds.isNotEmpty()) return zone.polylineIds.distinct()
        return RouteHighlightHelper.segmentIdsBetween(route, zone.start, zone.end).toList()
    }
}
