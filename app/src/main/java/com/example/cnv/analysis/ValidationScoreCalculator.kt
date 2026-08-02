package com.example.cnv.analysis

import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.speed.SpeedValidationConfig
import com.example.cnv.speed.SpeedValidator

/**
 * Single source for Validation Score used by Analysis Result (STEP 17).
 * Averages per-delta Speed Validation confidence (same model as SpeedValidator).
 */
class ValidationScoreCalculator(
    private val validator: SpeedValidator = SpeedValidator(SpeedValidationConfig.DEFAULT),
) {

    fun compute(
        events: List<PersistedInspectionEvent>,
        profile: ConveyorProfileSnapshot,
        fallbackScore: Float = 0f,
    ): Float {
        val conveyor = ConveyorProfileSnapshot.toProfile(profile)
        if (conveyor.nominalSpeedMPerMin == null || conveyor.nominalSpeedMPerMin <= 0f) {
            return fallbackScore.coerceIn(0f, 1f)
        }
        if (events.size < 2) return fallbackScore.coerceIn(0f, 1f)
        var sum = 0.0
        var count = 0
        for (i in 1 until events.size) {
            val prev = events[i - 1]
            val cur = events[i]
            val measuredDelta = (cur.distanceMm - prev.distanceMm).let { d ->
                if (d >= 0f) d else cur.distanceMm.coerceAtLeast(0f)
            }
            val sample = validator.validate(
                measuredDistanceMm = measuredDelta,
                profile = conveyor,
                timestampNs = cur.timestampNs,
                fusionConfidence = cur.trackingConfidence.takeIf { it > 0f },
            ) ?: continue
            sum += sample.confidence
            count += 1
        }
        if (count == 0) return fallbackScore.coerceIn(0f, 1f)
        return (sum / count).toFloat().coerceIn(0f, 1f)
    }

    fun speedDifferenceMmPerSec(
        averageMeasuredMmPerSec: Float,
        nominalMPerMin: Float?,
    ): Float {
        if (nominalMPerMin == null || nominalMPerMin <= 0f) return 0f
        val nominalMmPerSec = nominalMPerMin * SpeedValidationConfig.M_PER_MIN_TO_MM_PER_SEC
        return kotlin.math.abs(averageMeasuredMmPerSec - nominalMmPerSec)
    }
}
