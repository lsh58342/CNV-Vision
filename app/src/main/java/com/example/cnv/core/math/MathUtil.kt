package com.example.cnv.core.math

import kotlin.math.sqrt

/**
 * Shared vector math helpers.
 */
object MathUtil {
    fun magnitude3(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    fun clamp(value: Float, min: Float, max: Float): Float =
        value.coerceIn(min, max)
}
