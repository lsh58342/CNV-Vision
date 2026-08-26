package com.example.cnv.vio

import com.example.cnv.debug.TrackingAttitudeProbe
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Complementary-filter style heading fusion.
 * Gyro for short-term rotation; visual flow heading corrects long-term drift.
 * Does **not** integrate accelerometer for distance.
 */
class VisualInertialFusion(
    private val config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
) {

    private var fusedHeadingDeg: Float = 0f
    private var cumulativeRotationDeg: Float = 0f
    private var lastGyroNs: Long = 0L
    private var lastAngularVelocityDegPerSec: Float = 0f
    private var initialized: Boolean = false

    fun headingDeg(): Float = fusedHeadingDeg

    fun cumulativeRotationDeg(): Float = cumulativeRotationDeg

    fun angularVelocityDegPerSec(): Float = lastAngularVelocityDegPerSec

    fun reset() {
        fusedHeadingDeg = 0f
        cumulativeRotationDeg = 0f
        lastGyroNs = 0L
        lastAngularVelocityDegPerSec = 0f
        initialized = false
    }

    /**
     * @param gyroZRadPerSec device Z angular rate (rad/s)
     * @param timestampNs sensor timestamp
     * @param rotationVectorYawDeg optional TYPE_ROTATION_VECTOR / GAME_ROTATION yaw
     */
    fun onGyro(
        gyroZRadPerSec: Float,
        timestampNs: Long,
        rotationVectorYawDeg: Float? = null,
    ) {
        val dps = Math.toDegrees(gyroZRadPerSec.toDouble()).toFloat()
        lastAngularVelocityDegPerSec = dps
        if (lastGyroNs > 0L) {
            val dt = (timestampNs - lastGyroNs) * NS_TO_SEC
            if (dt > 0.0 && dt < config.maxDtSec) {
                val delta = dps * dt.toFloat()
                fusedHeadingDeg = TrackingAttitudeProbe.normalizeDeg(fusedHeadingDeg + delta)
                cumulativeRotationDeg += delta
            }
        } else if (!initialized && rotationVectorYawDeg != null) {
            fusedHeadingDeg = TrackingAttitudeProbe.normalizeDeg(rotationVectorYawDeg)
            initialized = true
        }
        lastGyroNs = timestampNs
        if (rotationVectorYawDeg != null) {
            // Light pull toward absolute yaw when available (no magnetometer required for GAME_RV).
            fusedHeadingDeg = TrackingAttitudeProbe.blendHeading(
                fusedHeadingDeg,
                rotationVectorYawDeg,
                0.97f,
            )
            initialized = true
        }
    }

    /**
     * Correct fused heading with visual motion direction when features are healthy.
     */
    fun onVisualMotion(
        txPx: Double,
        tyPx: Double,
        inlierRatio: Float,
        confidence: Float,
    ) {
        val mag = hypot(txPx, tyPx)
        if (mag < config.noiseFloorPx || inlierRatio < 0.25f || confidence < 0.15f) {
            return
        }
        val visualHeading = TrackingAttitudeProbe.headingFromDelta(txPx, tyPx)
        if (!initialized) {
            fusedHeadingDeg = visualHeading
            initialized = true
            return
        }
        fusedHeadingDeg = TrackingAttitudeProbe.blendHeading(
            fusedHeadingDeg,
            visualHeading,
            config.gyroWeight,
        )
    }

    fun headingErrorDeg(routeHeadingDeg: Float): Float {
        return abs(TrackingAttitudeProbe.normalizeDeg(fusedHeadingDeg - routeHeadingDeg))
    }

    companion object {
        private const val NS_TO_SEC = 1e-9

        fun residualFlowErrorPx(tx: Double, ty: Double, pairsTx: List<Double>, pairsTy: List<Double>): Float {
            if (pairsTx.isEmpty()) return 0f
            var sum = 0.0
            for (i in pairsTx.indices) {
                val dx = pairsTx[i] - tx
                val dy = pairsTy[i] - ty
                sum += sqrt(dx * dx + dy * dy)
            }
            return (sum / pairsTx.size).toFloat()
        }

        fun flowHeadingDeg(tx: Double, ty: Double): Float =
            TrackingAttitudeProbe.headingFromDelta(tx, ty)

        fun atan2Deg(y: Double, x: Double): Float =
            TrackingAttitudeProbe.normalizeDeg(Math.toDegrees(atan2(y, x)).toFloat())
    }
}
