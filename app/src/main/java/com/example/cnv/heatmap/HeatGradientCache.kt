package com.example.cnv.heatmap

/**
 * Discrete gradient color cache for render path (no allocation per cell).
 */
object HeatGradientCache {

    private const val BINS = 256
    private val colors: IntArray = IntArray(BINS) { i ->
        HeatGradient.colorForNormalized(i / (BINS - 1f))
    }

    fun colorForNormalized(t: Float): Int {
        val idx = ((t.coerceIn(0f, 1f)) * (BINS - 1)).toInt().coerceIn(0, BINS - 1)
        return colors[idx]
    }

    fun colorForIntensity(intensity: Float, maxShock: Float): Int {
        val norm = if (maxShock <= 1e-3f) 0f else (intensity / maxShock).coerceIn(0f, 1f)
        return colorForNormalized(norm)
    }
}
