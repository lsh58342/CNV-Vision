package com.example.cnv.ui.screen.commissioning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/**
 * Commissioning workflow:
 * Building → Floor → DWG → Route → Calibration → Zone → Route Lock → Operation
 */
class CommissioningScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_commissioning, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.refreshGates()
        if (!siteVm.enterCommissioningMode()) {
            Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }

        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.commissioning_context).text = it
        }
        siteVm.floorDashboard.observe(viewLifecycleOwner) { dash ->
            val hint = view.findViewById<TextView>(R.id.commissioning_hint)
            if (dash == null) {
                hint.setText(R.string.comm_workflow_full)
                return@observe
            }
            hint.text = buildString {
                appendLine(getString(R.string.comm_workflow_full))
                appendLine(
                    getString(
                        R.string.zone_dash_dwg,
                        if (dash.dwgReady) getString(R.string.status_ok_label)
                        else getString(R.string.status_missing_label),
                    ),
                )
                appendLine(
                    getString(
                        R.string.zone_dash_calibration,
                        if (dash.calibrationReady) getString(R.string.status_ok_label)
                        else getString(R.string.status_missing_label),
                    ),
                )
                append(
                    getString(
                        R.string.setup_route_lock_status,
                        if (dash.routeLocked) getString(R.string.setup_locked)
                        else getString(R.string.setup_unlocked),
                    ),
                )
            }
        }
        siteVm.bootstrap()
        if (CurrentContext.get().floorId != null) {
            siteVm.loadFloorDashboard()
        }

        view.findViewById<MaterialButton>(R.id.button_comm_building).setOnClickListener {
            nav().navigate(CnvDestination.BUILDING_SELECT)
        }
        view.findViewById<MaterialButton>(R.id.button_comm_floor).setOnClickListener {
            if (CurrentContext.get().buildingId == null) {
                Toast.makeText(requireContext(), R.string.op_select_building_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.FLOOR_SELECT)
        }
        view.findViewById<MaterialButton>(R.id.button_comm_dwg).setOnClickListener {
            if (CurrentContext.get().floorId == null) {
                Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.FLOOR_SELECT)
        }
        view.findViewById<MaterialButton>(R.id.button_comm_route).setOnClickListener {
            if (siteVm.generateRouteForCurrentFloor()) {
                Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_comm_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
        }
        view.findViewById<MaterialButton>(R.id.button_comm_zone_editor).setOnClickListener {
            if (!siteVm.canCreateZone()) {
                Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppNavigator.openZoneEditor(requireActivity())) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_comm_route_lock).setOnClickListener {
            if (siteVm.lockRouteForCurrentFloor()) {
                Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_comm_leave).setOnClickListener {
            siteVm.leaveCommissioningMode()
            nav().navigateClearTo(CnvDestination.BUILDING_SELECT)
        }
    }
}
