package com.example.cnv.imu

import android.content.Context

/**
 * App-wide adjustable Record / Critical shock thresholds (g).
 * Warning / High bands are derived between the two.
 */
object ShockThresholdStore {

    private const val PREFS = "cnv_shock_thresholds"
    private const val KEY_RECORDING_G = "recording_g"
    private const val KEY_CRITICAL_G = "critical_g"

    @Volatile
    private var recordingG: Float = ShockUnits.DEFAULT_RECORDING_THRESHOLD_G

    @Volatile
    private var criticalG: Float = ShockUnits.DEFAULT_CRITICAL_THRESHOLD_G

    @Volatile
    private var loaded: Boolean = false

    fun recordingThresholdG(): Float = recordingG

    fun criticalThresholdG(): Float = criticalG

    /** ~20% of span above Record (default layout: 1.03 → 1.06). */
    fun warningThresholdG(): Float {
        val span = (criticalG - recordingG).coerceAtLeast(0.01f)
        return recordingG + span * WARNING_FRACTION
    }

    /** ~60% of span above Record (default layout: 1.03 → 1.13). */
    fun highThresholdG(): Float {
        val span = (criticalG - recordingG).coerceAtLeast(0.01f)
        return recordingG + span * HIGH_FRACTION
    }

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rec = prefs.getFloat(KEY_RECORDING_G, ShockUnits.DEFAULT_RECORDING_THRESHOLD_G)
        val crit = prefs.getFloat(KEY_CRITICAL_G, ShockUnits.DEFAULT_CRITICAL_THRESHOLD_G)
        applyInMemory(rec, crit)
        loaded = true
        println(
            "LOG[ShockThreshold] loaded record=%.3fg critical=%.3fg warn=%.3fg high=%.3fg"
                .format(recordingG, criticalG, warningThresholdG(), highThresholdG()),
        )
    }

    fun ensureLoaded(context: Context) {
        if (!loaded) load(context)
    }

    /**
     * @return false if values invalid (must have critical > recording, both in range).
     */
    fun save(context: Context, recordingG: Float, criticalG: Float): Boolean {
        val normalized = normalize(recordingG, criticalG) ?: return false
        applyInMemory(normalized.first, normalized.second)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_RECORDING_G, this.recordingG)
            .putFloat(KEY_CRITICAL_G, this.criticalG)
            .apply()
        println(
            "LOG[ShockThreshold] saved record=%.3fg critical=%.3fg"
                .format(this.recordingG, this.criticalG),
        )
        return true
    }

    fun resetToDefaults(context: Context): Boolean =
        save(
            context,
            ShockUnits.DEFAULT_RECORDING_THRESHOLD_G,
            ShockUnits.DEFAULT_CRITICAL_THRESHOLD_G,
        )

    private fun applyInMemory(recording: Float, critical: Float) {
        recordingG = recording
        criticalG = critical
    }

    private fun normalize(recording: Float, critical: Float): Pair<Float, Float>? {
        if (!recording.isFinite() || !critical.isFinite()) return null
        if (recording < MIN_G || critical > MAX_G) return null
        if (critical <= recording + MIN_SPAN_G) return null
        return recording to critical
    }

    private const val WARNING_FRACTION = 0.176f // (1.06-1.03)/(1.20-1.03)
    private const val HIGH_FRACTION = 0.588f // (1.13-1.03)/(1.20-1.03)
    private const val MIN_G = 0.5f
    private const val MAX_G = 5.0f
    private const val MIN_SPAN_G = 0.02f
}
