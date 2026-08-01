package com.example.cnv.fusion

import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.FusionEventType
import com.example.cnv.core.event.ShockEvent
import kotlin.math.abs
import kotlin.math.min

/**
 * Rule-based fusion only (no AI / ML).
 * Owns timestamp matching and confidence scoring.
 */
class FusionRuleEngine(
    private val config: FusionConfig = FusionConfig.DEFAULT,
) {

    data class MatchInput(
        val distance: DistanceEvent?,
        val shock: ShockEvent?,
        val calibrated: Boolean,
    )

    /**
     * @return null when the candidate fails minimum gates.
     */
    fun evaluate(input: MatchInput): FusionResult? {
        val distance = input.distance
        val shock = input.shock

        val eventType = when {
            distance != null && shock != null -> FusionEventType.FUSED
            distance != null -> FusionEventType.DISTANCE_ONLY
            shock != null -> FusionEventType.SHOCK_ONLY
            else -> return null
        }

        if (distance != null && distance.trackingFeatureCount < config.minimumTrackingCount) {
            return null
        }

        val fusionConfidence = computeConfidence(distance, shock, input.calibrated)
        if (fusionConfidence.overall < config.minimumConfidence) {
            return null
        }

        val timestampNs = when (eventType) {
            FusionEventType.FUSED -> min(distance!!.timestampNs, shock!!.timestampNs)
            FusionEventType.DISTANCE_ONLY -> distance!!.timestampNs
            FusionEventType.SHOCK_ONLY -> shock!!.timestampNs
        }

        val delayNs = if (distance != null && shock != null) {
            abs(distance.timestampNs - shock.timestampNs)
        } else {
            0L
        }

        if (eventType == FusionEventType.FUSED && delayNs > config.timeWindowNs) {
            return null
        }

        return FusionResult(
            timestampNs = timestampNs,
            distance = distance?.distanceMm ?: 0f,
            confidence = fusionConfidence.overall,
            shockLevel = shock?.peakAcceleration ?: 0f,
            trackingCount = distance?.trackingFeatureCount ?: 0,
            peakAcceleration = shock?.peakAcceleration ?: 0f,
            eventType = eventType,
            timestampDelayNs = delayNs,
            distanceConfidence = fusionConfidence.distanceConfidence,
            shockConfidence = fusionConfidence.shockConfidence,
            calibrated = input.calibrated,
        )
    }

    /**
     * True when |Δt| is within [FusionConfig.timeWindowNs].
     */
    fun isWithinTimeWindow(aNs: Long, bNs: Long): Boolean {
        return abs(aNs - bNs) <= config.timeWindowNs
    }

    fun computeConfidence(
        distance: DistanceEvent?,
        shock: ShockEvent?,
        calibrated: Boolean,
    ): FusionConfidence {
        val distanceConfidence = (distance?.confidence ?: 0f).coerceIn(0f, 1f)
        val shockConfidence = (shock?.confidence ?: 0f).coerceIn(0f, 1f)
        // DistanceEvent exposes a single confidence that already includes RANSAC quality.
        val ransacConfidence = distanceConfidence
        val trackingScore = if (distance != null) {
            (distance.trackingFeatureCount.toFloat() / config.trackingCountNorm).coerceIn(0f, 1f)
        } else {
            0f
        }
        val peakAccelerationScore = if (shock != null) {
            (shock.peakAcceleration / config.peakAccelerationNorm).coerceIn(0f, 1f)
        } else {
            0f
        }
        val calibrationScore = if (calibrated) 1f else 0f

        val weightSum =
            config.distanceWeight +
                config.shockWeight +
                config.calibrationWeight +
                config.trackingWeight +
                config.peakAccelerationWeight +
                config.ransacWeight

        val overall = if (weightSum <= 0f) {
            0f
        } else {
            (
                config.distanceWeight * distanceConfidence +
                    config.shockWeight * shockConfidence +
                    config.calibrationWeight * calibrationScore +
                    config.trackingWeight * trackingScore +
                    config.peakAccelerationWeight * peakAccelerationScore +
                    config.ransacWeight * ransacConfidence
                ) / weightSum
        }.coerceIn(0f, 1f)

        return FusionConfidence(
            overall = overall,
            distanceConfidence = distanceConfidence,
            shockConfidence = shockConfidence,
            trackingScore = trackingScore,
            peakAccelerationScore = peakAccelerationScore,
            calibrationScore = calibrationScore,
            ransacConfidence = ransacConfidence,
        )
    }
}
