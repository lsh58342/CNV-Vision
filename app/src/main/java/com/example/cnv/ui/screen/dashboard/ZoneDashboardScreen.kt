package com.example.cnv.ui.screen.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.zone.dashboard.ZoneDashboardState
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zone Dashboard — Operation start (Zone → Inspection / HeatMap / History / CSV).
 */
class ZoneDashboardScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_zone_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusHeader = view.findViewById<FrameLayout>(R.id.dashboard_status_header_slot)
        statusHeader.addView(
            UiComponents.inflateSectionHeader(statusHeader, getString(R.string.op_status_section)),
        )
        val infoHeader = view.findViewById<FrameLayout>(R.id.dashboard_info_header_slot)
        infoHeader.addView(
            UiComponents.inflateSectionHeader(infoHeader, getString(R.string.op_info_section)),
        )
        val actionsHeader = view.findViewById<FrameLayout>(R.id.dashboard_actions_header_slot)
        actionsHeader.addView(
            UiComponents.inflateSectionHeader(actionsHeader, getString(R.string.op_actions_section)),
        )

        view.findViewById<MaterialButton>(R.id.button_dashboard_start).setOnClickListener {
            val state = siteVm.dashboard.value
            if (state?.canStartInspection != true) {
                Toast.makeText(requireContext(), R.string.zone_dash_not_ready, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.INSPECTION)
        }
        view.findViewById<MaterialButton>(R.id.button_dashboard_heatmap).setOnClickListener {
            nav().navigate(CnvDestination.HEATMAP_VIEWER)
        }
        view.findViewById<MaterialButton>(R.id.button_dashboard_history).setOnClickListener {
            nav().navigate(CnvDestination.INSPECTION_HISTORY)
        }
        view.findViewById<MaterialButton>(R.id.button_dashboard_csv).setOnClickListener {
            Toast.makeText(requireContext(), R.string.zone_dash_csv_future, Toast.LENGTH_SHORT).show()
        }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.dashboard_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }

        siteVm.dashboard.observe(viewLifecycleOwner) { bind(view, it) }
        siteVm.loadDashboard()
    }

    private fun bind(view: View, state: ZoneDashboardState) {
        val zone = state.zone
        view.findViewById<TextView>(R.id.dashboard_title).text =
            zone?.name ?: getString(R.string.zone_dash_missing)
        view.findViewById<TextView>(R.id.dashboard_subtitle).text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
            siteVm.currentFloorName(),
        )

        fun label(ok: Boolean) =
            if (ok) getString(R.string.status_ok_label) else getString(R.string.status_missing_label)

        val statusContainer = view.findViewById<LinearLayout>(R.id.dashboard_status_container)
        statusContainer.removeAllViews()
        val routeOk = zone != null && com.example.cnv.factory.repository.FactoryCatalog.get().routes.hasRoute()
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_route),
                label(routeOk),
                UiComponents.statusColor(requireContext(), label(routeOk)),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_dwg),
                label(state.dwgReady),
                UiComponents.statusColor(requireContext(), label(state.dwgReady)),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_calibration),
                label(state.calibrationReady),
                UiComponents.statusColor(requireContext(), label(state.calibrationReady)),
            ),
        )

        val infoContainer = view.findViewById<LinearLayout>(R.id.dashboard_info_container)
        infoContainer.removeAllViews()
        val lastText = state.lastInspection?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it.endTimeMs))
        } ?: "—"
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_last_inspection_title),
                lastText,
            ),
        )
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_history_title),
                getString(R.string.op_history_count, state.inspectionHistoryCount),
            ),
        )
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_heatmap_title),
                getString(R.string.op_heatmap_count, state.heatMapCount),
            ),
        )
    }
}
