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
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.drawing.DrawingState
import com.example.cnv.ui.screen.drawing.DrawingUiStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Floor screen — manages Floors and Drawings for the selected Building.
 * Drawing List is removed; Drawing cards open Drawing Workspace.
 */
class FloorScreen : BaseScreen() {

    private lateinit var floorListContainer: LinearLayout
    private lateinit var drawingListContainer: LinearLayout
    private lateinit var drawingEmptyView: View
    private var selectedFloorId: String? = null
    private var pendingDwgUri: String? = null

    private val pickDwg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.setup_dwg_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingDwgUri = uri.toString()
        promptDrawingNameAndSave()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_floor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        floorListContainer = view.findViewById(R.id.floor_list_container)
        drawingListContainer = view.findViewById(R.id.drawing_list_container)
        selectedFloorId = CurrentContext.get().floorId

        view.findViewById<TextView>(R.id.floor_context).text = getString(
            R.string.op_context_factory_building,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
        )

        val emptySlot = view.findViewById<FrameLayout>(R.id.drawing_empty_slot)
        drawingEmptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.floor_empty_drawings))
        emptySlot.addView(drawingEmptyView)

        view.findViewById<MaterialButton>(R.id.button_add_floor).setOnClickListener { promptAddFloor() }
        view.findViewById<MaterialButton>(R.id.button_rename_floor).setOnClickListener { promptRenameFloor() }
        view.findViewById<MaterialButton>(R.id.button_delete_floor).setOnClickListener { confirmDeleteFloor() }
        view.findViewById<MaterialButton>(R.id.button_add_drawing).setOnClickListener { startAddDrawing() }
        view.findViewById<MaterialButton>(R.id.button_floor_back).setOnClickListener {
            nav().navigateBack()
        }

        siteVm.floors.observe(viewLifecycleOwner) { floors ->
            renderFloorList(floors)
            siteVm.loadDrawings()
        }
        siteVm.drawings.observe(viewLifecycleOwner) { renderDrawings(it) }
        siteVm.loadFloors()
    }

    override fun onResume() {
        super.onResume()
        siteVm.loadFloors()
        siteVm.loadDrawings()
        view?.findViewById<TextView>(R.id.floor_context)?.text = getString(
            R.string.op_context_factory_building,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
        )
    }

    private fun startAddDrawing() {
        if (selectedFloorId == null) {
            Toast.makeText(requireContext(), R.string.op_select_floor_first, Toast.LENGTH_SHORT).show()
            return
        }
        siteVm.selectFloor(selectedFloorId!!)
        pickDwg.launch("*/*")
    }

    private fun promptDrawingNameAndSave() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = getString(R.string.setup_drawing_name_hint)
            setSingleLine()
        }
        val descInput = EditText(requireContext()).apply {
            hint = getString(R.string.setup_drawing_desc_hint)
        }
        container.addView(nameInput)
        container.addView(descInput)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.floor_add_drawing)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val uri = pendingDwgUri
                pendingDwgUri = null
                val created = siteVm.addDrawing(
                    nameInput.text?.toString().orEmpty(),
                    descInput.text?.toString().orEmpty(),
                    uri,
                )
                if (created == null) {
                    Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    siteVm.selectDrawing(created.id)
                    Toast.makeText(requireContext(), R.string.setup_dwg_registered, Toast.LENGTH_SHORT).show()
                    nav().navigate(CnvDestination.DRAWING_WORKSPACE)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingDwgUri = null }
            .show()
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

    private fun renderFloorList(items: List<Floor>) {
        UiComponents.clearChildren(floorListContainer)
        items.forEach { item ->
            floorListContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = floorListContainer,
                    title = item.name,
                    subtitle = getString(R.string.op_floor_subtitle),
                    selected = item.id == selectedFloorId,
                    onClick = {
                        selectedFloorId = item.id
                        siteVm.selectFloor(item.id)
                        renderFloorList(items)
                        siteVm.loadDrawings()
                    },
                ),
            )
        }
    }

    private fun renderDrawings(items: List<Drawing>) {
        UiComponents.clearChildren(drawingListContainer)
        val showEmpty = selectedFloorId != null && items.isEmpty()
        UiComponents.setEmptyVisible(drawingEmptyView, showEmpty)
        if (selectedFloorId == null) {
            UiComponents.setEmptyVisible(drawingEmptyView, false)
            return
        }
        val catalog = FactoryCatalog.get()
        val selectedId = CurrentContext.get().drawingId
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        items.forEach { item ->
            val zoneCount = catalog.zones.forDrawing(item.id).size
            val state = DrawingState.resolve(item, zoneCount)
            val lastResult = catalog.inspections.latestForDrawing(item.id)
            val lastDate = lastResult?.let { dateFmt.format(Date(it.endTimeMs)) } ?: "—"
            drawingListContainer.addView(
                UiComponents.inflateDrawingCard(
                    parent = drawingListContainer,
                    name = item.name,
                    status = state.label(requireContext()),
                    statusColor = state.color(requireContext()),
                    recentInspection = getString(R.string.draw_card_recent_inspection, lastDate),
                    routeLock = DrawingUiStatus.routeLockLabel(requireContext(), item.routeLocked),
                    selected = item.id == selectedId,
                    onClick = {
                        siteVm.selectDrawing(item.id)
                        nav().navigate(CnvDestination.DRAWING_WORKSPACE)
                    },
                ),
            )
        }
    }
}
