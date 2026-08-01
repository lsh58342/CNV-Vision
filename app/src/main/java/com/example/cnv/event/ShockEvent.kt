package com.example.cnv.event

/**
 * Published by IMU ShockDetector when a shock peak is confirmed.
 */
data class ShockEvent(
    override val timestampNs: Long,
    val peakAcceleration: Float,
    val peakGyroscope: Float,
    val durationNs: Long,
    val confidence: Float,
) : BaseEvent
