package com.example.cnv.imu

/**
 * Internal shock candidate produced by [ShockDetector] before bus publish.
 */
data class IMUEvent(
    val timestampNs: Long,
    val peakAcceleration: Float,
    val peakGyroscope: Float,
    val durationNs: Long,
    val confidence: Float,
)
