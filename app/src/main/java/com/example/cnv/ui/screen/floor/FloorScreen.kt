package com.example.cnv.ui.screen.floor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.dummy.OperationDummyData
import com.example.cnv.ui.screen.dummy.OperationUiSelection

/** Floor Screen — Operation UI (dummy data, Phase 2). */
class FloorScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_floor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.floor_list_container)

        val factory = OperationUiSelection.selectedFactory()?.name ?: "—"
        val building = OperationUiSelection.selectedBuilding()?.name ?: "—"
        view.findViewById<TextView>(R.id.floor_context).text =
            getString(R.string.op_context_factory_building, factory, building)

        val headerSlot = view.findViewById<FrameLayout>(R.id.floor_list_header_slot)
        headerSlot.addView(UiComponents.inflateSectionHeader(headerSlot, getString(R.string.op_floor_list)))

        val emptySlot = view.findViewById<FrameLayout>(R.id.floor_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.op_empty_floors))
        emptySlot.addView(emptyView)

        val primarySlot = view.findViewById<FrameLayout>(R.id.floor_primary_slot)
        val continueBtn = UiComponents.inflatePrimaryButton(primarySlot, getString(R.string.op_continue))
        primarySlot.addView(continueBtn)
        continueBtn.setOnClickListener {
            if (OperationUiSelection.floorId == null) {
                Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.ZONE_LIST)
        }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.floor_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }

        renderList()
    }

    private fun renderList() {
        UiComponents.clearChildren(listContainer)
        val buildingId = OperationUiSelection.buildingId
        val items = OperationDummyData.floors.filter { it.buildingId == buildingId }
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = listContainer,
                    title = item.name,
                    subtitle = getString(R.string.op_floor_subtitle),
                    selected = item.id == OperationUiSelection.floorId,
                    onClick = {
                        OperationUiSelection.floorId = item.id
                        renderList()
                    },
                ),
            )
        }
    }
}
