package com.example.cnv.heatmap

import com.example.cnv.inspection.InspectionSession

/**
 * Coverage HeatMap provider — structure only (STEP 12-3+).
 */
class CoverageHeatProvider : HeatMapProvider {

    override val providerName: String = "CoverageHeatProvider"

    override fun generateHeatPoints(session: InspectionSession): List<HeatPoint> {
        // TODO(STEP12-3+): generate coverage heat points from PositionEvent path density.
        return emptyList()
    }
}
