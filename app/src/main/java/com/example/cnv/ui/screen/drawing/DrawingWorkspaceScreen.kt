package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.commissioning.CommissioningWizardScreen
import com.google.android.material.tabs.TabLayout

/**
 * Drawing Workspace — Inspection / Commissioning / History / Settings (STEP 20-4).
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
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_inspection))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_commissioning))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_history))
        tabs.addTab(tabs.newTab().setText(R.string.ws_tab_settings))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                when (tab.position) {
                    0 -> openInspection()
                    1 -> showCommissioning()
                    2 -> nav().navigate(CnvDestination.INSPECTION_HISTORY)
                    3 -> nav().navigate(CnvDestination.SETTINGS)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                if (tab.position == 1) showCommissioning()
            }
        })

        showCommissioningTab()
    }

    override fun onResume() {
        super.onResume()
        siteVm.loadDrawingDashboard()
        val tabs = view?.findViewById<TabLayout>(R.id.workspace_tabs) ?: return
        // External tabs navigate away; restore Commissioning when returning.
        if (tabs.selectedTabPosition != TAB_COMMISSIONING) {
            showCommissioningTab()
        } else {
            showCommissioning()
        }
    }

    fun showCommissioningTab() {
        val tabs = view?.findViewById<TabLayout>(R.id.workspace_tabs) ?: return
        suppressTabCallback = true
        tabs.getTabAt(TAB_COMMISSIONING)?.select()
        suppressTabCallback = false
        showCommissioning()
    }

    /** Compatibility for callers that previously restored Overview. */
    fun showOverviewTab() = showCommissioningTab()

    private fun contentSlot(): FrameLayout =
        requireView().findViewById(R.id.workspace_content)

    private fun clearChildFragments() {
        val existing = childFragmentManager.findFragmentByTag(TAG_COMMISSIONING)
        if (existing != null) {
            childFragmentManager.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }
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
            showCommissioningTab()
            return
        }
        siteVm.leaveCommissioningMode()
        if (com.example.cnv.factory.context.CurrentContext.get().zoneId != null) {
            nav().navigate(CnvDestination.INSPECTION)
        } else {
            nav().navigate(CnvDestination.ZONE_LIST)
        }
    }

    companion object {
        private const val TAG_COMMISSIONING = "workspace_commissioning"
        private const val TAB_COMMISSIONING = 1
    }
}
