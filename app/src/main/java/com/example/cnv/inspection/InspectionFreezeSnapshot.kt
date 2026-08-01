package com.example.cnv.inspection

/**
 * Frozen calibration + app context for one inspection session.
 */
data class InspectionFreezeSnapshot(
    val routeVersion: String,
    val routeHash: String,
    val calibrationVersion: Int,
    val calibrationValue: Float,
    val appVersion: String,
    val timestampMs: Long,
    val deviceInformation: String,
    val samplingRateHz: Float,
    val routeQualityScore: Float,
)
