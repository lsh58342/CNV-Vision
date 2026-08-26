package com.example.cnv.analysis

import com.example.cnv.factory.model.Zone
import com.example.cnv.heatmap.DrawingHeatLayer
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.map.Route

/**
 * Inputs for [InspectionAnalysisEngine] (STEP 17).
 */
data class InspectionAnalysisInput(
    val session: PersistedInspectionSession,
    val heatLayer: DrawingHeatLayer? = null,
    /** Full drawing heat for coverage denominator when [heatLayer] is session-scoped only. */
    val drawingHeatLayerForCoverage: DrawingHeatLayer? = null,
    val zones: List<Zone> = emptyList(),
    val route: Route? = null,
    val layout: HeatMapRouteLayout.LayoutResult? = null,
)

data class InspectionAnalysisConfig(
    val lowConfidenceThreshold: Float = DEFAULT_LOW_CONFIDENCE,
    val trackingLossThreshold: Float = DEFAULT_TRACKING_LOSS,
) {
    companion object {
        const val DEFAULT_LOW_CONFIDENCE = 0.5f
        const val DEFAULT_TRACKING_LOSS = 0.15f
        val DEFAULT = InspectionAnalysisConfig()
    }
}
