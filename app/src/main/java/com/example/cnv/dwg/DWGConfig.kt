package com.example.cnv.dwg

/**
 * Tunable DWG import / extraction parameters. All magic numbers live here.
 */
data class DWGConfig(
    val importTolerance: Double = DEFAULT_IMPORT_TOLERANCE,
    val mergeTolerance: Double = DEFAULT_MERGE_TOLERANCE,
    val minimumPolylineLength: Double = DEFAULT_MINIMUM_POLYLINE_LENGTH,
    val centerLineTolerance: Double = DEFAULT_CENTER_LINE_TOLERANCE,
    val layerFilter: String = DEFAULT_LAYER_FILTER,
    val debugHudRefreshIntervalMs: Long = DEFAULT_DEBUG_HUD_REFRESH_MS,
) {
    companion object {
        const val DEFAULT_IMPORT_TOLERANCE = 0.5
        const val DEFAULT_MERGE_TOLERANCE = 10.0
        const val DEFAULT_MINIMUM_POLYLINE_LENGTH = 50.0
        const val DEFAULT_CENTER_LINE_TOLERANCE = 25.0
        const val DEFAULT_LAYER_FILTER = "CONVEYOR"
        const val DEFAULT_DEBUG_HUD_REFRESH_MS = 500L

        val DEFAULT: DWGConfig = DWGConfig()
    }
}
