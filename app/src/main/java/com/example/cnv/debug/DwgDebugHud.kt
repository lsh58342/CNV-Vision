package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.dwg.DWGConfig
import com.example.cnv.dwg.DWGImporter

/**
 * Displays latest DWG import / route-candidate extraction stats.
 */
class DwgDebugHud(
    private val textView: TextView,
    private val importer: DWGImporter,
    private val refreshIntervalMs: Long = DWGConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(importer.latestResult())
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

    private fun render(result: DWGImporter.ImportResult?) {
        if (result == null) {
            textView.text = "DWG Debug\n(no import yet)"
            return
        }
        textView.text = buildString {
            append("DWG Debug\n")
            append("File: %s\n".format(result.fileName))
            append("Layers: %s\n".format(result.layerNames.joinToString(",")))
            append("Selected: %s\n".format(result.selectedLayer))
            append("Polylines: %d\n".format(result.polylineCount))
            append("Merged: %d\n".format(result.mergedPolylineCount))
            append("CenterLines: %d\n".format(result.centerLineCount))
            append("RouteCandidates: %d".format(result.routeCandidateCount))
        }
    }
}
