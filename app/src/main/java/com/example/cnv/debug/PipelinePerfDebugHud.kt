package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.core.debug.PipelinePerfMonitor
import com.example.cnv.fusion.FusionConfig

/**
 * Frame budget + Distance→Fusion→Position latency for debug HUD.
 */
class PipelinePerfDebugHud(
    private val textView: TextView,
    private val refreshIntervalMs: Long = FusionConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(PipelinePerfMonitor.snapshot())
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

    private fun render(snapshot: PipelinePerfMonitor.Snapshot) {
        textView.text = buildString {
            append("Perf Debug\n")
            append("Frame: %.1f ms\n".format(snapshot.frameMs))
            append("FPS: %.1f\n".format(snapshot.fps))
            append("Dropped: %d\n".format(snapshot.droppedFrames))
            append("Dist→Fusion: %.1f ms\n".format(snapshot.distanceToFusionMs))
            append("Fusion→Pos: %.1f ms\n".format(snapshot.fusionToPositionMs))
            append("Dist→Pos: %.1f ms".format(snapshot.distanceToPositionMs))
        }
    }
}
