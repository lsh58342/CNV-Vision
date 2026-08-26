package com.example.cnv.imu

import com.example.cnv.core.config.IMUConfig

/**
 * Rule-based shock peak detector. No AI.
 * Peaks ≥ [ShockUnits.RECORDING_THRESHOLD_G] (1.03g) are always emitted.
 * Peaks within [IMUConfig.eventMergeWindowNs] (100 ms) are merged (keep max).
 */
class ShockDetector(
    private val config: IMUConfig,
) {

    private var peakActive = false
    private var peakStartNs = 0L
    private var peakAccel = 0f
    private var peakGyro = 0f

    private var lastEmittedNs = 0L
    private var lastEmittedPeakAccel = 0f

    /**
     * @return [IMUEvent] when a shock peak completes above confidence threshold; otherwise null.
     */
    fun onSample(
        timestampNs: Long,
        linearAccelMagnitude: Float,
        gyroMagnitude: Float,
    ): IMUEvent? {
        if (linearAccelMagnitude < config.noiseFloorLinearAccel) {
            return closePeakIfNeeded(timestampNs)
        }

        val accelThresholdMs2 = ShockUnits.recordingThresholdMs2()
        val accelTriggered = linearAccelMagnitude >= accelThresholdMs2
        val gyroSupport = gyroMagnitude >= config.shockGyroscopeThreshold

        if (accelTriggered) {
            if (!peakActive) {
                peakActive = true
                peakStartNs = timestampNs
                peakAccel = linearAccelMagnitude
                peakGyro = gyroMagnitude
            } else {
                if (linearAccelMagnitude > peakAccel) {
                    peakAccel = linearAccelMagnitude
                }
                if (gyroMagnitude > peakGyro) {
                    peakGyro = gyroMagnitude
                }
            }
            if (!gyroSupport && peakGyro < config.shockGyroscopeThreshold * GYRO_SOFT_FACTOR) {
                // Keep tracking accel peak; gyro is soft signal only.
            }
            return null
        }

        return closePeakIfNeeded(timestampNs)
    }

    fun reset() {
        peakActive = false
        peakStartNs = 0L
        peakAccel = 0f
        peakGyro = 0f
        lastEmittedNs = 0L
        lastEmittedPeakAccel = 0f
    }

    private fun closePeakIfNeeded(timestampNs: Long): IMUEvent? {
        if (!peakActive) {
            return null
        }
        val duration = timestampNs - peakStartNs
        peakActive = false
        if (duration < config.peakDurationNs) {
            peakAccel = 0f
            peakGyro = 0f
            return null
        }
        val confidence = computeConfidence(peakAccel, peakGyro, duration)
        val peakG = ShockUnits.ms2ToG(peakAccel)
        val recordable = ShockUnits.isRecordableG(peakG) || confidence >= config.confidenceThreshold
        if (!recordable) {
            peakAccel = 0f
            peakGyro = 0f
            return null
        }

        // Event Merge Window: suppress weaker peaks inside merge window; keep stronger as new event.
        if (lastEmittedNs > 0L &&
            timestampNs - lastEmittedNs < config.eventMergeWindowNs &&
            peakAccel <= lastEmittedPeakAccel
        ) {
            println(
                "LOG[ShockDetector][MERGE] suppressed peakG=%.3f within mergeWindowMs=%.0f"
                    .format(peakG, config.eventMergeWindowNs / 1_000_000f),
            )
            peakAccel = 0f
            peakGyro = 0f
            return null
        }

        println(
            "LOG[ShockDetector][PEAK] peakMs2=%.3f peakG=%.3f conf=%.2f durationMs=%.1f " +
                "record=%.2fg warn=%b crit=%b"
                    .format(
                        peakAccel,
                        peakG,
                        confidence,
                        duration / 1_000_000f,
                        ShockUnits.recordingThresholdG(),
                        ShockUnits.isWarningG(peakG),
                        ShockUnits.isCriticalG(peakG),
                    ),
        )
        lastEmittedNs = timestampNs
        lastEmittedPeakAccel = peakAccel
        val event = IMUEvent(
            timestampNs = timestampNs,
            peakAcceleration = peakAccel,
            peakGyroscope = peakGyro,
            durationNs = duration,
            confidence = if (ShockUnits.isRecordableG(peakG)) {
                confidence.coerceAtLeast(config.confidenceThreshold)
            } else {
                confidence
            },
        )
        peakAccel = 0f
        peakGyro = 0f
        return event
    }

    private fun computeConfidence(
        peakAcceleration: Float,
        peakGyroscope: Float,
        durationNs: Long,
    ): Float {
        val accelScore = (peakAcceleration / ShockUnits.recordingThresholdMs2().coerceAtLeast(0.01f))
            .coerceIn(0f, ACCEL_SCORE_CAP) / ACCEL_SCORE_CAP
        val gyroScore = (peakGyroscope / config.shockGyroscopeThreshold)
            .coerceIn(0f, GYRO_SCORE_CAP) / GYRO_SCORE_CAP
        val durationScore = (durationNs.toFloat() / config.peakDurationNs.toFloat())
            .coerceIn(0f, DURATION_SCORE_CAP) / DURATION_SCORE_CAP
        return (
            accelScore * WEIGHT_ACCEL +
                gyroScore * WEIGHT_GYRO +
                durationScore * WEIGHT_DURATION
            ).coerceIn(0f, 1f)
    }

    companion object {
        private const val GYRO_SOFT_FACTOR = 0.5f
        private const val ACCEL_SCORE_CAP = 2.5f
        private const val GYRO_SCORE_CAP = 2.5f
        private const val DURATION_SCORE_CAP = 3f
        private const val WEIGHT_ACCEL = 0.55f
        private const val WEIGHT_GYRO = 0.25f
        private const val WEIGHT_DURATION = 0.20f
    }
}
