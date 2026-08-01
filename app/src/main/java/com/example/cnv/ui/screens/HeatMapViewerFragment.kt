package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.ui.feature.FeatureRuntime
import com.example.cnv.ui.feature.requireFeatureRuntime
import com.google.android.material.button.MaterialButton

/**
 * HeatMap Viewer — CAD + Overlay + Timeline/Filter (migrated).
 */
class HeatMapViewerFragment : BaseScreenFragment() {

    private var features: FeatureRuntime? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_heatmap_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val runtime = requireActivity().requireFeatureRuntime()
        features = runtime
        runtime.attachHeatMap(view)

        val statsPanel = view.findViewById<View>(R.id.heatmap_stats_panel)
        view.findViewById<MaterialButton>(R.id.heatmap_stats_header).setOnClickListener {
            statsPanel.isVisible = !statsPanel.isVisible
            val stats = runtime.heatMapController()?.latestStatistics()
            val sessionStats = runtime.heatMapController()?.latestSessionStatistics()
            if (stats != null) {
                view.findViewById<TextView>(R.id.heatmap_stats_body).text = buildString {
                    appendLine("Shock Count / Points: ${stats.heatPointCount}")
                    appendLine("Coverage Distance: %.0f mm".format(stats.coveredDistanceMm))
                    appendLine("Cells: ${stats.heatCellCount}")
                    appendLine("Average Shock: %.2f".format(stats.averageShock))
                    appendLine("Maximum Shock: %.2f".format(stats.maximumShock))
                    if (sessionStats != null) {
                        appendLine("Inspection Time: %d ms".format(sessionStats.inspectionDurationMs))
                    }
                }
            }
        }
        view.findViewById<MaterialButton>(R.id.button_heatmap_back)
            .setOnClickListener { nav().navigateBack() }
    }

    override fun onDestroyView() {
        features?.detachHeatMap()
        features = null
        super.onDestroyView()
    }
}
