package com.example.cnv.ui.screen.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.camera.ShockClipSettingsStore
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.imu.ShockThresholdStore
import com.example.cnv.imu.ShockUnits
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.textfield.TextInputEditText

/**
 * Settings — shock thresholds + Inspection profiles + Developer Options + About.
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
        ShockThresholdStore.ensureLoaded(requireContext())
        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.screen_body).text = it
        }

        bindShockThresholds(view)
        bindShockClipSettings(view)

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

    private fun bindShockThresholds(view: View) {
        val recordInput = view.findViewById<TextInputEditText>(R.id.input_shock_record_g)
        val criticalInput = view.findViewById<TextInputEditText>(R.id.input_shock_critical_g)
        val derived = view.findViewById<TextView>(R.id.settings_shock_derived)

        fun refreshFields() {
            recordInput.setText("%.3f".format(ShockUnits.recordingThresholdG()))
            criticalInput.setText("%.3f".format(ShockUnits.criticalThresholdG()))
            derived.text = getString(
                R.string.settings_shock_derived_format,
                ShockUnits.warningThresholdG(),
                ShockUnits.highThresholdG(),
            )
        }
        refreshFields()

        view.findViewById<Button>(R.id.button_shock_save).setOnClickListener {
            val rec = recordInput.text?.toString()?.toFloatOrNull()
            val crit = criticalInput.text?.toString()?.toFloatOrNull()
            if (rec == null || crit == null) {
                Toast.makeText(requireContext(), R.string.settings_shock_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!ShockThresholdStore.save(requireContext(), rec, crit)) {
                Toast.makeText(requireContext(), R.string.settings_shock_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            refreshFields()
            Toast.makeText(requireContext(), R.string.settings_shock_saved, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.button_shock_reset).setOnClickListener {
            ShockThresholdStore.resetToDefaults(requireContext())
            refreshFields()
            Toast.makeText(requireContext(), R.string.settings_shock_reset_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindShockClipSettings(view: View) {
        val preInput = view.findViewById<TextInputEditText>(R.id.input_clip_pre_sec)
        val postInput = view.findViewById<TextInputEditText>(R.id.input_clip_post_sec)
        preInput.setText("%.1f".format(ShockClipSettingsStore.preSec(requireContext())))
        postInput.setText("%.1f".format(ShockClipSettingsStore.postSec(requireContext())))
        view.findViewById<Button>(R.id.button_clip_save).setOnClickListener {
            val pre = preInput.text?.toString()?.toFloatOrNull()
            val post = postInput.text?.toString()?.toFloatOrNull()
            if (pre == null || post == null) {
                Toast.makeText(requireContext(), R.string.settings_shock_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!ShockClipSettingsStore.save(requireContext(), pre, post)) {
                Toast.makeText(requireContext(), R.string.settings_shock_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Toast.makeText(requireContext(), R.string.settings_clip_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
