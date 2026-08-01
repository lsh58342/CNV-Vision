package com.example.cnv.ui.legacy.screens

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
    ): View = inflater.inflate(R.layout.legacy_fragment_zone_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.dashboard.observe(viewLifecycleOwner) { state ->
            val zone = state.zone
            view.findViewById<TextView>(R.id.zone_dash_title).text =
                zone?.name ?: getString(R.string.zone_dash_missing)

            val dwg = view.findViewById<TextView>(R.id.zone_dash_dwg_status)
            val cal = view.findViewById<TextView>(R.id.zone_dash_cal_status)
            val dwgLabel = if (state.dwgReady) {
                getString(R.string.status_ok_label)
            } else {
                getString(R.string.status_missing_label)
            }
            val calLabel = if (state.calibrationReady) {
                getString(R.string.status_ok_label)
            } else {
                getString(R.string.status_missing_label)
            }
            dwg.text = getString(R.string.zone_dash_dwg, dwgLabel)
            cal.text = getString(R.string.zone_dash_calibration, calLabel)
            dwg.setTextColor(
                resources.getColor(
                    if (state.dwgReady) R.color.cnv_status_ok else R.color.cnv_status_missing,
                    null,
                ),
            )
            cal.setTextColor(
                resources.getColor(
                    if (state.calibrationReady) R.color.cnv_status_ok else R.color.cnv_status_missing,
                    null,
                ),
            )

            view.findViewById<TextView>(R.id.zone_dash_last_inspection).text = buildString {
                append(
                    getString(
                        R.string.zone_dash_last_inspection,
                        state.lastInspection?.sessionId?.take(8) ?: "—",
                    ),
                )
                append("  ·  ")
                append(getString(R.string.zone_dash_history_count, state.inspectionHistoryCount))
            }
            view.findViewById<TextView>(R.id.zone_dash_body).text =
                getString(R.string.zone_dash_heatmap_count, state.heatMapCount)
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
