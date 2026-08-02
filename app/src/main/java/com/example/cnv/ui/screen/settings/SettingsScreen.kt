package com.example.cnv.ui.screen.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Settings — Calibration / Developer / About (always accessible). */
class SettingsScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.bootstrap()
        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.screen_body).text = it
        }

        view.findViewById<Button>(R.id.button_settings_calibration).setOnClickListener {
            AppNavigator.openCalibration(requireActivity())
        }
        view.findViewById<Button>(R.id.button_settings_developer).setOnClickListener {
            nav().navigate(CnvDestination.DEVELOPER)
        }
        view.findViewById<Button>(R.id.button_settings_about).setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_about_msg, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }
}
