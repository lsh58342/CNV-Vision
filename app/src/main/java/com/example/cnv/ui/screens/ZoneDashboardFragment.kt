package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

class ZoneDashboardFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_zone_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.dashboard.observe(viewLifecycleOwner) { state ->
            val zone = state.zone
            view.findViewById<TextView>(R.id.zone_dash_title).text =
                zone?.name ?: getString(R.string.zone_dash_missing)
            view.findViewById<TextView>(R.id.zone_dash_body).text = buildString {
                appendLine(getString(R.string.zone_dash_dwg, if (state.dwgReady) "OK" else "MISSING"))
                appendLine(
                    getString(
                        R.string.zone_dash_calibration,
                        if (state.calibrationReady) "OK" else "MISSING",
                    ),
                )
                appendLine(
                    getString(
                        R.string.zone_dash_last_inspection,
                        state.lastInspection?.sessionId?.take(8) ?: "—",
                    ),
                )
                appendLine(getString(R.string.zone_dash_history_count, state.inspectionHistoryCount))
                appendLine(getString(R.string.zone_dash_heatmap_count, state.heatMapCount))
            }
        }
        siteVm.loadDashboard()

        view.findViewById<MaterialButton>(R.id.button_zone_start_inspection)
            .setOnClickListener { nav().navigate(CnvDestination.INSPECTION) }
        view.findViewById<MaterialButton>(R.id.button_zone_heatmap)
            .setOnClickListener { nav().navigate(CnvDestination.HEATMAP_VIEWER) }
        view.findViewById<MaterialButton>(R.id.button_zone_history)
            .setOnClickListener { nav().navigate(CnvDestination.INSPECTION_HISTORY) }
        view.findViewById<MaterialButton>(R.id.button_zone_csv)
            .setOnClickListener {
                Toast.makeText(requireContext(), R.string.zone_dash_csv_future, Toast.LENGTH_SHORT).show()
            }
        view.findViewById<MaterialButton>(R.id.button_zone_settings)
            .setOnClickListener { nav().navigate(CnvDestination.SETTINGS) }
        view.findViewById<MaterialButton>(R.id.button_zone_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
