package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cnv.R
import com.google.android.material.button.MaterialButton

/**
 * HeatMap Viewer skeleton — CAD area ≥80% placeholder. No HeatMap algorithm wiring.
 */
class HeatMapViewerFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_heatmap_viewer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.button_heatmap_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
