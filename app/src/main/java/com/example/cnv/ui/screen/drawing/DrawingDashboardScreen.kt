package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.vm.SiteNavigationViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Drawing Dashboard — commissioning + operation entry for one Drawing. */
class DrawingDashboardScreen : BaseScreen() {

    private val pickDwg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.setup_dwg_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (siteVm.registerDwgForCurrentDrawing(uri.toString())) {
            Toast.makeText(requireContext(), R.string.setup_dwg_registered, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.setup_dwg_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_drawing_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusHeader = view.findViewById<FrameLayout>(R.id.drawing_dash_status_header)
        statusHeader.addView(
            UiComponents.inflateSectionHeader(statusHeader, getString(R.string.op_status_section)),
        )
        val infoHeader = view.findViewById<FrameLayout>(R.id.drawing_dash_info_header)
        infoHeader.addView(
            UiComponents.inflateSectionHeader(infoHeader, getString(R.string.op_info_section)),
        )

        view.findViewById<MaterialButton>(R.id.button_drawing_register_dwg).setOnClickListener {
            pickDwg.launch("*/*")
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_set_origin).setOnClickListener {
            // Structural origin pick (CAD touch wiring later) — once per Drawing.
            if (siteVm.setOriginForCurrentDrawing(0.5f, 0.5f)) {
                Toast.makeText(requireContext(), R.string.setup_origin_set, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_origin_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
            siteVm.markCalibrationReadyForCurrentDrawing()
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_generate_route).setOnClickListener {
            if (siteVm.generateRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_create_zone).setOnClickListener {
            if (!siteVm.canCreateZone()) {
                Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppNavigator.openZoneEditor(requireActivity())) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_route_lock).setOnClickListener {
            if (siteVm.lockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_open_zones).setOnClickListener {
            nav().navigate(CnvDestination.ZONE_LIST)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_heatmap).setOnClickListener {
            nav().navigate(CnvDestination.HEATMAP_VIEWER)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_history).setOnClickListener {
            nav().navigate(CnvDestination.INSPECTION_HISTORY)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_csv).setOnClickListener {
            Toast.makeText(requireContext(), R.string.setup_csv_drawing_scoped, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_replay).setOnClickListener {
            Toast.makeText(requireContext(), R.string.setup_replay_drawing_scoped, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_delete).setOnClickListener {
            val id = CurrentContext.get().drawingId ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setup_delete_drawing)
                .setMessage(R.string.setup_delete_drawing_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (siteVm.deleteDrawing(id)) {
                        nav().navigateBack()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_back).setOnClickListener {
            nav().navigateBack()
        }

        siteVm.drawingDashboard.observe(viewLifecycleOwner) { bind(view, it) }
        siteVm.loadDrawingDashboard()
    }

    override fun onResume() {
        super.onResume()
        siteVm.loadDrawingDashboard()
    }

    private fun bind(view: View, dash: SiteNavigationViewModel.DrawingDashboardUi?) {
        if (dash == null) {
            view.findViewById<TextView>(R.id.drawing_dash_title).text =
                getString(R.string.setup_drawing_dashboard)
            return
        }
        view.findViewById<TextView>(R.id.drawing_dash_title).text = dash.drawingName
        view.findViewById<TextView>(R.id.drawing_dash_subtitle).text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            dash.buildingName,
            dash.floorName,
        )

        fun label(ok: Boolean) =
            if (ok) getString(R.string.status_ok_label) else getString(R.string.status_missing_label)

        val status = view.findViewById<LinearLayout>(R.id.drawing_dash_status_container)
        status.removeAllViews()
        listOf(
            getString(R.string.op_status_dwg) to dash.dwgReady,
            getString(R.string.setup_origin) to dash.originSet,
            getString(R.string.op_status_calibration) to dash.calibrationReady,
            getString(R.string.op_status_route) to dash.routeReady,
            getString(R.string.setup_route_lock) to dash.routeLocked,
        ).forEach { (title, ok) ->
            status.addView(
                UiComponents.inflateStatusCard(
                    status, title, label(ok),
                    UiComponents.statusColor(requireContext(), label(ok)),
                ),
            )
        }

        val info = view.findViewById<LinearLayout>(R.id.drawing_dash_info_container)
        info.removeAllViews()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dash.registeredAtMs))
        info.addView(
            UiComponents.inflateInfoCard(info, getString(R.string.setup_registered_at), date),
        )
        if (dash.description.isNotBlank()) {
            info.addView(
                UiComponents.inflateInfoCard(
                    info, getString(R.string.setup_drawing_desc_hint), dash.description,
                ),
            )
        }
        info.addView(
            UiComponents.inflateInfoCard(
                info, getString(R.string.op_zone_list_section),
                if (dash.zoneCount == 0) getString(R.string.setup_empty_zones)
                else getString(R.string.setup_zone_count, dash.zoneCount),
            ),
        )
        info.addView(
            UiComponents.inflateInfoCard(
                info, getString(R.string.op_last_inspection_title), dash.lastInspectionLabel,
            ),
        )
        info.addView(
            UiComponents.inflateInfoCard(
                info, getString(R.string.op_history_title),
                getString(R.string.op_history_count, dash.historyCount),
            ),
        )
        info.addView(
            UiComponents.inflateInfoCard(
                info, getString(R.string.op_heatmap_title),
                getString(R.string.op_heatmap_count, dash.heatMapCount),
            ),
        )
        info.addView(
            UiComponents.inflateInfoCard(
                info, getString(R.string.setup_replay),
                getString(R.string.setup_replay_count, dash.replayCount),
            ),
        )
    }
}
