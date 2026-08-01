package com.example.cnv.core.config

/**
 * Root app configuration aggregate.
 */
data class AppConfig(
    val camera: CameraConfig = CameraConfig.DEFAULT,
    val openCv: OpenCVConfig = OpenCVConfig.DEFAULT,
    val imu: IMUConfig = IMUConfig.DEFAULT,
    val calibration: CalibrationConfig = CalibrationConfig.DEFAULT,
    val debug: DebugConfig = DebugConfig.DEFAULT,
) {
    companion object {
        val DEFAULT: AppConfig = AppConfig()
    }
}
