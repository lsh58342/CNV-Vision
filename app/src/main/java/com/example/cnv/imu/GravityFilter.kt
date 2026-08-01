package com.example.cnv.imu

import com.example.cnv.config.IMUConfig
import kotlin.math.sqrt

/**
 * Separates gravity (low-pass) from linear acceleration.
 * Reuses internal arrays to avoid per-sample allocations.
 */
class GravityFilter(
    private val config: IMUConfig,
) {

    private val gravity = FloatArray(VECTOR_SIZE)
    private val linear = FloatArray(VECTOR_SIZE)
    private var initialized = false

    fun process(rawAccel: FloatArray, outGravity: FloatArray, outLinear: FloatArray) {
        require(rawAccel.size >= VECTOR_SIZE)
        require(outGravity.size >= VECTOR_SIZE)
        require(outLinear.size >= VECTOR_SIZE)

        if (!initialized) {
            gravity[0] = rawAccel[0]
            gravity[1] = rawAccel[1]
            gravity[2] = rawAccel[2]
            initialized = true
        } else {
            val alpha = config.lowPassAlpha
            val inv = 1f - alpha
            gravity[0] = alpha * gravity[0] + inv * rawAccel[0]
            gravity[1] = alpha * gravity[1] + inv * rawAccel[1]
            gravity[2] = alpha * gravity[2] + inv * rawAccel[2]
        }

        // High-pass style linear accel: raw - gravity, with light smoothing.
        val hp = config.highPassAlpha
        linear[0] = hp * linear[0] + (1f - hp) * (rawAccel[0] - gravity[0])
        linear[1] = hp * linear[1] + (1f - hp) * (rawAccel[1] - gravity[1])
        linear[2] = hp * linear[2] + (1f - hp) * (rawAccel[2] - gravity[2])

        outGravity[0] = gravity[0]
        outGravity[1] = gravity[1]
        outGravity[2] = gravity[2]
        outLinear[0] = linear[0]
        outLinear[1] = linear[1]
        outLinear[2] = linear[2]
    }

    fun reset() {
        gravity.fill(0f)
        linear.fill(0f)
        initialized = false
    }

    companion object {
        const val VECTOR_SIZE = 3

        fun magnitude(x: Float, y: Float, z: Float): Float =
            sqrt(x * x + y * y + z * z)
    }
}
