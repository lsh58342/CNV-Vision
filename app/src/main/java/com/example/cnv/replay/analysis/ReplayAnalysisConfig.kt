package com.example.cnv.replay.analysis

/**
 * Analysis thresholds / defaults (STEP 16-1). Independent of ReplayEngine config.
 */
data class ReplayAnalysisConfig(
    val lowConfidenceThreshold: Float = DEFAULT_LOW_CONFIDENCE_THRESHOLD,
) {
    companion object {
        const val DEFAULT_LOW_CONFIDENCE_THRESHOLD = 0.5f
        val DEFAULT = ReplayAnalysisConfig()
    }
}
