package com.example.cnv.heatmap

import android.graphics.Color

/**
 * Display-only colors for HeatMap Viewer (STEP 15).
 * No heat calculation — Viewer paints Repository points with these colors.
 */
data class HeatMapDisplayConfig(
    val colorLow: Int = DEFAULT_LOW,
    val colorMedium: Int = DEFAULT_MEDIUM,
    val colorHigh: Int = DEFAULT_HIGH,
    val colorCritical: Int = DEFAULT_CRITICAL,
    val shockEmphasisColor: Int = DEFAULT_SHOCK_EMPHASIS,
    val routeColor: Int = DEFAULT_ROUTE,
    val originColor: Int = DEFAULT_ORIGIN,
    val zoneBorderColor: Int = DEFAULT_ZONE_BORDER,
    val zoneHighlightColor: Int = DEFAULT_ZONE_HIGHLIGHT,
    val pointRadiusPx: Float = DEFAULT_POINT_RADIUS,
    val shockRingRadiusPx: Float = DEFAULT_SHOCK_RING_RADIUS,
    val originRadiusPx: Float = DEFAULT_ORIGIN_RADIUS,
    /** Shock emphasis when point.shockStrength (g) >= this. */
    val shockEmphasisMinStrength: Float = HeatMapIntensityConfig.DEFAULT_RECORD,
) {
    fun colorFor(intensity: HeatIntensity): Int = when (intensity) {
        HeatIntensity.LOW -> colorLow
        HeatIntensity.MEDIUM -> colorMedium
        HeatIntensity.HIGH -> colorHigh
        HeatIntensity.CRITICAL -> colorCritical
    }

    fun colorForShockG(shockG: Float): Int =
        HeatMapIntensityConfig.active().colorForShockG(shockG)

    companion object {
        val DEFAULT = HeatMapDisplayConfig()
        val DEFAULT_LOW = HeatMapIntensityConfig.COLOR_GREEN
        val DEFAULT_MEDIUM = HeatMapIntensityConfig.COLOR_YELLOW
        val DEFAULT_HIGH = HeatMapIntensityConfig.COLOR_ORANGE
        val DEFAULT_CRITICAL = HeatMapIntensityConfig.COLOR_RED
        val DEFAULT_SHOCK_EMPHASIS = HeatMapIntensityConfig.COLOR_RED
        val DEFAULT_ROUTE = Color.parseColor("#9E9E9E")
        val DEFAULT_ORIGIN = Color.parseColor("#4CAF50")
        val DEFAULT_ZONE_BORDER = Color.parseColor("#80CBC4")
        val DEFAULT_ZONE_HIGHLIGHT = Color.parseColor("#26A69A")
        const val DEFAULT_POINT_RADIUS = 8f
        const val DEFAULT_SHOCK_RING_RADIUS = 12f
        const val DEFAULT_ORIGIN_RADIUS = 10f
    }
}

data class HeatMapViewerLayerFlags(
    val heatMap: Boolean = true,
    val route: Boolean = true,
    val zone: Boolean = true,
    val origin: Boolean = true,
    val shock: Boolean = true,
)

/** Zone polyline already in drawing coordinates — Viewer does not compute heat. */
data class HeatMapZoneOverlay(
    val zoneId: String,
    val name: String,
    val colorArgb: Int,
    val points: List<Pair<Double, Double>>,
)
