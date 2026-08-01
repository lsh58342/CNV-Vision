package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.commissioning.CommissioningWizardProgress
import com.example.cnv.ui.vm.SiteNavigationViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drawing Dashboard — management hub (no CAD / Camera / HeatMap / Debug HUD).
 * Commissioning via Wizard; Operation starts Inspection when Wizard + Route Lock complete.
 */
class DrawingDashboardScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_drawing_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.button_open_drawing).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.ws_operation_blocked, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.OPEN_DRAWING)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_inspection).setOnClickListener {
            if (!DrawingUiStatus.canStartInspection()) {
                Toast.makeText(requireContext(), R.string.draw_inspection_not_ready, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (com.example.cnv.factory.context.CurrentContext.get().zoneId != null) {
                nav().navigate(CnvDestination.INSPECTION)
            } else {
                nav().navigate(CnvDestination.ZONE_LIST)
            }
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_history).setOnClickListener {
            nav().navigate(CnvDestination.INSPECTION_HISTORY)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_heatmap).setOnClickListener {
            nav().navigate(CnvDestination.HEATMAP_VIEWER)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_csv).setOnClickListener {
            Toast.makeText(requireContext(), R.string.setup_csv_drawing_scoped, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<MaterialButton>(R.id.button_open_wizard).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.ws_operation_blocked, Toast.LENGTH_SHORT).show()
            } else {
                nav().navigate(CnvDestination.COMMISSIONING)
            }
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

        val drawing = FactoryCatalog.get().drawings.current()
        val snap = CommissioningWizardProgress.snapshot()
        view.findViewById<TextView>(R.id.drawing_dash_title).text = dash.drawingName
        view.findViewById<TextView>(R.id.drawing_dash_file).text = getString(
            R.string.draw_dwg_file,
            DrawingUiStatus.fileNameFromUri(drawing?.dwgUri),
        )
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dash.registeredAtMs))
        view.findViewById<TextView>(R.id.drawing_dash_registered).text =
            getString(R.string.draw_registered_at, date)
        view.findViewById<TextView>(R.id.drawing_dash_description).text =
            if (dash.description.isBlank()) getString(R.string.draw_no_description)
            else dash.description
        view.findViewById<TextView>(R.id.drawing_dash_context).text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            dash.buildingName,
            dash.floorName,
        )

        view.findViewById<TextView>(R.id.drawing_dash_wizard_progress).text =
            getString(
                R.string.wiz_dashboard_progress,
                snap.completedCount,
                CommissioningWizardProgress.TOTAL_STEPS,
            )

        val inspectionReady = DrawingUiStatus.canStartInspection()
        val statusContainer = view.findViewById<LinearLayout>(R.id.drawing_dash_status_container)
        statusContainer.removeAllViews()
        val rows = listOf(
            getString(R.string.op_status_dwg) to DrawingUiStatus.dwgLabel(requireContext(), dash.dwgReady),
            getString(R.string.setup_origin) to
                if (snap.originOk) getString(R.string.draw_status_origin_set)
                else getString(R.string.draw_status_missing),
            getString(R.string.op_status_calibration) to
                DrawingUiStatus.calibrationLabel(requireContext(), dash.calibrationReady),
            getString(R.string.op_status_route) to
                DrawingUiStatus.routeLabel(requireContext(), dash.routeReady),
            getString(R.string.op_zone_list_section) to
                DrawingUiStatus.zoneLabel(requireContext(), dash.zoneCount),
            getString(R.string.setup_route_lock) to
                if (snap.routeLocked) getString(R.string.status_ok_label)
                else getString(R.string.draw_status_missing),
            getString(R.string.screen_inspection) to
                DrawingUiStatus.inspectionLabel(requireContext(), inspectionReady),
        )
        rows.forEach { (label, value) ->
            val ok = !value.equals(getString(R.string.draw_status_missing), ignoreCase = true) &&
                !value.equals(getString(R.string.draw_status_not_ready), ignoreCase = true)
            statusContainer.addView(
                UiComponents.inflateStatusCard(
                    statusContainer,
                    label,
                    value,
                    UiComponents.statusColor(requireContext(), if (ok) "OK" else "MISSING"),
                ),
            )
        }

        val inspectionBtn = view.findViewById<MaterialButton>(R.id.button_drawing_inspection)
        inspectionBtn.isEnabled = inspectionReady
        inspectionBtn.alpha = if (inspectionReady) 1f else 0.45f

        val commissioningDone = DrawingUiStatus.commissioningComplete(
            dash.dwgReady,
            dash.calibrationReady,
            dash.routeReady,
            dash.zoneCount,
            dash.routeLocked,
        )
        view.findViewById<View>(R.id.drawing_dash_commissioning_section).isVisible = !commissioningDone
    }
}
