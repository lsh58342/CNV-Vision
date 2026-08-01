package com.example.cnv.ui.screen.building

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

/** Building Screen — Operation UI (dummy data, Phase 2). */
class BuildingScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_building, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.building_list_container)

        val factoryName = OperationUiSelection.selectedFactory()?.name ?: "—"
        view.findViewById<TextView>(R.id.building_context).text =
            getString(R.string.op_context_factory, factoryName)

        val headerSlot = view.findViewById<FrameLayout>(R.id.building_list_header_slot)
        headerSlot.addView(UiComponents.inflateSectionHeader(headerSlot, getString(R.string.op_building_list)))

        val emptySlot = view.findViewById<FrameLayout>(R.id.building_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.op_empty_buildings))
        emptySlot.addView(emptyView)

        val primarySlot = view.findViewById<FrameLayout>(R.id.building_primary_slot)
        val continueBtn = UiComponents.inflatePrimaryButton(primarySlot, getString(R.string.op_continue))
        primarySlot.addView(continueBtn)
        continueBtn.setOnClickListener {
            if (OperationUiSelection.buildingId == null) {
                Toast.makeText(requireContext(), R.string.op_select_building_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.FLOOR_SELECT)
        }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.building_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }

        renderList()
    }

    private fun renderList() {
        UiComponents.clearChildren(listContainer)
        val factoryId = OperationUiSelection.factoryId
        val items = OperationDummyData.buildings.filter { it.factoryId == factoryId }
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = listContainer,
                    title = item.name,
                    subtitle = getString(R.string.op_building_subtitle),
                    selected = item.id == OperationUiSelection.buildingId,
                    onClick = {
                        OperationUiSelection.buildingId = item.id
                        renderList()
                    },
                ),
            )
        }
    }
}
