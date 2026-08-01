package com.example.cnv.imu

/**
 * Latest processed IMU sample snapshot.
 */
data class IMUData(
    val timestampNs: Long = 0L,
    val accelerometerX: Float = 0f,
    val accelerometerY: Float = 0f,
    val accelerometerZ: Float = 0f,
    val gyroscopeX: Float = 0f,
    val gyroscopeY: Float = 0f,
    val gyroscopeZ: Float = 0f,
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val gravityZ: Float = 0f,
    val linearAccelerationX: Float = 0f,
    val linearAccelerationY: Float = 0f,
    val linearAccelerationZ: Float = 0f,
    val confidence: Float = 0f,
    val shockLevel: Float = 0f,
    val samplingRateHz: Float = 0f,
    val accelerometerAccuracy: SensorAccuracy = SensorAccuracy.UNKNOWN,
    val gyroscopeAccuracy: SensorAccuracy = SensorAccuracy.UNKNOWN,
)
