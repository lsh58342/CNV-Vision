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
    /** Shock emphasis when point.shockStrength >= this (from intensity config). */
    val shockEmphasisMinStrength: Float = HeatMapIntensityConfig.DEFAULT_MEDIUM,
) {
    fun colorFor(intensity: HeatIntensity): Int = when (intensity) {
        HeatIntensity.LOW -> colorLow
        HeatIntensity.MEDIUM -> colorMedium
        HeatIntensity.HIGH -> colorHigh
        HeatIntensity.CRITICAL -> colorCritical
    }

    companion object {
        val DEFAULT = HeatMapDisplayConfig()
        val DEFAULT_LOW = Color.parseColor("#42A5F5")
        val DEFAULT_MEDIUM = Color.parseColor("#FFEE58")
        val DEFAULT_HIGH = Color.parseColor("#FF9800")
        val DEFAULT_CRITICAL = Color.parseColor("#E53935")
        val DEFAULT_SHOCK_EMPHASIS = Color.parseColor("#FF1744")
        val DEFAULT_ROUTE = Color.parseColor("#9E9E9E")
        val DEFAULT_ORIGIN = Color.parseColor("#FFEB3B")
        val DEFAULT_ZONE_BORDER = Color.parseColor("#80CBC4")
        val DEFAULT_ZONE_HIGHLIGHT = Color.parseColor("#26A69A")
        const val DEFAULT_POINT_RADIUS = 7f
        const val DEFAULT_SHOCK_RING_RADIUS = 11f
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
