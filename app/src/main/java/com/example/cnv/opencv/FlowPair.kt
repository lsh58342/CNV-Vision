package com.example.cnv.opencv

import org.opencv.core.Point

/**
 * One Lucas-Kanade correspondence between consecutive frames.
 */
data class FlowPair(
    val from: Point,
    val to: Point,
    val error: Float = 0f,
) {
    fun magnitude(): Double {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
