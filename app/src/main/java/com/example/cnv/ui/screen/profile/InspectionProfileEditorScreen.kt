package com.example.cnv.ui.screen.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.ConveyorProfileConfig
import com.example.cnv.profile.RuleProfileEntry
import com.example.cnv.profile.SensorProfile
import com.example.cnv.rule.DefaultRuleCatalog
import com.example.cnv.rule.RuleSeverity
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

/**
 * Drawing → Inspection Profile Editor → Save (STEP 20-2).
 * Edits Conveyor / Sensor / Rule profiles; Inspection start freezes a Session Snapshot.
 */
class InspectionProfileEditorScreen : BaseScreen() {

    private val viewModel: InspectionProfileEditorViewModel by viewModels {
        InspectionProfileEditorViewModel.Factory()
    }

    private data class RuleRowViews(
        val ruleId: String,
        val enabled: MaterialSwitch,
        val threshold: TextInputEditText,
        val severity: Spinner,
        val version: Int,
    )

    private val ruleRows = ArrayList<RuleRowViews>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_inspection_profile_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val drawingId = arguments?.getString(NavArgs.DRAWING_ID)
        viewModel.state.observe(viewLifecycleOwner) { state ->
            if (state.loading) return@observe
            if (state.errorMessage != null && state.drawingId.isBlank()) {
                Toast.makeText(requireContext(), state.errorMessage, Toast.LENGTH_SHORT).show()
                nav().navigateBack()
                return@observe
            }
            view.findViewById<TextView>(R.id.profile_editor_drawing).text =
                getString(R.string.profile_editor_drawing_fmt, state.drawingName)
            if (ruleRows.isEmpty()) {
                bindForm(view, state.profile.conveyor, state.profile.sensor, state.profile.rule.entries)
            }
            if (state.saved) {
                Toast.makeText(requireContext(), R.string.profile_editor_saved, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.load(drawingId)

        view.findViewById<MaterialButton>(R.id.button_profile_cancel).setOnClickListener {
            nav().navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.button_profile_save).setOnClickListener {
            saveFromForm(view)
        }
    }

    private fun bindForm(
        root: View,
        conveyor: ConveyorProfile,
        sensor: SensorProfile,
        rules: List<RuleProfileEntry>,
    ) {
        val speedInput = root.findViewById<TextInputEditText>(R.id.input_nominal_speed)
        val toleranceInput = root.findViewById<TextInputEditText>(R.id.input_speed_tolerance)
        val fpsInput = root.findViewById<TextInputEditText>(R.id.input_expected_fps)
        val directionSpinner = root.findViewById<Spinner>(R.id.spinner_direction)
        val motionSpinner = root.findViewById<Spinner>(R.id.spinner_motion)

        val directions = ConveyorDirection.entries
        val motions = ConveyorMotionProfile.entries
        directionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            directions.map { it.name },
        )
        motionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            motions.map { it.name },
        )
        speedInput.setText(conveyor.nominalSpeedMPerMin?.toString().orEmpty())
        toleranceInput.setText(conveyor.speedTolerancePercent.toString())
        fpsInput.setText(conveyor.expectedFps.toString())
        directionSpinner.setSelection(directions.indexOf(conveyor.direction).coerceAtLeast(0))
        motionSpinner.setSelection(motions.indexOf(conveyor.motionProfile).coerceAtLeast(0))

        root.findViewById<TextInputEditText>(R.id.input_gravity_alpha)
            .setText(sensor.gravityFilterAlpha.toString())
        root.findViewById<TextInputEditText>(R.id.input_highpass_alpha)
            .setText(sensor.highPassAlpha.toString())
        root.findViewById<TextInputEditText>(R.id.input_shock_threshold)
            .setText(sensor.minimumShockThreshold.toString())
        root.findViewById<TextInputEditText>(R.id.input_peak_interval)
            .setText(sensor.peakIntervalNs.toString())
        root.findViewById<TextInputEditText>(R.id.input_moving_avg)
            .setText(sensor.movingAverageWindow.toString())
        root.findViewById<TextInputEditText>(R.id.input_tracking_threshold)
            .setText(sensor.trackingConfidenceThreshold.toString())

        val list = root.findViewById<LinearLayout>(R.id.profile_rule_list)
        list.removeAllViews()
        ruleRows.clear()
        val byId = rules.associateBy { it.ruleId }
        val defs = DefaultRuleCatalog.defaults()
        val inflater = layoutInflater
        for (def in defs) {
            val entry = byId[def.ruleId] ?: RuleProfileEntry(
                ruleId = def.ruleId,
                enabled = def.enabled,
                ruleVersion = def.version,
            )
            val row = inflater.inflate(R.layout.item_rule_profile_row, list, false)
            row.findViewById<TextView>(R.id.rule_row_title).text =
                "${def.ruleId} — ${def.description}"
            val enabled = row.findViewById<MaterialSwitch>(R.id.rule_row_enabled)
            enabled.isChecked = entry.enabled
            val threshold = row.findViewById<TextInputEditText>(R.id.rule_row_threshold)
            threshold.setText(entry.thresholdOverride?.toString().orEmpty())
            val severity = row.findViewById<Spinner>(R.id.rule_row_severity)
            val options = InspectionProfileEditorViewModel.SEVERITY_OPTIONS
            severity.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                options.map { if (it.isEmpty()) getString(R.string.profile_severity_default) else it },
            )
            val sevName = entry.severityOverride?.name.orEmpty()
            severity.setSelection(options.indexOf(sevName).coerceAtLeast(0))
            list.addView(row)
            ruleRows += RuleRowViews(
                ruleId = def.ruleId,
                enabled = enabled,
                threshold = threshold,
                severity = severity,
                version = entry.ruleVersion,
            )
        }
    }

    private fun saveFromForm(root: View) {
        val defaults = ConveyorProfileConfig.DEFAULT
        val speedText = root.findViewById<TextInputEditText>(R.id.input_nominal_speed)
            .text?.toString()?.trim().orEmpty()
        val nominal = speedText.toFloatOrNull()
        if (speedText.isNotEmpty() && nominal == null) {
            Toast.makeText(requireContext(), R.string.conveyor_invalid_speed, Toast.LENGTH_SHORT).show()
            return
        }
        val directions = ConveyorDirection.entries
        val motions = ConveyorMotionProfile.entries
        val directionSpinner = root.findViewById<Spinner>(R.id.spinner_direction)
        val motionSpinner = root.findViewById<Spinner>(R.id.spinner_motion)
        val conveyor = ConveyorProfile(
            nominalSpeedMPerMin = nominal,
            speedTolerancePercent = root.findViewById<TextInputEditText>(R.id.input_speed_tolerance)
                .text?.toString()?.toFloatOrNull()
                ?: defaults.defaultSpeedTolerancePercent,
            direction = directions[directionSpinner.selectedItemPosition],
            expectedFps = root.findViewById<TextInputEditText>(R.id.input_expected_fps)
                .text?.toString()?.toFloatOrNull()
                ?: defaults.defaultExpectedFps,
            motionProfile = motions[motionSpinner.selectedItemPosition],
        )
        val sensorDefaults = SensorProfile.DEFAULT
        val sensor = SensorProfile(
            gravityFilterAlpha = root.findViewById<TextInputEditText>(R.id.input_gravity_alpha)
                .text?.toString()?.toFloatOrNull() ?: sensorDefaults.gravityFilterAlpha,
            highPassAlpha = root.findViewById<TextInputEditText>(R.id.input_highpass_alpha)
                .text?.toString()?.toFloatOrNull() ?: sensorDefaults.highPassAlpha,
            minimumShockThreshold = root.findViewById<TextInputEditText>(R.id.input_shock_threshold)
                .text?.toString()?.toFloatOrNull() ?: sensorDefaults.minimumShockThreshold,
            peakIntervalNs = root.findViewById<TextInputEditText>(R.id.input_peak_interval)
                .text?.toString()?.toLongOrNull() ?: sensorDefaults.peakIntervalNs,
            movingAverageWindow = com.example.cnv.imu.ShockUnits.clampMovingAverageWindow(
                root.findViewById<TextInputEditText>(R.id.input_moving_avg)
                    .text?.toString()?.toIntOrNull() ?: sensorDefaults.movingAverageWindow,
            ),
            trackingConfidenceThreshold = root.findViewById<TextInputEditText>(R.id.input_tracking_threshold)
                .text?.toString()?.toFloatOrNull() ?: sensorDefaults.trackingConfidenceThreshold,
        )
        val options = InspectionProfileEditorViewModel.SEVERITY_OPTIONS
        val rules = ruleRows.map { row ->
            val thrText = row.threshold.text?.toString()?.trim().orEmpty()
            val sevRaw = options.getOrNull(row.severity.selectedItemPosition).orEmpty()
            RuleProfileEntry(
                ruleId = row.ruleId,
                enabled = row.enabled.isChecked,
                ruleVersion = row.version,
                thresholdOverride = thrText.toFloatOrNull(),
                severityOverride = sevRaw.takeIf { it.isNotBlank() }
                    ?.let { runCatching { RuleSeverity.valueOf(it) }.getOrNull() },
            )
        }
        if (viewModel.save(conveyor, sensor, rules)) {
            nav().navigateClearTo(CnvDestination.DRAWING_WORKSPACE)
        } else {
            Toast.makeText(requireContext(), R.string.profile_editor_save_fail, Toast.LENGTH_SHORT).show()
        }
    }
}
