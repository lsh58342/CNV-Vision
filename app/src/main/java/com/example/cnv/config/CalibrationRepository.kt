package com.example.cnv.config

import android.content.Context

/**
 * Persistence for [CalibrationData]. SharedPreferences only (no network).
 */
class CalibrationRepository(
    context: Context,
) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun saveCalibration(data: CalibrationData) {
        preferences.edit()
            .putFloat(KEY_MM_PER_PIXEL, data.mmPerPixel)
            .putFloat(KEY_TOTAL_OBSERVED_PIXEL, data.totalObservedPixel)
            .putFloat(KEY_CALIBRATED_DISTANCE_MM, data.calibratedDistanceMm)
            .putLong(KEY_CALIBRATED_AT, data.calibratedAt)
            .putInt(KEY_VERSION, data.version)
            .putBoolean(KEY_HAS_CALIBRATION, true)
            .apply()
    }

    fun loadCalibration(): CalibrationData? {
        if (!preferences.getBoolean(KEY_HAS_CALIBRATION, false)) {
            return null
        }
        val mmPerPixel = preferences.getFloat(KEY_MM_PER_PIXEL, 0f)
        if (mmPerPixel <= 0f) {
            return null
        }
        return CalibrationData(
            mmPerPixel = mmPerPixel,
            totalObservedPixel = preferences.getFloat(KEY_TOTAL_OBSERVED_PIXEL, 0f),
            calibratedDistanceMm = preferences.getFloat(KEY_CALIBRATED_DISTANCE_MM, 0f),
            calibratedAt = preferences.getLong(KEY_CALIBRATED_AT, 0L),
            version = preferences.getInt(KEY_VERSION, CalibrationData.CURRENT_VERSION),
        )
    }

    fun resetCalibration() {
        preferences.edit()
            .remove(KEY_MM_PER_PIXEL)
            .remove(KEY_TOTAL_OBSERVED_PIXEL)
            .remove(KEY_CALIBRATED_DISTANCE_MM)
            .remove(KEY_CALIBRATED_AT)
            .remove(KEY_VERSION)
            .putBoolean(KEY_HAS_CALIBRATION, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "cnv_calibration"
        private const val KEY_HAS_CALIBRATION = "has_calibration"
        private const val KEY_MM_PER_PIXEL = "mm_per_pixel"
        private const val KEY_TOTAL_OBSERVED_PIXEL = "total_observed_pixel"
        private const val KEY_CALIBRATED_DISTANCE_MM = "calibrated_distance_mm"
        private const val KEY_CALIBRATED_AT = "calibrated_at"
        private const val KEY_VERSION = "version"
    }
}
