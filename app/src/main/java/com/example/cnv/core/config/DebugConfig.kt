package com.example.cnv.core.config

/**
 * Debug HUD / logging defaults.
 */
data class DebugConfig(
    val imuHudRefreshIntervalMs: Long = DEFAULT_IMU_HUD_REFRESH_MS,
    val showImuHud: Boolean = true,
    val showOpenCvOverlay: Boolean = true,
) {
    companion object {
        const val DEFAULT_IMU_HUD_REFRESH_MS = 200L

        val DEFAULT: DebugConfig = DebugConfig()
    }
}
