package com.example.cnv.heatmap

import android.graphics.Color
import kotlin.math.roundToInt

/**
 * Blue → Green → Yellow → Orange → Red. LUT cached for render performance.
 */
object HeatGradient {

    private const val LUT_SIZE = 256
    private val lut: IntArray = IntArray(LUT_SIZE) { index ->
        colorAt(index / (LUT_SIZE - 1f))
    }

    fun colorForNormalized(t: Float): Int {
        val x = t.coerceIn(0f, 1f)
        val idx = (x * (LUT_SIZE - 1)).roundToInt().coerceIn(0, LUT_SIZE - 1)
        return lut[idx]
    }

    fun colorAt(t: Float): Int {
        val x = t.coerceIn(0f, 1f)
        val stops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val colors = intArrayOf(
            Color.rgb(33, 150, 243),
            Color.rgb(76, 175, 80),
            Color.rgb(255, 235, 59),
            Color.rgb(255, 152, 0),
            Color.rgb(244, 67, 54),
        )
        for (i in 0 until stops.lastIndex) {
            if (x <= stops[i + 1]) {
                val local = (x - stops[i]) / (stops[i + 1] - stops[i])
                return lerpColor(colors[i], colors[i + 1], local)
            }
        }
        return colors.last()
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = Color.red(a)
        val ag = Color.green(a)
        val ab = Color.blue(a)
        val br = Color.red(b)
        val bg = Color.green(b)
        val bb = Color.blue(b)
        return Color.argb(
            180,
            (ar + (br - ar) * t).roundToInt(),
            (ag + (bg - ag) * t).roundToInt(),
            (ab + (bb - ab) * t).roundToInt(),
        )
    }
}
