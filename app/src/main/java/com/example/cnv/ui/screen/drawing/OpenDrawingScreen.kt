package com.example.cnv.ui.screen.drawing

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADLayer
import com.example.cnv.cad.CADView
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.zone.editor.ZoneEditorController
import com.google.android.material.button.MaterialButton

/**
 * Open Drawing Workspace — Commissioning only.
 * No Inspection / Camera / HeatMap / History / Developer HUD.
 */
class OpenDrawingScreen : BaseScreen() {

    private enum class PickMode {
        IDLE,
        ORIGIN,
        ZONE_CAD_START,
        ZONE_CAD_END,
        ZONE_READY_SAVE,
    }

    private var cadController: CADController? = null
    private val zoneEditor = ZoneEditorController()
    private val highlightSegments = linkedSetOf<String>()
    private var pickMode: PickMode = PickMode.IDLE
    private var pendingZoneName: String = ""
    private var pendingZoneColor: Int = Color.parseColor("#FF9800")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_open_drawing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Operation Mode cannot access Open Drawing.
        if (!siteVm.enterCommissioningMode()) {
            Toast.makeText(requireContext(), R.string.ws_operation_blocked, Toast.LENGTH_SHORT).show()
            nav().navigate(CnvDestination.DRAWING_DASHBOARD)
            return
        }

        bindToolbar(view)
        val cadView = view.findViewById<CADView>(R.id.open_drawing_cad)
        cadController = CADController(
            routeRepository = FactoryCatalog.get().routes.underlying(),
            cadView = cadView,
            mapperProvider = { null },
            debugHud = null,
            errorSegmentIdsProvider = { highlightSegments.toSet() },
        )
        cadController?.setLayerEnabled(CADLayer.DEBUG, false)

        wireViewerTools(view)
        wireActionPanel(view)
        refreshLockUi(view)
        setModeStatus(view, getString(R.string.ws_mode_idle))
    }

    override fun onResume() {
        super.onResume()
        if (CurrentContext.get().appMode != AppMode.COMMISSIONING) {
            if (!siteVm.enterCommissioningMode()) {
                nav().navigate(CnvDestination.DRAWING_DASHBOARD)
                return
            }
        }
        cadController?.start()
        siteVm.loadDrawingDashboard()
        view?.let { refreshLockUi(it) }
    }

    override fun onPause() {
        cadController?.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        cadController?.stop()
        cadController = null
        super.onDestroyView()
    }

    private fun bindToolbar(view: View) {
        view.findViewById<TextView>(R.id.workspace_drawing_name).text =
            siteVm.currentDrawingName().takeIf { it != "—" } ?: getString(R.string.draw_open_drawing)
        view.findViewById<MaterialButton>(R.id.button_workspace_back).setOnClickListener {
            siteVm.leaveCommissioningMode()
            nav().navigate(CnvDestination.DRAWING_DASHBOARD)
        }
    }

    private fun wireViewerTools(view: View) {
        view.findViewById<MaterialButton>(R.id.button_cad_zoom_in).setOnClickListener {
            cadController?.zoomIn()
        }
        view.findViewById<MaterialButton>(R.id.button_cad_zoom_out).setOnClickListener {
            cadController?.zoomOut()
        }
        view.findViewById<MaterialButton>(R.id.button_cad_fit).setOnClickListener {
            cadController?.fitToRoute()
        }
        view.findViewById<MaterialButton>(R.id.button_cad_reset).setOnClickListener {
            cadController?.resetView()
        }
        view.findViewById<MaterialButton>(R.id.button_cad_layer_grid).setOnClickListener {
            cadController?.toggleLayer(CADLayer.GRID)
        }
        view.findViewById<MaterialButton>(R.id.button_cad_layer_node).setOnClickListener {
            cadController?.toggleLayer(CADLayer.NODE)
        }
        view.findViewById<MaterialButton>(R.id.button_cad_layer_branch).setOnClickListener {
            cadController?.toggleLayer(CADLayer.BRANCH)
        }
    }

    private fun wireActionPanel(view: View) {
        view.findViewById<MaterialButton>(R.id.button_ws_origin).setOnClickListener {
            if (isLocked()) return@setOnClickListener toastLocked()
            pickMode = PickMode.ORIGIN
            view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).isVisible = true
            setModeStatus(view, getString(R.string.ws_mode_origin))
        }
        view.findViewById<MaterialButton>(R.id.button_ws_calibration).setOnClickListener {
            if (isLocked()) return@setOnClickListener toastLocked()
            val drawing = FactoryCatalog.get().drawings.current()
            if (drawing?.originSet != true) {
                Toast.makeText(requireContext(), R.string.ws_need_origin, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppNavigator.openCalibration(requireActivity())
            siteVm.markCalibrationReadyForCurrentDrawing()
            siteVm.loadDrawingDashboard()
            Toast.makeText(requireContext(), R.string.ws_calibration_done, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_ws_route).setOnClickListener {
            if (isLocked()) return@setOnClickListener toastLocked()
            if (siteVm.generateRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
                cadController?.start()
                cadController?.fitToRoute()
                siteVm.loadDrawingDashboard()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_ws_zone).setOnClickListener {
            if (isLocked()) return@setOnClickListener toastLocked()
            if (!siteVm.canCreateZone()) {
                Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            promptZoneMethod(view)
        }
        view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).setOnClickListener {
            onConfirmPick(view)
        }
        view.findViewById<MaterialButton>(R.id.button_ws_save).setOnClickListener {
            if (isLocked()) return@setOnClickListener toastLocked()
            saveCommissioning(view)
        }
        view.findViewById<MaterialButton>(R.id.button_ws_route_lock).setOnClickListener {
            if (siteVm.lockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
                refreshLockUi(view)
                siteVm.loadDrawingDashboard()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptZoneMethod(view: View) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_create_zone)
            .setItems(
                arrayOf(
                    getString(R.string.ws_zone_method_cad),
                    getString(R.string.ws_zone_method_drive),
                ),
            ) { _, which ->
                when (which) {
                    0 -> beginZoneCad(view)
                    1 -> beginZoneDrive(view)
                }
            }
            .show()
    }

    private fun beginZoneCad(view: View) {
        if (!zoneEditor.beginCadCreation()) {
            Toast.makeText(requireContext(), R.string.zone_editor_need_context, Toast.LENGTH_SHORT).show()
            return
        }
        highlightSegments.clear()
        pickMode = PickMode.ZONE_CAD_START
        view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).isVisible = true
        setModeStatus(view, getString(R.string.ws_mode_zone_start))
    }

    private fun beginZoneDrive(view: View) {
        if (!zoneEditor.beginDriveRecording()) {
            Toast.makeText(requireContext(), R.string.zone_editor_need_context, Toast.LENGTH_SHORT).show()
            return
        }
        // Structural drive anchors from current selection or default segment.
        val info = cadController?.latestSelectionInfo()
        val segmentId = info?.segmentId?.takeIf { it != "—" } ?: "S-DRIVE"
        zoneEditor.markDriveStart(RouteAnchor(segmentId = segmentId, distanceFromSegmentStartMm = 0f))
        zoneEditor.markDriveEnd(RouteAnchor(segmentId = segmentId, progress = 1f))
        val draft = zoneEditor.draft()
        val route = FactoryCatalog.get().routes.currentRoute()
        if (route != null) {
            highlightSegments.clear()
            highlightSegments.addAll(RouteHighlightHelper.segmentIdsBetween(route, draft.start, draft.end))
        }
        pickMode = PickMode.ZONE_READY_SAVE
        view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).isVisible = false
        setModeStatus(view, getString(R.string.ws_mode_zone_save))
        promptZoneNameColor(view)
    }

    private fun onConfirmPick(view: View) {
        when (pickMode) {
            PickMode.ORIGIN -> {
                val info = cadController?.latestSelectionInfo()
                val x = info?.progress ?: 0.5f
                val y = 0.5f
                if (siteVm.setOriginForCurrentDrawing(x, y)) {
                    Toast.makeText(requireContext(), R.string.setup_origin_set, Toast.LENGTH_SHORT).show()
                    siteVm.loadDrawingDashboard()
                } else {
                    Toast.makeText(requireContext(), R.string.setup_origin_failed, Toast.LENGTH_SHORT).show()
                }
                pickMode = PickMode.IDLE
                view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).isVisible = false
                setModeStatus(view, getString(R.string.ws_mode_idle))
            }
            PickMode.ZONE_CAD_START -> {
                val anchor = selectionToAnchor()
                if (anchor == null || !zoneEditor.setCadStart(anchor)) {
                    Toast.makeText(requireContext(), R.string.ws_pick_required, Toast.LENGTH_SHORT).show()
                    return
                }
                pickMode = PickMode.ZONE_CAD_END
                setModeStatus(view, getString(R.string.ws_mode_zone_end))
            }
            PickMode.ZONE_CAD_END -> {
                val anchor = selectionToAnchor()
                if (anchor == null || !zoneEditor.setCadEnd(anchor)) {
                    Toast.makeText(requireContext(), R.string.ws_pick_required, Toast.LENGTH_SHORT).show()
                    return
                }
                val draft = zoneEditor.draft()
                val route = FactoryCatalog.get().routes.currentRoute()
                highlightSegments.clear()
                if (route != null) {
                    highlightSegments.addAll(
                        RouteHighlightHelper.segmentIdsBetween(route, draft.start, draft.end),
                    )
                }
                pickMode = PickMode.ZONE_READY_SAVE
                view.findViewById<MaterialButton>(R.id.button_ws_confirm_pick).isVisible = false
                setModeStatus(view, getString(R.string.ws_mode_zone_highlight))
                promptZoneNameColor(view)
            }
            else -> Unit
        }
    }

    private fun selectionToAnchor(): RouteAnchor? {
        val info = cadController?.latestSelectionInfo() ?: return null
        val nodeId = info.nodeId.takeIf { it.isNotBlank() && it != "—" }
        val segmentId = info.segmentId.takeIf { it.isNotBlank() && it != "—" }
        return when {
            nodeId != null -> RouteAnchor(nodeId = nodeId)
            segmentId != null -> RouteAnchor(segmentId = segmentId, progress = info.progress)
            else -> null
        }
    }

    private fun promptZoneNameColor(view: View) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.zone_editor_name_hint)
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ws_zone_name_color)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                pendingZoneName = input.text?.toString().orEmpty()
                pendingZoneColor = Color.parseColor("#FF9800")
                setModeStatus(view, getString(R.string.ws_mode_zone_save))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                pickMode = PickMode.IDLE
                highlightSegments.clear()
                zoneEditor.reset()
                setModeStatus(view, getString(R.string.ws_mode_idle))
            }
            .show()
    }

    private fun saveCommissioning(view: View) {
        when (pickMode) {
            PickMode.ZONE_READY_SAVE -> {
                zoneEditor.setName(pendingZoneName)
                zoneEditor.setColor("Orange", pendingZoneColor)
                val saved = zoneEditor.save()
                if (saved == null) {
                    Toast.makeText(requireContext(), R.string.zone_editor_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.zone_editor_saved, Toast.LENGTH_SHORT).show()
                    highlightSegments.clear()
                    pickMode = PickMode.IDLE
                    siteVm.loadDrawingDashboard()
                    setModeStatus(view, getString(R.string.ws_mode_idle))
                }
            }
            else -> {
                // Persist commissioning progress already applied via VM calls.
                siteVm.loadDrawingDashboard()
                Toast.makeText(requireContext(), R.string.ws_save_ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshLockUi(view: View) {
        val locked = isLocked()
        view.findViewById<TextView>(R.id.workspace_lock_status).text =
            if (locked) getString(R.string.setup_locked) else getString(R.string.setup_unlocked)
        val editable = listOf(
            R.id.button_ws_origin,
            R.id.button_ws_calibration,
            R.id.button_ws_route,
            R.id.button_ws_zone,
            R.id.button_ws_save,
            R.id.button_ws_confirm_pick,
            R.id.button_ws_route_lock,
        )
        editable.forEach { id ->
            val btn = view.findViewById<MaterialButton>(id)
            btn.isEnabled = !locked
            btn.alpha = if (locked) 0.4f else 1f
        }
    }

    private fun isLocked(): Boolean =
        FactoryCatalog.get().drawings.current()?.routeLocked == true

    private fun toastLocked() {
        Toast.makeText(requireContext(), R.string.ws_locked_blocked, Toast.LENGTH_SHORT).show()
    }

    private fun setModeStatus(view: View, text: String) {
        view.findViewById<TextView>(R.id.workspace_mode_status).text = text
    }
}
