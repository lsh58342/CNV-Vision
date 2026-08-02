package com.example.cnv.replay

import com.example.cnv.factory.model.Zone
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.map.Route

/**
 * Context for [ReplayEngine.loadSession] — Drawing geometry, not Room access by Viewer.
 */
data class ReplayLoadContext(
    val preferredDrawingId: String? = null,
    val route: Route? = null,
    val zones: List<Zone> = emptyList(),
    val layout: HeatMapRouteLayout.LayoutResult? = null,
)
