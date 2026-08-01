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
import com.example.cnv.ui.vm.SiteNavigationViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drawing Dashboard — management hub (no CAD / Camera / HeatMap / Debug HUD).
 * Commissioning work happens on Open Drawing; Operation starts Inspection here.
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
            nav().navigate(CnvDestination.OPEN_DRAWING)
        }
        view.findViewById<MaterialButton>(R.id.button_drawing_inspection).setOnClickListener {
            val dash = siteVm.drawingDashboard.value
            if (dash == null || !DrawingUiStatus.canStartInspection(dash.dwgReady, dash.calibrationReady, dash.routeReady)) {
                Toast.makeText(requireContext(), R.string.draw_inspection_not_ready, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Operation: Dashboard → (Zone if needed) → Inspection
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

        val openCommissioning = {
            nav().navigate(CnvDestination.OPEN_DRAWING)
        }
        view.findViewById<MaterialButton>(R.id.button_comm_open_calibration).setOnClickListener { openCommissioning() }
        view.findViewById<MaterialButton>(R.id.button_comm_open_route).setOnClickListener { openCommissioning() }
        view.findViewById<MaterialButton>(R.id.button_comm_open_zone).setOnClickListener { openCommissioning() }
        view.findViewById<MaterialButton>(R.id.button_comm_open_lock).setOnClickListener { openCommissioning() }

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

        val inspectionReady = DrawingUiStatus.canStartInspection(
            dash.dwgReady, dash.calibrationReady, dash.routeReady,
        )
        val statusContainer = view.findViewById<LinearLayout>(R.id.drawing_dash_status_container)
        statusContainer.removeAllViews()
        val rows = listOf(
            getString(R.string.op_status_dwg) to DrawingUiStatus.dwgLabel(requireContext(), dash.dwgReady),
            getString(R.string.op_status_calibration) to
                DrawingUiStatus.calibrationLabel(requireContext(), dash.calibrationReady),
            getString(R.string.op_status_route) to
                DrawingUiStatus.routeLabel(requireContext(), dash.routeReady),
            getString(R.string.op_zone_list_section) to
                DrawingUiStatus.zoneLabel(requireContext(), dash.zoneCount),
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
