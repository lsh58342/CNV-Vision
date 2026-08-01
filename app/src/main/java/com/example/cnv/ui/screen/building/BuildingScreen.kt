package com.example.cnv.ui.screen.building

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/** Building Dashboard — LGES Poland → user-created Buildings. */
class BuildingScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View
    private var selectedBuildingId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_building, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.building_list_container)
        selectedBuildingId = CurrentContext.get().buildingId

        siteVm.bootstrap()
        siteVm.factoryName.observe(viewLifecycleOwner) { name ->
            view.findViewById<TextView>(R.id.building_factory_label).text =
                getString(R.string.op_context_factory, name)
        }

        val headerSlot = view.findViewById<FrameLayout>(R.id.building_header_slot)
        headerSlot.addView(
            UiComponents.inflateSectionHeader(headerSlot, getString(R.string.op_building_list)),
        )

        val emptySlot = view.findViewById<FrameLayout>(R.id.building_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.setup_empty_buildings))
        emptySlot.addView(emptyView)

        view.findViewById<MaterialButton>(R.id.button_add_building).setOnClickListener {
            promptName(R.string.setup_add_building, R.string.setup_building_name_hint) { name ->
                val created = siteVm.addBuilding(name)
                if (created == null) {
                    Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    selectedBuildingId = created.id
                    siteVm.selectBuilding(created.id)
                }
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_building).setOnClickListener {
            val id = selectedBuildingId
            if (id == null) {
                Toast.makeText(requireContext(), R.string.op_select_building_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            siteVm.selectBuilding(id)
            nav().navigate(CnvDestination.FLOOR_SELECT)
        }

        // Long-press rename/delete via secondary actions on open button long-click
        view.findViewById<MaterialButton>(R.id.button_open_building).setOnLongClickListener {
            showBuildingEditMenu()
            true
        }

        siteVm.buildings.observe(viewLifecycleOwner) { renderList(it) }
        siteVm.loadBuildings()
    }

    private fun showBuildingEditMenu() {
        val id = selectedBuildingId ?: run {
            Toast.makeText(requireContext(), R.string.op_select_building_first, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_building_dashboard)
            .setItems(
                arrayOf(
                    getString(R.string.setup_rename_building),
                    getString(R.string.setup_delete_building),
                ),
            ) { _, which ->
                when (which) {
                    0 -> promptName(R.string.setup_rename_building, R.string.setup_building_name_hint) { name ->
                        if (!siteVm.renameBuilding(id, name)) {
                            Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> AlertDialog.Builder(requireContext())
                        .setMessage(R.string.setup_delete_building_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            if (siteVm.deleteBuilding(id)) selectedBuildingId = null
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun promptName(titleRes: Int, hintRes: Int, onOk: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = getString(hintRes)
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(input.text?.toString().orEmpty()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderList(items: List<com.example.cnv.factory.model.Building>) {
        UiComponents.clearChildren(listContainer)
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = listContainer,
                    title = item.name,
                    subtitle = getString(R.string.op_building_subtitle),
                    selected = item.id == selectedBuildingId,
                    onClick = {
                        selectedBuildingId = item.id
                        siteVm.selectBuilding(item.id)
                        renderList(items)
                    },
                ),
            )
        }
    }
}
