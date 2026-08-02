package com.example.cnv.heatmap

/**
 * Heat intensity bands for Drawing Heat Points (STEP 14).
 * Thresholds live in [HeatMapIntensityConfig] — no magic numbers at call sites.
 */
enum class HeatIntensity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * Config for mapping shock strength → [HeatIntensity].
 */
data class HeatMapIntensityConfig(
    val mediumThreshold: Float = DEFAULT_MEDIUM,
    val highThreshold: Float = DEFAULT_HIGH,
    val criticalThreshold: Float = DEFAULT_CRITICAL,
    /** Base heat applied when event has no shock. */
    val baseNoShockStrength: Float = DEFAULT_BASE_NO_SHOCK,
) {
    fun intensityFor(shockStrength: Float, hasShock: Boolean): HeatIntensity {
        val strength = if (hasShock) shockStrength else baseNoShockStrength
        return when {
            strength >= criticalThreshold -> HeatIntensity.CRITICAL
            strength >= highThreshold -> HeatIntensity.HIGH
            strength >= mediumThreshold -> HeatIntensity.MEDIUM
            else -> HeatIntensity.LOW
        }
    }

    fun normalizedIntensity(shockStrength: Float, hasShock: Boolean): Float {
        val strength = if (hasShock) shockStrength else baseNoShockStrength
        return when (intensityFor(shockStrength, hasShock)) {
            HeatIntensity.LOW -> NORMALIZED_LOW
            HeatIntensity.MEDIUM -> NORMALIZED_MEDIUM
            HeatIntensity.HIGH -> NORMALIZED_HIGH
            HeatIntensity.CRITICAL -> NORMALIZED_CRITICAL
        }.coerceAtLeast(strength.coerceIn(0f, 1f))
    }

    companion object {
        val DEFAULT = HeatMapIntensityConfig()
        const val DEFAULT_MEDIUM = 0.25f
        const val DEFAULT_HIGH = 0.50f
        const val DEFAULT_CRITICAL = 0.75f
        const val DEFAULT_BASE_NO_SHOCK = 0.05f
        const val NORMALIZED_LOW = 0.15f
        const val NORMALIZED_MEDIUM = 0.40f
        const val NORMALIZED_HIGH = 0.70f
        const val NORMALIZED_CRITICAL = 1.0f
    }
}
