package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.fusion.FusionConfig
import com.example.cnv.production.ProductionMetrics

/**
 * Production performance metrics HUD (STEP 20).
 * FPS / Frame Time / Drop / Analyzer / Memory / Queue / Replay cache.
 */
class PipelinePerfDebugHud(
    private val textView: TextView,
    private val refreshIntervalMs: Long = FusionConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(ProductionMetrics.snapshot())
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

    private fun render(s: ProductionMetrics.Snapshot) {
        textView.text = buildString {
            append("Perf Debug\n")
            append("FPS: %.1f\n".format(s.fps))
            append("Frame: %.1f ms\n".format(s.frameTimeMs))
            append("Drop: %d\n".format(s.frameDrop))
            append("Analyzer: %.1f ms\n".format(s.analyzerTimeMs))
            append("Mem: %.1f MB\n".format(s.memoryUsageMb))
            append("Queue: %d\n".format(s.eventQueueSize))
            append("ReplayCache: %d\n".format(s.replayCacheSize))
            append("Sensor: %.0f Hz drop=%d\n".format(s.sensorHz, s.sensorDropped))
            append("HeatCells: %d\n".format(s.heatOverlayCells))
            append(
                "Stall C/S/R/P: %.0f/%.0f/%.0f/%.0f".format(
                    finiteOrZero(s.cameraStallMs),
                    finiteOrZero(s.sensorStallMs),
                    finiteOrZero(s.replayStallMs),
                    finiteOrZero(s.processStallMs),
                ),
            )
        }
    }

    private fun finiteOrZero(v: Double): Double =
        if (v.isFinite()) v else 0.0
}
