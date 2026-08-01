package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.map.MapConfig
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RoutePosition

/**
 * Polls [MapMatchingEngine] latest [RoutePosition] for debug fields.
 */
class MapDebugHud(
    private val textView: TextView,
    private val mapMatchingEngine: MapMatchingEngine,
    private val refreshIntervalMs: Long = MapConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(mapMatchingEngine.latestPosition())
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

    private fun render(position: RoutePosition?) {
        if (position == null) {
            textView.text = "Map Debug\n(waiting for FusionEvent + Route)"
            return
        }
        textView.text = buildString {
            append("Map Debug\n")
            append("Segment: %s\n".format(position.segmentId))
            append("Node: %s\n".format(position.nodeId))
            append("Progress: %.1f%%\n".format(position.progress * 100f))
            append("Dist From Start: %.1f mm\n".format(position.distanceFromSegmentStart))
            append("Direction: %s\n".format(position.direction.name))
            append("Pos Conf: %.2f".format(position.confidence))
        }
    }
}
