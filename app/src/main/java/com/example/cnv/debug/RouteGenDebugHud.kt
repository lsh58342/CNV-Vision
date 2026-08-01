package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.route.CoordinateConfig
import com.example.cnv.route.RouteGenerator
import com.example.cnv.route.RouteImportResult

/**
 * Displays generated route + coordinate mapping debug fields.
 */
class RouteGenDebugHud(
    private val textView: TextView,
    private val routeGenerator: RouteGenerator,
    private val refreshIntervalMs: Long = CoordinateConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(routeGenerator.latestResult())
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

    private fun render(result: RouteImportResult?) {
        if (result == null) {
            textView.text = "Route Gen Debug\n(no route generated)"
            return
        }
        textView.text = buildString {
            append("Route Gen Debug\n")
            append("Name: %s\n".format(result.routeName))
            append("Nodes: %d\n".format(result.nodeCount))
            append("Segments: %d\n".format(result.segmentCount))
            append("Branches: %d\n".format(result.branchCount))
            append("Total Length: %.1f mm\n".format(result.totalRouteLengthMm))
            append("Coord Scale: %.3f\n".format(result.coordinateScale))
            append(
                "Coord Offset: %.1f, %.1f".format(
                    result.coordinateOffsetX,
                    result.coordinateOffsetY,
                ),
            )
        }
    }
}
