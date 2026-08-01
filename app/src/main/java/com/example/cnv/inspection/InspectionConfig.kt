package com.example.cnv.inspection

/**
 * Inspection session defaults. All magic numbers live here.
 */
data class InspectionConfig(
    val autoSave: Boolean = DEFAULT_AUTO_SAVE,
    val sessionTimeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
    val maximumSessionLengthMs: Long = DEFAULT_MAXIMUM_SESSION_LENGTH_MS,
    val cacheLimit: Int = DEFAULT_CACHE_LIMIT,
    val snapshotPolicy: SnapshotPolicy = SnapshotPolicy.FREEZE_ON_START,
    val debugHudRefreshIntervalMs: Long = DEFAULT_DEBUG_HUD_REFRESH_MS,
    val defaultSamplingRateHz: Float = DEFAULT_SAMPLING_RATE_HZ,
) {
    enum class SnapshotPolicy {
        FREEZE_ON_START,
    }

    companion object {
        const val DEFAULT_AUTO_SAVE = true
        const val DEFAULT_SESSION_TIMEOUT_MS = 3_600_000L
        const val DEFAULT_MAXIMUM_SESSION_LENGTH_MS = 14_400_000L
        const val DEFAULT_CACHE_LIMIT = 8
        const val DEFAULT_DEBUG_HUD_REFRESH_MS = 300L
        const val DEFAULT_SAMPLING_RATE_HZ = 100f

        val DEFAULT: InspectionConfig = InspectionConfig()
    }
}
