package com.example.cnv.ui.screen.drawing

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
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Drawing List — cards only; tap opens Drawing Dashboard. */
class DrawingListScreen : BaseScreen() {

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View
    private var pendingName: String = ""
    private var pendingDescription: String = ""

    private val pickDwg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.setup_dwg_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val created = siteVm.addDrawing(pendingName, pendingDescription, uri.toString())
        if (created == null) {
            Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
        } else {
            siteVm.selectDrawing(created.id)
            Toast.makeText(requireContext(), R.string.setup_dwg_registered, Toast.LENGTH_SHORT).show()
            nav().navigate(CnvDestination.DRAWING_DASHBOARD)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_drawing_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.drawing_list_container)

        view.findViewById<TextView>(R.id.drawing_list_context).text = getString(
            R.string.op_context_full,
            siteVm.factoryName.value ?: "LGES Poland",
            siteVm.currentBuildingName(),
            siteVm.currentFloorName(),
        )

        val emptySlot = view.findViewById<FrameLayout>(R.id.drawing_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.setup_empty_drawings))
        emptySlot.addView(emptyView)

        view.findViewById<MaterialButton>(R.id.button_add_drawing).setOnClickListener { promptAddDrawing() }
        view.findViewById<MaterialButton>(R.id.button_drawing_list_back).setOnClickListener {
            nav().navigateBack()
        }

        siteVm.drawings.observe(viewLifecycleOwner) { render(it) }
        siteVm.loadDrawings()
    }

    override fun onResume() {
        super.onResume()
        siteVm.loadDrawings()
    }

    private fun promptAddDrawing() {
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
            .setTitle(R.string.setup_add_drawing)
            .setView(container)
            .setPositiveButton(R.string.setup_register_dwg) { _, _ ->
                pendingName = nameInput.text?.toString().orEmpty()
                pendingDescription = descInput.text?.toString().orEmpty()
                if (pendingName.isBlank()) {
                    Toast.makeText(requireContext(), R.string.setup_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    pickDwg.launch("*/*")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun render(items: List<Drawing>) {
        UiComponents.clearChildren(listContainer)
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        val catalog = FactoryCatalog.get()
        val selectedId = CurrentContext.get().drawingId
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        items.forEach { item ->
            val zoneCount = catalog.zones.forDrawing(item.id).size
            val last = catalog.inspections.latestForDrawing(item.id)?.sessionId ?: "—"
            listContainer.addView(
                UiComponents.inflateDrawingCard(
                    parent = listContainer,
                    name = item.name,
                    status = DrawingUiStatus.summaryLabel(requireContext(), item, zoneCount),
                    recentInspection = getString(R.string.op_zone_last_inspection, last),
                    updatedAt = getString(
                        R.string.draw_card_updated,
                        dateFmt.format(Date(item.updatedAtMs)),
                    ),
                    selected = item.id == selectedId,
                    onClick = {
                        siteVm.selectDrawing(item.id)
                        nav().navigate(CnvDestination.DRAWING_DASHBOARD)
                    },
                ),
            )
        }
    }
}
