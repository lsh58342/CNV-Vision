package com.example.cnv.heatmap

import com.example.cnv.route.CoordinateMapper

/**
 * Creates [HeatMapProvider] for a [HeatMapMode]. Renderer never calls this.
 */
object HeatMapFactory {

    fun create(
        mode: HeatMapMode,
        mapperProvider: () -> CoordinateMapper?,
    ): HeatMapProvider {
        return when (mode) {
            HeatMapMode.SHOCK -> ShockHeatProvider(mapperProvider)
            HeatMapMode.COVERAGE -> CoverageHeatProvider()
            HeatMapMode.CONFIDENCE -> ConfidenceHeatProvider()
        }
    }
}
