package com.example.cnv.ui.screen.heatmap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADLayer
import com.example.cnv.cad.CADView
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.HeatLayerViewerOverlay
import com.example.cnv.heatmap.HeatMapViewerLayerFlags
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date
import kotlin.math.hypot

/**
 * HeatMap Viewer — displays Repository [DrawingHeatLayer] on CAD (STEP 15).
 * Does not generate HeatPoints or run HeatMapGenerator.
 */
class HeatMapScreen : BaseScreen() {

    private val vm: HeatMapViewModel by viewModels { HeatMapViewModel.Factory() }

    private var cadController: CADController? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var spinnerReady = false
    private var lastSessionListKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_heatmap, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cadView = view.findViewById<CADView>(R.id.heatmap_cad_view)
        val overlay = view.findViewById<HeatLayerViewerOverlay>(R.id.heatmap_viewer_overlay)

        cadController = CADController(
            routeRepository = FactoryCatalog.get().routes.underlying(),
            cadView = cadView,
            mapperProvider = { vm.state.value?.heatMapMapper },
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

        cadView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    if (hypot(event.x - touchDownX, event.y - touchDownY) <= TAP_SLOP_PX) {
                        overlay.hitTestZone(event.x, event.y)?.let { vm.highlightZone(it) }
                    }
                }
            }
            false
        }

        view.findViewById<MaterialButton>(R.id.button_heatmap_zoom_in).setOnClickListener {
            cadController?.zoomIn()
            overlay.invalidate()
        }
        view.findViewById<MaterialButton>(R.id.button_heatmap_zoom_out).setOnClickListener {
            cadController?.zoomOut()
            overlay.invalidate()
        }
        view.findViewById<MaterialButton>(R.id.button_heatmap_fit).setOnClickListener {
            cadController?.fitToRoute()
            overlay.invalidate()
        }
        view.findViewById<MaterialButton>(R.id.button_heatmap_reset).setOnClickListener {
            cadController?.resetView()
            overlay.invalidate()
        }
        view.findViewById<MaterialButton>(R.id.button_heatmap_back).setOnClickListener {
            nav().navigateBack()
        }

        view.findViewById<MaterialButton>(R.id.toggle_heatmap).setOnClickListener { vm.toggleHeatMap() }
        view.findViewById<MaterialButton>(R.id.toggle_route).setOnClickListener { vm.toggleRoute() }
        view.findViewById<MaterialButton>(R.id.toggle_zone).setOnClickListener { vm.toggleZone() }
        view.findViewById<MaterialButton>(R.id.toggle_origin).setOnClickListener { vm.toggleOrigin() }
        view.findViewById<MaterialButton>(R.id.toggle_shock).setOnClickListener { vm.toggleShock() }

        vm.state.observe(viewLifecycleOwner) { state ->
            bindState(view, overlay, state)
        }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onDestroyView() {
        val cadView = view?.findViewById<CADView>(R.id.heatmap_cad_view)
        val listener = drawListener
        if (cadView != null && listener != null && cadView.viewTreeObserver.isAlive) {
            cadView.viewTreeObserver.removeOnDrawListener(listener)
        }
        drawListener = null
        cadController?.stop()
        cadController = null
        super.onDestroyView()
    }

    private fun bindState(
        view: View,
        overlay: HeatLayerViewerOverlay,
        state: HeatMapViewModel.UiState,
    ) {
        view.findViewById<TextView>(R.id.heatmap_drawing_name).text =
            getString(R.string.heatmap_drawing_label, state.drawingName)

        val summary = state.selectedSummary
        val dateText = if (summary != null) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(summary.startTimeMs))
        } else {
            getString(R.string.heatmap_no_session_date)
        }
        view.findViewById<TextView>(R.id.heatmap_inspection_date).text =
            getString(R.string.heatmap_inspection_date_label, dateText)

        bindSessionSpinner(view.findViewById(R.id.heatmap_session_spinner), state)
        bindToggleLabels(view, state.flags)
        bindBottomPanel(view.findViewById(R.id.heatmap_bottom_panel), state)

        val empty = view.findViewById<TextView>(R.id.heatmap_empty_message)
        empty.isVisible = state.emptyMessage != null
        empty.text = state.emptyMessage

        overlay.setDisplayConfig(state.displayConfig)
        overlay.setLayerFlags(state.flags)
        overlay.setHeatPoints(state.displayPoints)
        overlay.setRoutePolyline(state.routePolyline)
        overlay.setOriginWorld(state.originWorld)
        overlay.setZones(state.zones)
        overlay.setHighlightedZone(state.highlightedZoneId)
        overlay.invalidate()
    }

    private fun bindSessionSpinner(spinner: Spinner, state: HeatMapViewModel.UiState) {
        val listKey = state.sessions.joinToString(separator = ",") { it.sessionId }
        if (listKey != lastSessionListKey) {
            lastSessionListKey = listKey
            val labels = state.sessions.map { s ->
                val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(s.startTimeMs))
                "$date · shocks=${s.shockCount}"
            }
            spinnerReady = false
            spinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                if (labels.isEmpty()) listOf(getString(R.string.heatmap_no_sessions)) else labels,
            )
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    if (!spinnerReady) return
                    val session = state.sessions.getOrNull(position) ?: return
                    vm.selectSession(session.sessionId)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val index = state.sessions.indexOfFirst { it.sessionId == state.selectedSessionId }
            .coerceAtLeast(0)
        spinnerReady = false
        if (state.sessions.isNotEmpty() && spinner.selectedItemPosition != index) {
            spinner.setSelection(index, false)
        }
        spinner.post { spinnerReady = true }
    }

    private fun bindToggleLabels(view: View, flags: HeatMapViewerLayerFlags) {
        view.findViewById<MaterialButton>(R.id.toggle_heatmap).alpha = if (flags.heatMap) 1f else 0.45f
        view.findViewById<MaterialButton>(R.id.toggle_route).alpha = if (flags.route) 1f else 0.45f
        view.findViewById<MaterialButton>(R.id.toggle_zone).alpha = if (flags.zone) 1f else 0.45f
        view.findViewById<MaterialButton>(R.id.toggle_origin).alpha = if (flags.origin) 1f else 0.45f
        view.findViewById<MaterialButton>(R.id.toggle_shock).alpha = if (flags.shock) 1f else 0.45f
    }

    private fun bindBottomPanel(panel: TextView, state: HeatMapViewModel.UiState) {
        val s = state.selectedSummary
        if (s == null) {
            panel.text = getString(R.string.heatmap_bottom_empty)
            return
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(s.startTimeMs))
        panel.text = getString(
            R.string.heatmap_bottom_stats,
            date,
            formatDuration(s.durationMs),
            s.totalDistanceMm,
            s.shockCount,
            s.coverage * 100f,
            s.maximumShock,
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }

    companion object {
        private const val TAP_SLOP_PX = 24f
    }
}
