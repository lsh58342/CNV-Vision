package com.example.cnv.ui.legacy.screens

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

class SettingsFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.legacy_fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val developerBtn = view.findViewById<MaterialButton>(R.id.button_settings_developer)
        val commissioningBtn = view.findViewById<MaterialButton>(R.id.button_settings_commissioning)

        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.settings_status).text = it
        }
        siteVm.bootstrap()

        developerBtn.visibility = View.VISIBLE
        commissioningBtn.visibility = View.VISIBLE

        view.findViewById<MaterialButton>(R.id.button_settings_camera).setOnClickListener {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_settings_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
        }
        developerBtn.setOnClickListener { nav().navigate(CnvDestination.DEVELOPER) }
        commissioningBtn.setOnClickListener {
            siteVm.enterCommissioningMode()
            nav().navigate(CnvDestination.DRAWING_WORKSPACE)
        }
        view.findViewById<MaterialButton>(R.id.button_settings_about).setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_about_msg, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_settings_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
