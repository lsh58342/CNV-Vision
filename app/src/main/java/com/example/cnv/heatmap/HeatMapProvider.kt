package com.example.cnv.heatmap

import com.example.cnv.inspection.InspectionSession

/**
 * Mode-specific HeatPoint generator. Renderer never sees providers.
 */
interface HeatMapProvider {
    /** Stable name for debug HUD. */
    val providerName: String

    fun generateHeatPoints(session: InspectionSession): List<HeatPoint>
}
