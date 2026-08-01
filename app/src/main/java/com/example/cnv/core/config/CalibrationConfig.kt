package com.example.cnv.core.config

/**
 * Calibration session / persistence defaults.
 */
data class CalibrationConfig(
    val schemaVersion: Int = DEFAULT_SCHEMA_VERSION,
    val sessionUiRefreshIntervalMs: Long = DEFAULT_SESSION_UI_REFRESH_MS,
) {
    companion object {
        const val DEFAULT_SCHEMA_VERSION = 1
        const val DEFAULT_SESSION_UI_REFRESH_MS = 500L

        val DEFAULT: CalibrationConfig = CalibrationConfig()
    }
}
