package com.example.cnv.config

import com.example.cnv.core.config.CalibrationConfig

/**
 * Persisted calibration scale for pixel → mm conversion.
 */
data class CalibrationData(
    val mmPerPixel: Float,
    val totalObservedPixel: Float,
    val calibratedDistanceMm: Float,
    val calibratedAt: Long,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        val CURRENT_VERSION = CalibrationConfig.DEFAULT_SCHEMA_VERSION
    }
}
