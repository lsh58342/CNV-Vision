package com.example.cnv.ui.screen.inspection

/**
 * Display-only shock timeline for [ShockGraphView] (live + history).
 */
data class ShockGraphState(
    val samples: List<Float> = emptyList(),
    val peakIndices: List<Int> = emptyList(),
    val current: Float = 0f,
    val average: Float = 0f,
    val maximum: Float = 0f,
    val threshold: Float = 0.55f,
) {
    companion object {
        fun fromSamples(
            samples: List<Float>,
            threshold: Float,
            current: Float = samples.lastOrNull() ?: 0f,
            average: Float = if (samples.isEmpty()) 0f else samples.average().toFloat(),
            maximum: Float = samples.maxOrNull() ?: 0f,
        ): ShockGraphState {
            val peaks = ArrayList<Int>()
            for (i in samples.indices) {
                val v = samples[i]
                if (v < threshold) continue
                val prev = samples.getOrElse(i - 1) { Float.NEGATIVE_INFINITY }
                val next = samples.getOrElse(i + 1) { Float.NEGATIVE_INFINITY }
                if (v >= prev && v >= next) {
                    peaks.add(i)
                }
            }
            return ShockGraphState(
                samples = samples,
                peakIndices = peaks,
                current = current,
                average = average,
                maximum = maximum,
                threshold = threshold,
            )
        }
    }
}
