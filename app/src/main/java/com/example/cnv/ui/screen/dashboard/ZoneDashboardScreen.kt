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
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.dummy.OperationDummyData
import com.example.cnv.ui.screen.dummy.OperationUiSelection
import com.google.android.material.button.MaterialButton

/**
 * Zone Dashboard — Operation start screen (dummy data, Phase 2).
 * Inspection / HeatMap / History / CSV are UI entry points only (no feature wiring).
 */
class ZoneDashboardScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_zone_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zone = OperationUiSelection.selectedZone()
        val dash = OperationDummyData.dashboardFor(zone)
        val factory = OperationUiSelection.selectedFactory()?.name ?: "—"
        val building = OperationUiSelection.selectedBuilding()?.name ?: "—"
        val floor = OperationUiSelection.selectedFloor()?.name ?: "—"

        view.findViewById<TextView>(R.id.dashboard_title).text = dash.zoneName
        view.findViewById<TextView>(R.id.dashboard_subtitle).text =
            getString(R.string.op_context_full, factory, building, floor)

        val statusHeader = view.findViewById<FrameLayout>(R.id.dashboard_status_header_slot)
        statusHeader.addView(
            UiComponents.inflateSectionHeader(statusHeader, getString(R.string.op_status_section)),
        )

        val statusContainer = view.findViewById<LinearLayout>(R.id.dashboard_status_container)
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_route),
                dash.routeStatus,
                UiComponents.statusColor(requireContext(), dash.routeStatus),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_dwg),
                dash.dwgStatus,
                UiComponents.statusColor(requireContext(), dash.dwgStatus),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_calibration),
                dash.calibrationStatus,
                UiComponents.statusColor(requireContext(), dash.calibrationStatus),
            ),
        )

        val infoHeader = view.findViewById<FrameLayout>(R.id.dashboard_info_header_slot)
        infoHeader.addView(
            UiComponents.inflateSectionHeader(infoHeader, getString(R.string.op_info_section)),
        )
        val infoContainer = view.findViewById<LinearLayout>(R.id.dashboard_info_container)
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_last_inspection_title),
                dash.lastInspection,
            ),
        )
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_history_title),
                getString(R.string.op_history_count, dash.historyCount),
            ),
        )
        infoContainer.addView(
            UiComponents.inflateInfoCard(
                infoContainer,
                getString(R.string.op_heatmap_title),
                getString(R.string.op_heatmap_count, dash.heatMapCount),
            ),
        )

        val actionsHeader = view.findViewById<FrameLayout>(R.id.dashboard_actions_header_slot)
        actionsHeader.addView(
            UiComponents.inflateSectionHeader(actionsHeader, getString(R.string.op_actions_section)),
        )

        val later = { Toast.makeText(requireContext(), R.string.op_feature_later, Toast.LENGTH_SHORT).show() }
        view.findViewById<MaterialButton>(R.id.button_dashboard_start).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_dashboard_heatmap).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_dashboard_history).setOnClickListener { later() }
        view.findViewById<MaterialButton>(R.id.button_dashboard_csv).setOnClickListener { later() }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.dashboard_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }
    }
}
