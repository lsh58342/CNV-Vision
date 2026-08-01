package com.example.cnv.ui.screen.floor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.vm.SiteNavigationViewModel
import com.google.android.material.button.MaterialButton

/** Floor Dashboard — floors under Building + DWG / Route / Zone commissioning actions. */
class FloorScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var emptyView: View
    private var selectedFloorId: String? = null

    private val pickDwg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.setup_dwg_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (siteVm.registerDwgForCurrentFloor()) {
            Toast.makeText(requireContext(), R.string.setup_dwg_registered, Toast.LENGTH_SHORT).show()
            refreshDashboard()
        } else {
            Toast.makeText(requireContext(), R.string.setup_dwg_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_floor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.floor_list_container)
        statusContainer = view.findViewById(R.id.floor_status_container)
        selectedFloorId = CurrentContext.get().floorId

        view.findViewById<TextView>(R.id.floor_context).text = getString(
            R.string.op_context_factory_building,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
        )

        val statusHeader = view.findViewById<FrameLayout>(R.id.floor_status_header_slot)
        statusHeader.addView(
            UiComponents.inflateSectionHeader(statusHeader, getString(R.string.op_status_section)),
        )
        val listHeader = view.findViewById<FrameLayout>(R.id.floor_list_header_slot)
        listHeader.addView(
            UiComponents.inflateSectionHeader(listHeader, getString(R.string.op_floor_list)),
        )

        val emptySlot = view.findViewById<FrameLayout>(R.id.floor_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.setup_empty_floors))
        emptySlot.addView(emptyView)

        view.findViewById<MaterialButton>(R.id.button_add_floor).setOnClickListener { promptAddFloor() }
        view.findViewById<MaterialButton>(R.id.button_register_dwg).setOnClickListener {
            if (CurrentContext.get().floorId == null) {
                Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickDwg.launch("*/*")
        }
        view.findViewById<MaterialButton>(R.id.button_generate_route).setOnClickListener {
            if (siteVm.generateRouteForCurrentFloor()) {
                Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
                refreshDashboard()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_create_zone).setOnClickListener {
            if (!siteVm.canCreateZone()) {
                Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppNavigator.openZoneEditor(requireActivity())) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_route_lock).setOnClickListener {
            if (siteVm.lockRouteForCurrentFloor()) {
                Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
                refreshDashboard()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_zones).setOnClickListener {
            if (CurrentContext.get().floorId == null) {
                Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.ZONE_LIST)
        }
        view.findViewById<MaterialButton>(R.id.button_floor_back).setOnClickListener {
            nav().navigateBack()
        }

        siteVm.floors.observe(viewLifecycleOwner) { renderFloorList(it) }
        siteVm.floorDashboard.observe(viewLifecycleOwner) { renderStatus(it) }
        siteVm.loadFloors()
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        siteVm.loadFloors()
    }

    private fun refreshDashboard() {
        if (CurrentContext.get().floorId != null) {
            siteVm.loadFloorDashboard()
        } else {
            statusContainer.removeAllViews()
        }
    }

    private fun promptAddFloor() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.setup_floor_name_hint)
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_add_floor)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val created = siteVm.addFloor(input.text?.toString().orEmpty())
                if (created == null) {
                    Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    selectedFloorId = created.id
                    siteVm.selectFloor(created.id)
                    refreshDashboard()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderFloorList(items: List<com.example.cnv.factory.model.Floor>) {
        UiComponents.clearChildren(listContainer)
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = listContainer,
                    title = item.name,
                    subtitle = getString(R.string.op_floor_subtitle),
                    selected = item.id == selectedFloorId,
                    onClick = {
                        selectedFloorId = item.id
                        siteVm.selectFloor(item.id)
                        refreshDashboard()
                        renderFloorList(items)
                    },
                ),
            )
        }
    }

    private fun renderStatus(dash: SiteNavigationViewModel.FloorDashboardUi?) {
        statusContainer.removeAllViews()
        if (dash == null) {
            statusContainer.addView(
                UiComponents.inflateEmptyView(statusContainer, getString(R.string.setup_select_floor_for_status)),
            )
            return
        }
        view?.findViewById<TextView>(R.id.floor_context)?.text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            dash.buildingName,
            dash.floorName,
        )
        fun statusLabel(ok: Boolean) =
            if (ok) getString(R.string.status_ok_label) else getString(R.string.status_missing_label)
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_dwg),
                statusLabel(dash.dwgReady),
                UiComponents.statusColor(requireContext(), statusLabel(dash.dwgReady)),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_route),
                statusLabel(dash.routeReady),
                UiComponents.statusColor(requireContext(), statusLabel(dash.routeReady)),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.op_status_calibration),
                statusLabel(dash.calibrationReady),
                UiComponents.statusColor(requireContext(), statusLabel(dash.calibrationReady)),
            ),
        )
        val lockLabel = if (dash.routeLocked) {
            getString(R.string.setup_locked)
        } else {
            getString(R.string.setup_unlocked)
        }
        statusContainer.addView(
            UiComponents.inflateStatusCard(
                statusContainer,
                getString(R.string.setup_route_lock),
                lockLabel,
                UiComponents.statusColor(requireContext(), if (dash.routeLocked) "OK" else "MISSING"),
            ),
        )
        statusContainer.addView(
            UiComponents.inflateInfoCard(
                statusContainer,
                getString(R.string.op_zone_list_section),
                if (dash.zones.isEmpty()) {
                    getString(R.string.setup_empty_zones)
                } else {
                    dash.zones.joinToString { it.name }
                },
            ),
        )
        statusContainer.addView(
            UiComponents.inflateInfoCard(
                statusContainer,
                getString(R.string.op_history_title),
                getString(R.string.op_history_count, dash.historyCount),
            ),
        )
    }
}
