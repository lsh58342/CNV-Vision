package com.example.cnv.ui.screen.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen

/**
 * Settings — Inspection profiles + Developer Options + About (STEP 20-4).
 * Always accessible; no Role checks.
 */
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

        val openProfile = View.OnClickListener {
            val drawingId = CurrentContext.get().drawingId
            if (drawingId.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.settings_need_drawing, Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            val args = Bundle().apply {
                putString(NavArgs.DRAWING_ID, drawingId)
            }
            nav().navigate(CnvDestination.INSPECTION_PROFILE_EDITOR, args = args)
        }

        view.findViewById<Button>(R.id.button_settings_inspection_profile)
            .setOnClickListener(openProfile)
        view.findViewById<Button>(R.id.button_settings_sensor_profile)
            .setOnClickListener(openProfile)
        view.findViewById<Button>(R.id.button_settings_rule_profile)
            .setOnClickListener(openProfile)
        view.findViewById<Button>(R.id.button_settings_conveyor_profile)
            .setOnClickListener(openProfile)

        view.findViewById<Button>(R.id.button_settings_developer).setOnClickListener {
            nav().navigate(CnvDestination.DEVELOPER)
        }
        view.findViewById<Button>(R.id.button_settings_about).setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_about_msg, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }
}
