package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.fusion.FusionConfig
import com.example.cnv.fusion.FusionRepository
import com.example.cnv.fusion.FusionResult

/**
 * Polls [FusionRepository] for fusion debug fields. Main-thread only.
 */
class FusionDebugHud(
    private val textView: TextView,
    private val repository: FusionRepository,
    private val refreshIntervalMs: Long = FusionConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(repository.latest())
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    fun start() {
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    fun stop() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun render(result: FusionResult?) {
        if (result == null) {
            textView.text = "Fusion Debug\n(waiting for Distance+Shock match)"
            return
        }
        val delayMs = result.timestampDelayNs / 1_000_000.0
        textView.text = buildString {
            append("Fusion Debug\n")
            append("Distance Conf: %.2f\n".format(result.distanceConfidence))
            append("Shock Conf: %.2f\n".format(result.shockConfidence))
            append("Fusion Conf: %.2f\n".format(result.confidence))
            append("Tracking Count: %d\n".format(result.trackingCount))
            append("Peak Accel: %.2f\n".format(result.peakAcceleration))
            append("Timestamp Delay: %.1f ms\n".format(delayMs))
            append("Calibration: %s\n".format(if (result.calibrated) "OK" else "NO"))
            append("Type: %s".format(result.eventType.name))
        }
    }
}
