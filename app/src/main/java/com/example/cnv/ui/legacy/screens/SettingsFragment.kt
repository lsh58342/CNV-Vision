package com.example.cnv.ui.legacy.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.factory.context.AccessRole
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
        siteVm.canOpenDeveloper.observe(viewLifecycleOwner) { allowed ->
            developerBtn.isVisible = allowed == true
            // Operation hides developer entry.
        }
        siteVm.canOpenCommissioning.observe(viewLifecycleOwner) { allowed ->
            commissioningBtn.isVisible = allowed == true
        }
        siteVm.bootstrap()
        siteVm.refreshGates()

        view.findViewById<MaterialButton>(R.id.button_role_operator)
            .setOnClickListener { siteVm.setRole(AccessRole.OPERATOR) }
        view.findViewById<MaterialButton>(R.id.button_role_admin)
            .setOnClickListener { siteVm.setRole(AccessRole.ADMIN) }
        view.findViewById<MaterialButton>(R.id.button_role_developer)
            .setOnClickListener { siteVm.setRole(AccessRole.DEVELOPER) }

        view.findViewById<MaterialButton>(R.id.button_settings_camera).setOnClickListener {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_settings_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
        }
        developerBtn.setOnClickListener { nav().navigate(CnvDestination.DEVELOPER) }
        commissioningBtn.setOnClickListener {
            if (siteVm.enterCommissioningMode()) {
                nav().navigate(CnvDestination.COMMISSIONING)
            } else {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_settings_about).setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_about_msg, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_settings_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
