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
import androidx.appcompat.app.AlertDialog
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/** Floor list under Building — open Drawing list for selected Floor. */
class FloorScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View
    private var selectedFloorId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_floor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.floor_list_container)
        selectedFloorId = CurrentContext.get().floorId

        view.findViewById<TextView>(R.id.floor_context).text = getString(
            R.string.op_context_factory_building,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
        )

        val listHeader = view.findViewById<FrameLayout>(R.id.floor_list_header_slot)
        listHeader.addView(
            UiComponents.inflateSectionHeader(listHeader, getString(R.string.op_floor_list)),
        )
        val emptySlot = view.findViewById<FrameLayout>(R.id.floor_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.setup_empty_floors))
        emptySlot.addView(emptyView)

        view.findViewById<MaterialButton>(R.id.button_add_floor).setOnClickListener { promptAddFloor() }
        view.findViewById<MaterialButton>(R.id.button_rename_floor).setOnClickListener { promptRenameFloor() }
        view.findViewById<MaterialButton>(R.id.button_delete_floor).setOnClickListener { confirmDeleteFloor() }
        view.findViewById<MaterialButton>(R.id.button_open_drawings).setOnClickListener {
            val id = selectedFloorId
            if (id == null) {
                Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            siteVm.selectFloor(id)
            nav().navigate(CnvDestination.DRAWING_LIST)
        }
        view.findViewById<MaterialButton>(R.id.button_floor_back).setOnClickListener {
            nav().navigateBack()
        }

        siteVm.floors.observe(viewLifecycleOwner) { renderFloorList(it) }
        siteVm.loadFloors()
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
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptRenameFloor() {
        val id = selectedFloorId
        if (id == null) {
            Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            setText(siteVm.currentFloorName().takeIf { it != "—" }.orEmpty())
            setSingleLine()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_rename_floor)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (!siteVm.renameFloor(id, input.text?.toString().orEmpty())) {
                    Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteFloor() {
        val id = selectedFloorId ?: run {
            Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.setup_delete_floor)
            .setMessage(R.string.setup_delete_floor_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (siteVm.deleteFloor(id)) {
                    selectedFloorId = null
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
                        renderFloorList(items)
                    },
                ),
            )
        }
    }
}
