package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.cnv.R
import com.google.android.material.button.MaterialButton

/**
 * Product HeatMap Viewer UI — CAD ≥80%. Algorithm wiring deferred.
 */
class HeatMapViewerFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_heatmap_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val statsPanel = view.findViewById<View>(R.id.heatmap_stats_panel)
        val statsHeader = view.findViewById<MaterialButton>(R.id.heatmap_stats_header)

        statsHeader.setOnClickListener {
            val open = !statsPanel.isVisible
            statsPanel.isVisible = open
            statsHeader.setText(
                if (open) R.string.heatmap_stats_toggle else R.string.heatmap_stats_toggle,
            )
        }

        fun later() {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<MaterialButton>(R.id.button_heatmap_session).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_timeline).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_filter).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_zoom_in).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_zoom_out).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_fit).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_reset).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_search).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_heatmap_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
