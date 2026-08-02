package com.example.cnv.speed

import com.example.cnv.factory.model.ConveyorProfile
import kotlin.math.abs

/**
 * Computes Expected Distance vs Measured Distance for validation only (STEP 15-2).
 * Never overwrites Distance / Route / Fusion distance fields.
 *
 * ExpectedDistance(mm) = NominalSpeed(m/min) × FrameTime(s) × (1000/60)
 */
class SpeedValidator(
    private val config: SpeedValidationConfig = SpeedValidationConfig.DEFAULT,
) {

    fun validate(
        measuredDistanceMm: Float,
        profile: ConveyorProfile,
        timestampNs: Long,
        fusionConfidence: Float? = null,
    ): SpeedValidationSample? {
        val nominal = profile.nominalSpeedMPerMin ?: return null
        if (nominal <= 0f) return null
        val fps = profile.expectedFps.coerceAtLeast(1f)
        val frameTimeSec = 1f / fps
        val expectedDistanceMm = nominal *
            SpeedValidationConfig.M_PER_MIN_TO_MM_PER_SEC *
            frameTimeSec
        val differenceMm = abs(expectedDistanceMm - measuredDistanceMm)
        val denom = expectedDistanceMm.coerceAtLeast(config.minExpectedDistanceMm)
        val relativeError = differenceMm / denom
        val toleranceFraction = (profile.speedTolerancePercent / 100f).coerceAtLeast(0f)
        val withinTolerance = relativeError <= toleranceFraction
        val outlier = relativeError >= toleranceFraction * config.outlierToleranceMultiplier &&
            toleranceFraction > 0f

        val confidence = computeConfidence(
            relativeError = relativeError,
            toleranceFraction = toleranceFraction,
            withinTolerance = withinTolerance,
            outlier = outlier,
        )
        val measuredSpeed = if (frameTimeSec > 0f) {
            (measuredDistanceMm / frameTimeSec) / SpeedValidationConfig.M_PER_MIN_TO_MM_PER_SEC
        } else {
            0f
        }
        val validatedFusion = fusionConfidence?.let { base ->
            (base * confidence).coerceIn(config.minConfidence, config.maxConfidence)
        }

        return SpeedValidationSample(
            timestampNs = timestampNs,
            nominalSpeedMPerMin = nominal,
            expectedDistanceMm = expectedDistanceMm,
            measuredDistanceMm = measuredDistanceMm,
            differenceMm = differenceMm,
            relativeError = relativeError,
            toleranceFraction = toleranceFraction,
            confidence = confidence,
            outlier = outlier,
            withinTolerance = withinTolerance,
            expectedSpeedMPerMin = nominal,
            measuredSpeedMPerMin = measuredSpeed,
            frameTimeSec = frameTimeSec,
            validatedFusionConfidence = validatedFusion,
        )
    }

    /**
     * Difference small → confidence up; large → down; very large → outlier floor.
     */
    private fun computeConfidence(
        relativeError: Float,
        toleranceFraction: Float,
        withinTolerance: Boolean,
        outlier: Boolean,
    ): Float {
        if (outlier) {
            return config.minConfidence
        }
        if (withinTolerance) {
            val headroom = 1f - (relativeError / toleranceFraction.coerceAtLeast(config.minExpectedDistanceMm))
                .coerceIn(0f, 1f)
            return (config.baseConfidence + config.withinToleranceBoost * headroom)
                .coerceIn(config.minConfidence, config.maxConfidence)
        }
        val over = if (toleranceFraction > 0f) {
            ((relativeError - toleranceFraction) / toleranceFraction).coerceAtLeast(0f)
        } else {
            relativeError
        }
        val penalty = config.overTolerancePenalty * (1f + over).coerceAtMost(4f)
        return (config.baseConfidence - penalty)
            .coerceIn(config.minConfidence, config.maxConfidence)
    }
}
