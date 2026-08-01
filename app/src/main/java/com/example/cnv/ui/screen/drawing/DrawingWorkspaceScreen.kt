package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.commissioning.CommissioningWizardProgress
import com.example.cnv.ui.screen.commissioning.CommissioningWizardScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drawing Workspace — single hub for Overview / Commissioning / Inspection / HeatMap / History.
 * Replaces Drawing List, Drawing Dashboard, and Open Drawing.
 */
class DrawingWorkspaceScreen : BaseScreen() {

    private var suppressTabCallback = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_drawing_workspace, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.bootstrap()
        siteVm.loadDrawingDashboard()

        val drawing = FactoryCatalog.get().drawings.current()
        view.findViewById<TextView>(R.id.workspace_title).text =
            drawing?.name ?: getString(R.string.ws_workspace_title)
        view.findViewById<TextView>(R.id.workspace_context).text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
            siteVm.currentFloorName(),
        )

        val tabs = view.findViewById<TabLayout>(R.id.workspace_tabs)
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_overview))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_commissioning))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_inspection))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_heatmap))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_history))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                when (tab.position) {
                    0 -> showOverview()
                    1 -> showCommissioning()
                    2 -> openInspection()
                    3 -> nav().navigate(CnvDestination.HEATMAP_VIEWER)
                    4 -> nav().navigate(CnvDestination.INSPECTION_HISTORY)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                if (tab.position == 0) showOverview()
            }
        })

        showOverview()
    }

    override fun onResume() {
        super.onResume()
        siteVm.loadDrawingDashboard()
        val tabs = view?.findViewById<TabLayout>(R.id.workspace_tabs) ?: return
        // External tabs navigate away; restore Overview when returning.
        if (tabs.selectedTabPosition >= 2) {
            showOverviewTab()
        } else if (tabs.selectedTabPosition == 0) {
            showOverview()
        }
    }

    fun showOverviewTab() {
        val tabs = view?.findViewById<TabLayout>(R.id.workspace_tabs) ?: return
        suppressTabCallback = true
        tabs.getTabAt(0)?.select()
        suppressTabCallback = false
        showOverview()
    }

    private fun contentSlot(): FrameLayout =
        requireView().findViewById(R.id.workspace_content)

    private fun clearChildFragments() {
        val existing = childFragmentManager.findFragmentByTag(TAG_COMMISSIONING)
        if (existing != null) {
            childFragmentManager.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }
    }

    private fun showOverview() {
        clearChildFragments()
        val slot = contentSlot()
        slot.removeAllViews()
        val content = layoutInflater.inflate(R.layout.include_workspace_overview, slot, false)
        slot.addView(content)
        bindOverview(content)
    }

    private fun showCommissioning() {
        clearChildFragments()
        val slot = contentSlot()
        slot.removeAllViews()
        childFragmentManager.beginTransaction()
            .replace(R.id.workspace_content, CommissioningWizardScreen(), TAG_COMMISSIONING)
            .commitNowAllowingStateLoss()
    }

    private fun openInspection() {
        val reason = DrawingUiStatus.inspectionBlockReason(requireContext())
        if (reason != null || !DrawingUiStatus.canStartInspection()) {
            Toast.makeText(
                requireContext(),
                reason ?: getString(R.string.draw_inspection_not_ready),
                Toast.LENGTH_SHORT,
            ).show()
            showOverviewTab()
            return
        }
        siteVm.leaveCommissioningMode()
        if (com.example.cnv.factory.context.CurrentContext.get().zoneId != null) {
            nav().navigate(CnvDestination.INSPECTION)
        } else {
            nav().navigate(CnvDestination.ZONE_LIST)
        }
    }

    private fun bindOverview(content: View) {
        val snap = CommissioningWizardProgress.snapshot()
        val drawing = snap.drawing
        val state = DrawingState.resolveFromSnapshot(snap)

        content.findViewById<TextView>(R.id.overview_drawing_name).text =
            drawing?.name ?: "—"

        val headline = content.findViewById<TextView>(R.id.overview_state_headline)
        headline.text = state.label(requireContext())
        headline.setTextColor(state.color(requireContext()))

        val highlights = DrawingUiStatus.overviewHighlights(requireContext(), snap)
        content.findViewById<TextView>(R.id.overview_state_detail).text =
            highlights.drop(1).joinToString("\n").ifBlank {
                getString(R.string.draw_overview_go_commissioning)
            }

        content.findViewById<TextView>(R.id.overview_wizard_progress).text =
            getString(
                R.string.wiz_dashboard_progress,
                snap.completedCount,
                CommissioningWizardProgress.TOTAL_STEPS,
            )

        val lastResult = drawing?.let { FactoryCatalog.get().inspections.latestForDrawing(it.id) }
        val lastDate = lastResult?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it.endTimeMs))
        } ?: "—"
        content.findViewById<TextView>(R.id.overview_recent_inspection).text =
            getString(R.string.draw_card_recent_inspection, lastDate)

        val updated = drawing?.updatedAtMs?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it))
        } ?: "—"
        content.findViewById<TextView>(R.id.overview_updated).text =
            getString(R.string.draw_card_updated, updated)

        val statusContainer = content.findViewById<LinearLayout>(R.id.overview_status_container)
        statusContainer.removeAllViews()
        val inspectionReady = state.canStartInspection()
        val rows = listOf(
            getString(R.string.op_status_dwg) to DrawingUiStatus.dwgLabel(requireContext(), snap.dwgOk),
            getString(R.string.setup_origin) to
                if (snap.originOk) getString(R.string.draw_status_origin_set)
                else getString(R.string.draw_status_missing),
            getString(R.string.op_status_calibration) to
                DrawingUiStatus.calibrationLabel(requireContext(), snap.calibrationOk),
            getString(R.string.op_status_route) to
                DrawingUiStatus.routeLabel(requireContext(), snap.routeOk),
            getString(R.string.op_zone_list_section) to
                DrawingUiStatus.zoneLabel(requireContext(), snap.zoneCount),
            getString(R.string.setup_route_lock) to
                DrawingUiStatus.routeLockLabel(requireContext(), snap.routeLocked),
        )
        rows.forEach { (label, value) ->
            val ok = !value.equals(getString(R.string.draw_status_missing), ignoreCase = true) &&
                !value.equals(getString(R.string.draw_card_route_unlocked), ignoreCase = true)
            statusContainer.addView(
                UiComponents.inflateStatusCard(
                    statusContainer,
                    label,
                    value,
                    UiComponents.statusColor(requireContext(), if (ok) "OK" else "MISSING"),
                ),
            )
        }

        val inspectionBtn = content.findViewById<MaterialButton>(R.id.button_overview_inspection)
        val reasonView = content.findViewById<TextView>(R.id.overview_inspection_reason)
        inspectionBtn.isEnabled = inspectionReady
        inspectionBtn.alpha = if (inspectionReady) 1f else 0.45f
        val blockReason = DrawingUiStatus.inspectionBlockReason(requireContext())
        if (blockReason != null) {
            reasonView.visibility = View.VISIBLE
            reasonView.text = blockReason
        } else {
            reasonView.visibility = View.GONE
        }
        inspectionBtn.setOnClickListener { openInspection() }

        content.findViewById<MaterialButton>(R.id.button_overview_delete).setOnClickListener {
            val id = drawing?.id ?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setup_delete_drawing)
                .setMessage(R.string.setup_delete_drawing_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (siteVm.deleteDrawing(id)) {
                        nav().navigateClearTo(CnvDestination.FLOOR_SELECT)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        content.findViewById<MaterialButton>(R.id.button_overview_back).setOnClickListener {
            nav().navigateClearTo(CnvDestination.FLOOR_SELECT)
        }
    }

    companion object {
        private const val TAG_COMMISSIONING = "workspace_commissioning"
    }
}
