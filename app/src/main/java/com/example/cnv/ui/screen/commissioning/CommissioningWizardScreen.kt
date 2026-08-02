package com.example.cnv.ui.screen.commissioning

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADLayer
import com.example.cnv.cad.CADView
import com.example.cnv.dwg.DxfImportStatus
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper
import com.example.cnv.zone.editor.ZoneEditorController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * Drawing Workspace Commissioning tab — 6 gated steps (Origin → Route Lock).
 * No Inspection / HeatMap / History / Camera / Developer HUD.
 */
class CommissioningWizardScreen : BaseScreen() {

    private var currentStep = CommissioningWizardProgress.Step.ORIGIN
    private var workspace: View? = null
    private var cadController: CADController? = null
    private val zoneEditor = ZoneEditorController()
    private val highlightSegments = linkedSetOf<String>()
    private var zonePickPhase: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_commissioning_wizard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!siteVm.enterCommissioningMode()) {
            return
        }
        val snap = CommissioningWizardProgress.snapshot()
        currentStep = CommissioningWizardProgress.firstIncompleteStep(snap)

        view.findViewById<MaterialButton>(R.id.button_wiz_prev).setOnClickListener { goPrev() }
        view.findViewById<MaterialButton>(R.id.button_wiz_next).setOnClickListener { goNext() }
        view.findViewById<MaterialButton>(R.id.button_wiz_finish).setOnClickListener { finishWizard() }

        showStep(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            if (currentStep == CommissioningWizardProgress.Step.ORIGIN ||
                currentStep == CommissioningWizardProgress.Step.ZONE
            ) {
                cadController?.start()
            }
            refresh()
        }
    }

    override fun onPause() {
        cadController?.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        cadController?.stop()
        cadController = null
        workspace = null
        super.onDestroyView()
    }

    private fun refresh() {
        val root = view ?: return
        val snap = CommissioningWizardProgress.snapshot()
        root.findViewById<TextView>(R.id.wiz_progress).text =
            getString(
                R.string.wiz_progress_format,
                currentStep.index,
                CommissioningWizardProgress.TOTAL_STEPS,
            )
        root.findViewById<ProgressBar>(R.id.wiz_progress_bar).apply {
            max = CommissioningWizardProgress.TOTAL_STEPS
            progress = currentStep.index
        }
        updateNavButtons(root, snap)
        if (currentStep == CommissioningWizardProgress.Step.VALIDATION ||
            currentStep == CommissioningWizardProgress.Step.ROUTE_LOCK
        ) {
            bindValidationList(snap)
            updateWorkspaceStatus(snap)
        }
    }

    private fun showStep(root: View) {
        cadController?.stop()
        cadController = null
        zonePickPhase = 0
        highlightSegments.clear()

        val snap = CommissioningWizardProgress.snapshot()
        root.findViewById<TextView>(R.id.wiz_step_title).text = getString(currentStep.titleRes)
        root.findViewById<TextView>(R.id.wiz_step_hint).text = getString(hintRes(currentStep))
        root.findViewById<TextView>(R.id.wiz_progress).text =
            getString(
                R.string.wiz_progress_format,
                currentStep.index,
                CommissioningWizardProgress.TOTAL_STEPS,
            )
        root.findViewById<ProgressBar>(R.id.wiz_progress_bar).apply {
            max = CommissioningWizardProgress.TOTAL_STEPS
            progress = currentStep.index
        }

        val slot = root.findViewById<FrameLayout>(R.id.wiz_workspace)
        slot.removeAllViews()
        val content = layoutInflater.inflate(R.layout.include_wizard_step_workspace, slot, false)
        slot.addView(content)
        workspace = content
        bindStepWorkspace(content, snap)
        updateNavButtons(root, snap)
    }

    private fun bindStepWorkspace(content: View, snap: CommissioningWizardProgress.Snapshot) {
        val input = content.findViewById<TextInputEditText>(R.id.wiz_input)
        val secondaryLayout = content.findViewById<View>(R.id.wiz_input_secondary_layout)
        val action = content.findViewById<MaterialButton>(R.id.button_wiz_action)
        val action2 = content.findViewById<MaterialButton>(R.id.button_wiz_action_secondary)
        val status = content.findViewById<TextView>(R.id.wiz_workspace_status)
        val cadSlot = content.findViewById<FrameLayout>(R.id.wiz_cad_slot)
        val validationList = content.findViewById<LinearLayout>(R.id.wiz_validation_list)

        secondaryLayout.isVisible = false
        action2.isVisible = false
        cadSlot.isVisible = false
        validationList.isVisible = false
        input.isVisible = false
        action.isVisible = true

        when (currentStep) {
            CommissioningWizardProgress.Step.ORIGIN -> {
                cadSlot.isVisible = true
                action.setText(R.string.ws_confirm_pick)
                status.text = if (snap.originOk) {
                    getString(R.string.wiz_status_origin_ok)
                } else {
                    getString(R.string.ws_mode_origin)
                }
                if (!snap.dwgOk) {
                    Toast.makeText(requireContext(), R.string.wiz_fail_dwg, Toast.LENGTH_SHORT).show()
                } else if (!siteVm.ensureRoutePreviewForCurrentDrawing()) {
                    Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_LONG).show()
                }
                attachCad(content, originPickEnabled = !snap.originOk)
                applyOriginMarkerFromDrawing()
                action.isEnabled = !snap.originOk
                action.alpha = if (action.isEnabled) 1f else 0.45f
                action.setOnClickListener {
                    if (FactoryCatalog.get().drawings.current()?.routeLocked == true) {
                        Toast.makeText(requireContext(), R.string.ws_locked_blocked, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (!snap.dwgOk) {
                        Toast.makeText(requireContext(), R.string.wiz_fail_dwg, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val info = cadController?.latestSelectionInfo()
                    val progress = info?.progress?.takeIf { info.segmentId != "—" } ?: 0f
                    commitOriginPick(progress)
                }
            }
            CommissioningWizardProgress.Step.CALIBRATION -> {
                action.setText(R.string.comm_step_calibration)
                status.text = if (snap.calibrationOk) {
                    getString(R.string.wiz_status_cal_ok)
                } else {
                    getString(R.string.wiz_status_need_cal)
                }
                action.setOnClickListener {
                    if (!snap.originOk) {
                        Toast.makeText(requireContext(), R.string.ws_need_origin, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    // Open Calibration; Drawing ready flag is set only after Finish succeeds.
                    AppNavigator.openCalibration(requireActivity())
                }
            }
            CommissioningWizardProgress.Step.ROUTE -> {
                action.setText(R.string.setup_generate_route)
                action2.isVisible = true
                action2.setText(R.string.wiz_select_conveyor_layer)
                validationList.isVisible = true
                bindConveyorLayerList(validationList)
                val selected = siteVm.currentConveyorLayerName()
                status.text = if (snap.routeOk) {
                    getString(R.string.wiz_status_route_ok)
                } else {
                    getString(R.string.wiz_status_need_route_layer, selected)
                }
                action2.setOnClickListener { promptConveyorLayerSelection() }
                action.setOnClickListener {
                    if (siteVm.generateRouteForCurrentDrawing()) {
                        Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
                        refresh()
                        showStep(requireView())
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.setup_route_failed_layer, siteVm.currentConveyorLayerName()),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            CommissioningWizardProgress.Step.ZONE -> {
                cadSlot.isVisible = true
                action.setText(R.string.ws_zone_method_cad)
                action2.isVisible = true
                action2.setText(R.string.ws_zone_method_drive)
                status.text = if (snap.zoneOk) {
                    getString(R.string.wiz_status_zone_ok, snap.zoneCount)
                } else {
                    getString(R.string.wiz_status_need_zone)
                }
                attachCad(content)
                action.setOnClickListener { beginZoneCad(content) }
                action2.setOnClickListener { beginZoneDrive(content) }
            }
            CommissioningWizardProgress.Step.VALIDATION -> {
                action.isVisible = false
                validationList.isVisible = true
                bindValidationList(snap)
                status.text = if (snap.validationOk) {
                    getString(R.string.wiz_validation_pass)
                } else {
                    getString(R.string.wiz_validation_fail)
                }
            }
            CommissioningWizardProgress.Step.ROUTE_LOCK -> {
                action.setText(R.string.setup_route_lock)
                validationList.isVisible = true
                bindValidationList(snap)
                status.text = if (snap.routeLocked) {
                    getString(R.string.setup_route_locked)
                } else if (snap.validationOk) {
                    getString(R.string.wiz_lock_ready)
                } else {
                    getString(R.string.wiz_lock_blocked)
                }
                action.isEnabled = CommissioningWizardProgress.canLockRoute(snap)
                action.alpha = if (action.isEnabled) 1f else 0.45f
                action.setOnClickListener {
                    val latest = CommissioningWizardProgress.snapshot()
                    if (!CommissioningWizardProgress.canLockRoute(latest)) {
                        Toast.makeText(requireContext(), R.string.wiz_lock_blocked, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (siteVm.lockRouteForCurrentDrawing()) {
                        Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
                        refresh()
                        showStep(requireView())
                    } else {
                        Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun attachCad(content: View, originPickEnabled: Boolean = false) {
        cadController?.stop()
        cadController?.setOnOriginTapListener(null)
        cadController?.setOnTapSelectionListener(null)
        val cadView = content.findViewById<CADView>(R.id.wiz_cad_view)
        cadController = CADController(
            routeRepository = FactoryCatalog.get().routes.underlying(),
            cadView = cadView,
            mapperProvider = { siteVm.currentRouteMapper() },
            debugHud = null,
            errorSegmentIdsProvider = { highlightSegments.toSet() },
        )
        cadController?.setLayerEnabled(CADLayer.DEBUG, false)
        cadController?.start()
        cadController?.fitToRoute()
        if (originPickEnabled) {
            cadController?.setOnOriginTapListener { viewX, viewY ->
                val pick = cadController?.pickRouteStartPoint(viewX, viewY)
                if (pick == null) {
                    Toast.makeText(requireContext(), R.string.ws_pick_required, Toast.LENGTH_SHORT).show()
                    return@setOnOriginTapListener
                }
                cadController?.setOriginWorldMarker(pick.world.x, pick.world.y)
                commitOriginPick(pick.progressOnStartSegment)
            }
        }
    }

    private fun commitOriginPick(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (siteVm.setOriginForCurrentDrawing(p, p)) {
            cadController?.originWorldFromProgress(p)?.let { (x, y) ->
                cadController?.setOriginWorldMarker(x, y)
            }
            Toast.makeText(requireContext(), R.string.setup_origin_set, Toast.LENGTH_SHORT).show()
            refresh()
            view?.let { showStep(it) }
        } else {
            Toast.makeText(requireContext(), R.string.setup_origin_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyOriginMarkerFromDrawing() {
        val drawing = FactoryCatalog.get().drawings.current() ?: return
        if (!drawing.originSet) return
        val progress = (drawing.originX ?: 0f).coerceIn(0f, 1f)
        cadController?.originWorldFromProgress(progress)?.let { (x, y) ->
            cadController?.setOriginWorldMarker(x, y)
        }
    }

    private fun beginZoneCad(content: View) {
        if (!siteVm.canCreateZone() || !zoneEditor.beginCadCreation()) {
            Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
            return
        }
        zonePickPhase = 1
        highlightSegments.clear()
        content.findViewById<TextView>(R.id.wiz_workspace_status).text =
            getString(R.string.ws_mode_zone_start)
        content.findViewById<MaterialButton>(R.id.button_wiz_action).apply {
            setText(R.string.ws_confirm_pick)
            setOnClickListener { confirmZonePick(content) }
        }
    }

    private fun beginZoneDrive(content: View) {
        if (!siteVm.canCreateZone() || !zoneEditor.beginDriveRecording()) {
            Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
            return
        }
        val info = cadController?.latestSelectionInfo()
        val segmentId = info?.segmentId?.takeIf { it != "—" } ?: "S-DRIVE"
        zoneEditor.markDriveStart(RouteAnchor(segmentId = segmentId, distanceFromSegmentStartMm = 0f))
        zoneEditor.markDriveEnd(RouteAnchor(segmentId = segmentId, progress = 1f))
        applyHighlightFromDraft()
        promptAndSaveZone(content)
    }

    private fun confirmZonePick(content: View) {
        val info = cadController?.latestSelectionInfo()
        val nodeId = info?.nodeId?.takeIf { it != "—" }
        val segmentId = info?.segmentId?.takeIf { it != "—" }
        val anchor = when {
            nodeId != null -> RouteAnchor(nodeId = nodeId)
            segmentId != null -> RouteAnchor(segmentId = segmentId, progress = info?.progress ?: 0f)
            else -> null
        }
        if (anchor == null) {
            Toast.makeText(requireContext(), R.string.ws_pick_required, Toast.LENGTH_SHORT).show()
            return
        }
        when (zonePickPhase) {
            1 -> {
                if (!zoneEditor.setCadStart(anchor)) return
                zonePickPhase = 2
                content.findViewById<TextView>(R.id.wiz_workspace_status).text =
                    getString(R.string.ws_mode_zone_end)
            }
            2 -> {
                if (!zoneEditor.setCadEnd(anchor)) return
                applyHighlightFromDraft()
                content.findViewById<TextView>(R.id.wiz_workspace_status).text =
                    getString(R.string.ws_mode_zone_highlight)
                promptAndSaveZone(content)
            }
        }
    }

    private fun applyHighlightFromDraft() {
        val draft = zoneEditor.draft()
        val route = FactoryCatalog.get().routes.currentRoute() ?: return
        highlightSegments.clear()
        highlightSegments.addAll(RouteHighlightHelper.segmentIdsBetween(route, draft.start, draft.end))
    }

    private fun promptAndSaveZone(content: View) {
        val draft = zoneEditor.draft()
        if (!CommissioningWizardProgress.zoneOnRoute(draft.start, draft.end)) {
            Toast.makeText(requireContext(), R.string.wiz_zone_not_on_route, Toast.LENGTH_SHORT).show()
            return
        }
        val existing = FactoryCatalog.get().zones.listForCurrentDrawing()
        if (CommissioningWizardProgress.zonesOverlap(FactoryCatalog.get(), draft.start, draft.end, existing)) {
            Toast.makeText(requireContext(), R.string.wiz_zone_overlap, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.zone_editor_name_hint)
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ws_zone_name_color)
            .setView(input)
            .setPositiveButton(R.string.ws_save) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (!CommissioningWizardProgress.isZoneNameUnique(name, existing)) {
                    Toast.makeText(requireContext(), R.string.wiz_zone_name_dup, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                zoneEditor.setName(name)
                zoneEditor.setColor("Orange", Color.parseColor("#FF9800"))
                val saved = zoneEditor.save()
                if (saved == null) {
                    Toast.makeText(requireContext(), R.string.zone_editor_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.zone_editor_saved, Toast.LENGTH_SHORT).show()
                    highlightSegments.clear()
                    zonePickPhase = 0
                    siteVm.loadZones()
                    refresh()
                    showStep(requireView())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindConveyorLayerList(list: LinearLayout) {
        UiComponents.clearChildren(list)
        val report = siteVm.latestCadImportReport()
            ?: siteVm.runCadImportDiagnostics()
        if (report != null) {
            val s = report.summary
            val statusLabel = when (report.status) {
                DxfImportStatus.SUCCESS -> "Import Success"
                DxfImportStatus.WARNING -> "Import Success (Warnings)"
                DxfImportStatus.ERROR -> "Import Error"
            }
            list.addView(
                UiComponents.inflateInfoCard(
                    list,
                    getString(R.string.dxf_import_summary_title),
                    "$statusLabel\n" +
                        "${s.fileName} · ${s.dxfVersion}\n" +
                        "Layers ${s.layerCount} · Geometry ${s.geometryCount} · " +
                        "Route ${s.routeCandidateCount}\n" +
                        "Layer: ${s.selectedConveyorLayer}",
                ),
            )
            report.validation.warnings.forEach { msg ->
                list.addView(
                    UiComponents.inflateStatusCard(
                        list,
                        getString(R.string.dxf_import_validation_title),
                        "Warning: $msg",
                        UiComponents.statusColor(requireContext(), "MISSING"),
                    ),
                )
            }
            report.validation.errors.forEach { msg ->
                list.addView(
                    UiComponents.inflateStatusCard(
                        list,
                        getString(R.string.dxf_import_validation_title),
                        "Error: $msg",
                        UiComponents.statusColor(requireContext(), "MISSING"),
                    ),
                )
            }
            report.validation.guidance.forEach { msg ->
                list.addView(
                    UiComponents.inflateInfoCard(list, "Next", msg),
                )
            }
        }
        val layers = siteVm.listCadLayersForCurrentDrawing()
        val selected = siteVm.currentConveyorLayerName()
        list.addView(
            UiComponents.inflateInfoCard(
                list,
                getString(R.string.wiz_layer_list_title),
                if (layers.isEmpty()) {
                    getString(R.string.wiz_layer_list_empty)
                } else {
                    getString(R.string.wiz_layer_list_selected, selected, layers.size)
                },
            ),
        )
        layers.forEach { name ->
            val mark = if (name == selected) "●" else "○"
            list.addView(
                UiComponents.inflateStatusCard(
                    list,
                    "$mark $name",
                    if (name == selected) {
                        getString(R.string.wiz_layer_selected)
                    } else {
                        getString(R.string.wiz_layer_available)
                    },
                    UiComponents.statusColor(
                        requireContext(),
                        if (name == selected) "OK" else "MISSING",
                    ),
                ),
            )
        }
    }

    private fun promptConveyorLayerSelection() {
        val layers = siteVm.listCadLayersForCurrentDrawing()
        if (layers.isEmpty()) {
            Toast.makeText(requireContext(), R.string.wiz_layer_list_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = siteVm.currentConveyorLayerName()
        val checked = layers.indexOfFirst { it.equals(selected, ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wiz_select_conveyor_layer)
            .setSingleChoiceItems(layers.toTypedArray(), checked) { dialog, which ->
                val name = layers[which]
                if (siteVm.setConveyorLayerForCurrentDrawing(name)) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.wiz_layer_set_ok, name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    refresh()
                    view?.let { showStep(it) }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindValidationList(snap: CommissioningWizardProgress.Snapshot) {
        val list = workspace?.findViewById<LinearLayout>(R.id.wiz_validation_list) ?: return
        UiComponents.clearChildren(list)
        val checks = listOf(
            getString(R.string.op_status_dwg) to snap.dwgOk,
            getString(R.string.setup_origin) to snap.originOk,
            getString(R.string.op_status_calibration) to snap.calibrationOk,
            getString(R.string.op_status_route) to snap.routeOk,
            getString(R.string.op_zone_list_section) to snap.zoneOk,
            getString(R.string.setup_route_lock) to snap.routeLocked,
        )
        checks.forEach { (label, ok) ->
            val value = if (ok) getString(R.string.status_ok_label) else getString(R.string.status_missing_label)
            list.addView(
                UiComponents.inflateStatusCard(
                    list, label, value,
                    UiComponents.statusColor(requireContext(), if (ok) "OK" else "MISSING"),
                ),
            )
        }
        if (!snap.validationOk) {
            CommissioningWizardProgress.validationFailures(requireContext(), snap).forEach { msg ->
                list.addView(UiComponents.inflateInfoCard(list, getString(R.string.wiz_validation_fail), msg))
            }
        }
    }

    private fun updateWorkspaceStatus(snap: CommissioningWizardProgress.Snapshot) {
        val status = workspace?.findViewById<TextView>(R.id.wiz_workspace_status) ?: return
        when (currentStep) {
            CommissioningWizardProgress.Step.VALIDATION -> {
                status.text = if (snap.validationOk) {
                    getString(R.string.wiz_validation_pass)
                } else {
                    getString(R.string.wiz_validation_fail)
                }
            }
            CommissioningWizardProgress.Step.ROUTE_LOCK -> {
                status.text = if (snap.routeLocked) {
                    getString(R.string.setup_route_locked)
                } else if (snap.validationOk) {
                    getString(R.string.wiz_lock_ready)
                } else {
                    getString(R.string.wiz_lock_blocked)
                }
                workspace?.findViewById<MaterialButton>(R.id.button_wiz_action)?.let { btn ->
                    btn.isEnabled = CommissioningWizardProgress.canLockRoute(snap)
                    btn.alpha = if (btn.isEnabled) 1f else 0.45f
                }
            }
            else -> Unit
        }
    }

    private fun updateNavButtons(root: View, snap: CommissioningWizardProgress.Snapshot) {
        val prev = root.findViewById<MaterialButton>(R.id.button_wiz_prev)
        val next = root.findViewById<MaterialButton>(R.id.button_wiz_next)
        val finish = root.findViewById<MaterialButton>(R.id.button_wiz_finish)
        prev.isEnabled = currentStep.index > 1
        val canNext = CommissioningWizardProgress.canAdvance(currentStep, snap) &&
            currentStep != CommissioningWizardProgress.Step.ROUTE_LOCK
        next.isEnabled = canNext
        next.alpha = if (canNext) 1f else 0.45f
        val showFinish = currentStep == CommissioningWizardProgress.Step.ROUTE_LOCK && snap.routeLocked
        finish.isVisible = showFinish
        next.isVisible = !showFinish
    }

    private fun goPrev() {
        if (currentStep.index <= 1) return
        currentStep = CommissioningWizardProgress.Step.fromIndex(currentStep.index - 1)
        view?.let { showStep(it) }
    }

    private fun goNext() {
        val snap = CommissioningWizardProgress.snapshot()
        if (!CommissioningWizardProgress.canAdvance(currentStep, snap)) {
            Toast.makeText(requireContext(), R.string.wiz_step_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        if (currentStep.index >= CommissioningWizardProgress.TOTAL_STEPS) return
        currentStep = CommissioningWizardProgress.Step.fromIndex(currentStep.index + 1)
        view?.let { showStep(it) }
    }

    private fun finishWizard() {
        val snap = CommissioningWizardProgress.snapshot()
        if (!CommissioningWizardProgress.canStartInspection(snap)) {
            Toast.makeText(requireContext(), R.string.wiz_finish_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        siteVm.leaveCommissioningMode()
        // Stay in Drawing Workspace (parent tab host); just toast.
        Toast.makeText(requireContext(), R.string.wiz_commissioning_complete, Toast.LENGTH_SHORT).show()
        (parentFragment as? com.example.cnv.ui.screen.drawing.DrawingWorkspaceScreen)
            ?.showOverviewTab()
            ?: nav().navigateClearTo(CnvDestination.DRAWING_WORKSPACE)
    }

    private fun hintRes(step: CommissioningWizardProgress.Step): Int = when (step) {
        CommissioningWizardProgress.Step.ORIGIN -> R.string.wiz_hint_origin
        CommissioningWizardProgress.Step.CALIBRATION -> R.string.wiz_hint_calibration
        CommissioningWizardProgress.Step.ROUTE -> R.string.wiz_hint_route
        CommissioningWizardProgress.Step.ZONE -> R.string.wiz_hint_zone
        CommissioningWizardProgress.Step.VALIDATION -> R.string.wiz_hint_validation
        CommissioningWizardProgress.Step.ROUTE_LOCK -> R.string.wiz_hint_route_lock
    }
}
