package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.inspection.InspectionConfig
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionState

/**
 * Displays active inspection session freeze + live statistics.
 */
class InspectionDebugHud(
    private val textView: TextView,
    private val inspectionManager: InspectionManager,
    private val refreshIntervalMs: Long = InspectionConfig.DEFAULT_DEBUG_HUD_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render()
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

    private fun render() {
        val state = inspectionManager.state()
        val session = inspectionManager.currentSession()
        if (session == null || state == InspectionState.IDLE) {
            val latest = inspectionManager.repository().latest()
            textView.text = if (latest == null) {
                "Inspection\nState: IDLE"
            } else {
                buildString {
                    append("Inspection\nState: IDLE (last saved)\n")
                    append("Route Ver: %s\n".format(latest.routeVersion))
                    append("Cal Ver: %d\n".format(latest.calibrationVersion))
                    append("Distance: %.1f mm\n".format(latest.statistics.totalDistanceMm))
                    append("Shocks: %d\n".format(latest.statistics.shockCount))
                    append("Avg Conf: %.2f\n".format(latest.statistics.averageConfidence))
                    append("Quality: %.2f".format(latest.routeQualityScore))
                }
            }
            return
        }
        val freeze = session.freeze
        val live = session.recorder().computeStatistics(
            freeze = freeze,
            startTimeMs = session.startTimeMs,
            endTimeMs = System.currentTimeMillis(),
        )
        val elapsedSec = session.elapsedMs() / 1000.0
        textView.text = buildString {
            append("Inspection\n")
            append("State: %s\n".format(state.name))
            append("Time: %.1f s\n".format(elapsedSec))
            append("Route Ver: %s\n".format(freeze.routeVersion))
            append("Cal Ver: %d\n".format(freeze.calibrationVersion))
            append("Distance: %.1f mm\n".format(live.totalDistanceMm))
            append("Shocks: %d\n".format(live.shockCount))
            append("Avg Conf: %.2f\n".format(live.averageConfidence))
            append("Quality: %.2f".format(freeze.routeQualityScore))
        }
    }
}
