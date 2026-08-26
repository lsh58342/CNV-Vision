package com.example.cnv.heatmap

import com.example.cnv.imu.ShockUnits

/**
 * Heat intensity bands for Drawing Heat Points — shock strength in **g**.
 *
 * 1.03–1.06 Green (Record / LOW)
 * 1.06–1.13 Yellow (Warning / MEDIUM)
 * 1.13–1.20 Orange (HIGH)
 * ≥1.20 Red (Critical)
 */
enum class HeatIntensity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class HeatMapIntensityConfig(
    val mediumThreshold: Float = ShockUnits.DEFAULT_WARNING_THRESHOLD_G,
    val highThreshold: Float = ShockUnits.DEFAULT_HIGH_THRESHOLD_G,
    val criticalThreshold: Float = ShockUnits.DEFAULT_CRITICAL_THRESHOLD_G,
    val recordThreshold: Float = ShockUnits.DEFAULT_RECORDING_THRESHOLD_G,
    val baseNoShockStrength: Float = DEFAULT_BASE_NO_SHOCK,
) {
    fun intensityFor(shockStrengthG: Float, hasShock: Boolean): HeatIntensity {
        if (!hasShock || shockStrengthG < recordThreshold) {
            return HeatIntensity.LOW
        }
        return when {
            shockStrengthG >= criticalThreshold -> HeatIntensity.CRITICAL
            shockStrengthG >= highThreshold -> HeatIntensity.HIGH
            shockStrengthG >= mediumThreshold -> HeatIntensity.MEDIUM
            else -> HeatIntensity.LOW
        }
    }

    fun colorForShockG(shockG: Float): Int = when {
        shockG >= criticalThreshold -> COLOR_RED
        shockG >= highThreshold -> COLOR_ORANGE
        shockG >= mediumThreshold -> COLOR_YELLOW
        shockG >= recordThreshold -> COLOR_GREEN
        else -> COLOR_MUTED
    }

    fun normalizedIntensity(shockStrength: Float, hasShock: Boolean): Float {
        return when (intensityFor(shockStrength, hasShock)) {
            HeatIntensity.LOW -> NORMALIZED_LOW
            HeatIntensity.MEDIUM -> NORMALIZED_MEDIUM
            HeatIntensity.HIGH -> NORMALIZED_HIGH
            HeatIntensity.CRITICAL -> NORMALIZED_CRITICAL
        }
    }

    companion object {
        /** Static factory defaults (compile-time). Prefer [active] at runtime. */
        val DEFAULT = HeatMapIntensityConfig()

        /** Live thresholds from Settings ([ShockThresholdStore]). */
        fun active(): HeatMapIntensityConfig = HeatMapIntensityConfig(
            recordThreshold = ShockUnits.recordingThresholdG(),
            mediumThreshold = ShockUnits.warningThresholdG(),
            highThreshold = ShockUnits.highThresholdG(),
            criticalThreshold = ShockUnits.criticalThresholdG(),
        )

        const val DEFAULT_RECORD = ShockUnits.DEFAULT_RECORDING_THRESHOLD_G
        const val DEFAULT_MEDIUM = ShockUnits.DEFAULT_WARNING_THRESHOLD_G
        const val DEFAULT_HIGH = ShockUnits.DEFAULT_HIGH_THRESHOLD_G
        const val DEFAULT_CRITICAL = ShockUnits.DEFAULT_CRITICAL_THRESHOLD_G
        const val DEFAULT_BASE_NO_SHOCK = 0f
        const val NORMALIZED_LOW = 0.25f
        const val NORMALIZED_MEDIUM = 0.50f
        const val NORMALIZED_HIGH = 0.75f
        const val NORMALIZED_CRITICAL = 1.0f

        const val COLOR_GREEN = 0xFF4CAF50.toInt()
        const val COLOR_YELLOW = 0xFFFFEB3B.toInt()
        const val COLOR_ORANGE = 0xFFFF9800.toInt()
        const val COLOR_RED = 0xFFF44336.toInt()
        const val COLOR_MUTED = 0x6642A5F5
    }
}
