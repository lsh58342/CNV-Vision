package com.example.cnv.ui.screen.replay

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADLayer
import com.example.cnv.cad.CADView
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.replay.analysis.ReplayFilter
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText

/**
 * Replay Viewer + Analysis Tools UI (STEP 16 / 16-1).
 * Analysis reads Engine cache only; does not modify ReplayEngine.
 */
class ReplayScreen : BaseScreen() {

    private val vm: ReplayViewModel by viewModels { ReplayViewModel.Factory() }

    private var cadController: CADController? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var filterReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_replay, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionId = arguments?.getString(NavArgs.SESSION_ID)
        val drawingId = arguments?.getString(NavArgs.DRAWING_ID)
        if (sessionId.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }

        val cadView = view.findViewById<CADView>(R.id.replay_cad_view)
        val overlay = view.findViewById<ReplayOverlay>(R.id.replay_overlay)

        cadController = CADController(
            routeRepository = FactoryCatalog.get().routes.underlying(),
            cadView = cadView,
            mapperProvider = { null },
            debugHud = null,
        ).also {
            it.setLayerEnabled(CADLayer.DEBUG, false)
            it.setLayerEnabled(CADLayer.ROUTE, false)
            it.setLayerEnabled(CADLayer.POSITION, false)
            it.start()
            it.fitToRoute()
        }
        overlay.setCameraProvider { cadView.viewport().camera }

        val listener = ViewTreeObserver.OnDrawListener {
            overlay.postInvalidateOnAnimation()
        }
        drawListener = listener
        cadView.viewTreeObserver.addOnDrawListener(listener)

        view.findViewById<MaterialButton>(R.id.button_replay_back).setOnClickListener {
            nav().navigateBack()
        }
        view.findViewById<MaterialButton>(R.id.button_replay_fit).setOnClickListener {
            cadController?.fitToRoute()
            overlay.invalidate()
        }

        bindAnalysisToolbar(view)
        bindFrameNav(view)
        bindFilters(view)
        bindSearch(view)

        vm.state.observe(viewLifecycleOwner) { state -> bindState(view, overlay, state) }
        vm.load(sessionId, drawingId)
    }

    override fun onDestroyView() {
        val cadView = view?.findViewById<CADView>(R.id.replay_cad_view)
        val listener = drawListener
        if (cadView != null && listener != null && cadView.viewTreeObserver.isAlive) {
            cadView.viewTreeObserver.removeOnDrawListener(listener)
        }
        drawListener = null
        cadController?.stop()
        cadController = null
        super.onDestroyView()
    }

    private fun bindAnalysisToolbar(view: View) {
        view.findViewById<MaterialButton>(R.id.button_jump_shock).setOnClickListener {
            val targets = vm.jumpShockList()
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), R.string.replay_no_shocks, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.replay_jump_shock)
                .setItems(targets.map { it.label }.toTypedArray()) { _, which ->
                    vm.selectShock(targets[which])
                }
                .show()
        }
        view.findViewById<MaterialButton>(R.id.button_jump_zone).setOnClickListener {
            val targets = vm.jumpZoneList()
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), R.string.replay_no_zones, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.replay_jump_zone)
                .setItems(targets.map { it.zoneName }.toTypedArray()) { _, which ->
                    vm.selectZone(targets[which])
                }
                .show()
        }
        view.findViewById<MaterialButton>(R.id.button_jump_low_conf).setOnClickListener {
            val targets = vm.jumpLowConfidenceList()
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), R.string.replay_no_low_conf, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.replay_jump_low_conf)
                .setItems(targets.map { it.label }.toTypedArray()) { _, which ->
                    vm.selectLowConfidence(targets[which])
                }
                .show()
        }
        view.findViewById<MaterialButton>(R.id.button_jump_timestamp).setOnClickListener {
            promptNumber(R.string.replay_jump_timestamp, R.string.replay_input_elapsed_ms) { value ->
                vm.jumpTimestampElapsedMs(value.toLong())
            }
        }
        view.findViewById<MaterialButton>(R.id.button_jump_route).setOnClickListener {
            promptNumber(R.string.replay_jump_route, R.string.replay_input_route_mm) { value ->
                vm.jumpRoutePositionMm(value)
            }
        }
    }

    private fun bindFrameNav(view: View) {
        view.findViewById<MaterialButton>(R.id.button_prev_event).setOnClickListener { vm.previousEvent() }
        view.findViewById<MaterialButton>(R.id.button_next_event).setOnClickListener { vm.nextEvent() }
        view.findViewById<MaterialButton>(R.id.button_prev_shock).setOnClickListener { vm.previousShock() }
        view.findViewById<MaterialButton>(R.id.button_next_shock).setOnClickListener { vm.nextShock() }
        view.findViewById<MaterialButton>(R.id.button_prev_zone).setOnClickListener { vm.previousZone() }
        view.findViewById<MaterialButton>(R.id.button_next_zone).setOnClickListener { vm.nextZone() }
    }

    private fun bindFilters(view: View) {
        val shock = view.findViewById<MaterialCheckBox>(R.id.filter_shock_only)
        val low = view.findViewById<MaterialCheckBox>(R.id.filter_low_conf_only)
        fun applyChecks() {
            if (!filterReady) return
            val cur = vm.state.value?.filter ?: ReplayFilter.NONE
            vm.setFilter(
                cur.copy(
                    shocksOnly = shock.isChecked,
                    lowConfidenceOnly = low.isChecked,
                ),
            )
        }
        shock.setOnCheckedChangeListener { _, _ -> applyChecks() }
        low.setOnCheckedChangeListener { _, _ -> applyChecks() }
        filterReady = true

        view.findViewById<MaterialButton>(R.id.button_filter_zone).setOnClickListener {
            val zones = vm.state.value?.zoneOptions.orEmpty()
            val labels = mutableListOf(getString(R.string.replay_filter_zone_all))
            labels.addAll(zones.map { it.name })
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.replay_filter_zone)
                .setItems(labels.toTypedArray()) { _, which ->
                    val cur = vm.state.value?.filter ?: ReplayFilter.NONE
                    val zoneId = if (which == 0) null else zones.getOrNull(which - 1)?.id
                    vm.setFilter(cur.copy(zoneId = zoneId))
                }
                .show()
        }
        view.findViewById<MaterialButton>(R.id.button_filter_time).setOnClickListener {
            promptTimeRange()
        }
    }

    private fun bindSearch(view: View) {
        view.findViewById<TextInputEditText>(R.id.replay_search).addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    vm.setSearchQuery(s?.toString().orEmpty())
                }
            },
        )
    }

    private fun bindState(view: View, overlay: ReplayOverlay, state: ReplayViewModel.UiState) {
        val empty = view.findViewById<TextView>(R.id.replay_empty)
        if (state.errorMessage != null) {
            empty.isVisible = true
            empty.text = state.errorMessage
            return
        }
        if (state.loading) {
            empty.isVisible = true
            empty.text = getString(R.string.replay_loading)
            return
        }
        empty.isVisible = state.frameCount == 0
        empty.text = getString(R.string.replay_no_events)

        view.findViewById<TextView>(R.id.replay_session_label).text = getString(
            R.string.replay_session_label,
            state.drawingName,
            state.sessionId ?: "—",
            state.frameIndex + 1,
            state.frameCount,
            state.visibleCount,
        )
        view.findViewById<TextView>(R.id.replay_marker_label).text = state.markerLabel

        val s = state.statistics
        view.findViewById<TextView>(R.id.replay_stats_panel).text = getString(
            R.string.replay_stats_format,
            formatDuration(s.currentTimeMs),
            formatDuration(s.elapsedMs),
            s.distanceMm,
            s.currentZoneName,
            if (s.hasShock) getString(R.string.replay_shock_yes) else getString(R.string.replay_shock_no),
            s.shockStrength,
            s.trackingConfidence,
            s.validationScore,
        )

        overlay.setRoutePolyline(state.routePolyline)
        overlay.setZones(state.zones)
        overlay.setHighlightedZone(state.highlightedZoneId)
        overlay.setShockFrames(state.shockFrames)
        overlay.setLowConfidenceFrames(state.lowConfidenceFrames)
        overlay.setCurrent(state.current, state.highlight)
        overlay.invalidate()
    }

    private fun promptNumber(titleRes: Int, hintRes: Int, onValue: (Float) -> Unit) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(hintRes)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = input.text?.toString()?.toFloatOrNull()
                if (v == null) {
                    Toast.makeText(requireContext(), R.string.replay_invalid_number, Toast.LENGTH_SHORT).show()
                } else {
                    onValue(v)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptTimeRange() {
        val from = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.replay_input_time_from_ms)
            setPadding(48, 24, 48, 12)
        }
        val to = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.replay_input_time_to_ms)
            setPadding(48, 12, 48, 24)
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(from)
            addView(to)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.replay_filter_time)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val fromMs = from.text?.toString()?.toLongOrNull()
                val toMs = to.text?.toString()?.toLongOrNull()
                val cur = vm.state.value?.filter ?: ReplayFilter.NONE
                val baseNs = vm.state.value?.sessionStartNs ?: 0L
                vm.setFilter(
                    cur.copy(
                        timeFromNs = fromMs?.let { baseNs + it * 1_000_000L },
                        timeToNs = toMs?.let { baseNs + it * 1_000_000L },
                    ),
                )
            }
            .setNeutralButton(R.string.replay_filter_clear) { _, _ ->
                val cur = vm.state.value?.filter ?: ReplayFilter.NONE
                vm.setFilter(cur.copy(timeFromNs = null, timeToNs = null))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }
}
