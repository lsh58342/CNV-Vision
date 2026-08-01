package com.example.cnv.imu

/**
 * Sensor accuracy wrapper for repository / debug UI.
 */
enum class SensorAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
    ;

    companion object {
        fun fromAndroid(accuracy: Int): SensorAccuracy = when (accuracy) {
            android.hardware.SensorManager.SENSOR_STATUS_UNRELIABLE -> UNRELIABLE
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> LOW
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MEDIUM
            android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> HIGH
            else -> UNKNOWN
        }
    }
}
