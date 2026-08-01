package com.example.cnv.config

/**
 * Persisted calibration scale for pixel → mm conversion.
 */
data class CalibrationData(
    val mmPerPixel: Float,
    val calibratedAt: Long,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
