package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

class CommissioningFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_commissioning, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.refreshGates()
        if (siteVm.canOpenCommissioning.value != true) {
            Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }
        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.commissioning_context).text = it
        }
        siteVm.dashboard.observe(viewLifecycleOwner) { state ->
            val dwg = if (state.dwgReady) {
                getString(R.string.status_ok_label)
            } else {
                getString(R.string.status_missing_label)
            }
            val cal = if (state.calibrationReady) {
                getString(R.string.status_ok_label)
            } else {
                getString(R.string.status_missing_label)
            }
            view.findViewById<TextView>(R.id.commissioning_hint).text = buildString {
                appendLine(getString(R.string.comm_workflow_hint))
                appendLine(getString(R.string.zone_dash_dwg, dwg))
                append(getString(R.string.zone_dash_calibration, cal))
            }
        }
        siteVm.bootstrap()
        siteVm.loadDashboard()

        view.findViewById<MaterialButton>(R.id.button_comm_dwg).setOnClickListener {
            Toast.makeText(requireContext(), R.string.comm_dwg_structure, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_comm_route).setOnClickListener {
            Toast.makeText(requireContext(), R.string.comm_route_structure, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_comm_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
        }
        view.findViewById<MaterialButton>(R.id.button_comm_zone_editor).setOnClickListener {
            if (!AppNavigator.openZoneEditor(requireActivity())) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_comm_route_lock).setOnClickListener {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_comm_leave).setOnClickListener {
            siteVm.leaveCommissioningMode()
            nav().navigateClearTo(CnvDestination.FACTORY_SELECT)
        }
    }
}
