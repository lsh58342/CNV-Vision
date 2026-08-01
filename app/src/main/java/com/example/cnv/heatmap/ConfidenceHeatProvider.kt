package com.example.cnv.heatmap

import com.example.cnv.inspection.InspectionSession

/**
 * Confidence HeatMap provider — structure only (STEP 12-3+).
 */
class ConfidenceHeatProvider : HeatMapProvider {

    override val providerName: String = "ConfidenceHeatProvider"

    override fun generateHeatPoints(session: InspectionSession): List<HeatPoint> {
        // TODO(STEP12-3+): generate confidence heat points from FusionEvent/PositionEvent confidence.
        return emptyList()
    }
}
