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
    val mediumThreshold: Float = DEFAULT_MEDIUM,
    val highThreshold: Float = DEFAULT_HIGH,
    val criticalThreshold: Float = DEFAULT_CRITICAL,
    val recordThreshold: Float = DEFAULT_RECORD,
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
        val DEFAULT = HeatMapIntensityConfig()
        const val DEFAULT_RECORD = ShockUnits.RECORDING_THRESHOLD_G // 1.03g
        const val DEFAULT_MEDIUM = ShockUnits.WARNING_THRESHOLD_G // 1.06g
        const val DEFAULT_HIGH = ShockUnits.HIGH_THRESHOLD_G // 1.13g
        const val DEFAULT_CRITICAL = ShockUnits.CRITICAL_THRESHOLD_G // 1.20g
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
