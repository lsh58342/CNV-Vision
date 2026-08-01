package com.example.cnv.heatmap

import com.example.cnv.inspection.InspectionSession

/**
 * Mode-specific HeatPoint generator. Renderer never sees providers.
 * Filter pipeline feeds [fromFilterResult] without re-running generation algorithms.
 */
interface HeatMapProvider {
    /** Stable name for debug HUD. */
    val providerName: String

    fun generateHeatPoints(session: InspectionSession): List<HeatPoint>

    /**
     * Accepts already-filtered points. Default: pass-through (no extra calculation).
     */
    fun fromFilterResult(result: HeatMapFilterResult): List<HeatPoint> = result.points
}
