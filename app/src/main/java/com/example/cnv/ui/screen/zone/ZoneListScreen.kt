package com.example.cnv.ui.screen.zone

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Zone
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/** Zone List — Drawing-scoped zones. */
class ZoneListScreen : BaseScreen() {

    private var query: String = ""
    private var sortByNameAsc: Boolean = true
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View
    private var selectedZoneId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_zone_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.zone_list_container)
        selectedZoneId = CurrentContext.get().zoneId

        view.findViewById<TextView>(R.id.zone_list_context).text = getString(
            R.string.op_context_full,
            siteVm.currentBuildingName(),
            siteVm.currentFloorName(),
            siteVm.currentDrawingName(),
        )

        val searchSlot = view.findViewById<FrameLayout>(R.id.zone_search_slot)
        val searchBar = UiComponents.inflateSearchBar(searchSlot)
        searchSlot.addView(searchBar)
        UiComponents.searchInput(searchBar).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                siteVm.zones.value?.let { renderList(it) }
            }
        })

        val sortBtn = view.findViewById<MaterialButton>(R.id.button_zone_sort)
        sortBtn.setOnClickListener {
            sortByNameAsc = !sortByNameAsc
            sortBtn.setText(if (sortByNameAsc) R.string.op_sort_name else R.string.op_sort_name_desc)
            siteVm.zones.value?.let { renderList(it) }
        }

        val headerSlot = view.findViewById<FrameLayout>(R.id.zone_list_header_slot)
        headerSlot.addView(
            UiComponents.inflateSectionHeader(headerSlot, getString(R.string.op_zone_list_section)),
        )

        val emptySlot = view.findViewById<FrameLayout>(R.id.zone_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.setup_empty_zones))
        emptySlot.addView(emptyView)

        val primarySlot = view.findViewById<FrameLayout>(R.id.zone_primary_slot)
        val continueBtn = UiComponents.inflatePrimaryButton(primarySlot, getString(R.string.op_open_dashboard))
        primarySlot.addView(continueBtn)
        continueBtn.setOnClickListener {
            val id = selectedZoneId
            if (id == null) {
                Toast.makeText(requireContext(), R.string.op_select_zone_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            siteVm.selectZone(id)
            nav().navigate(CnvDestination.ZONE_DASHBOARD)
        }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.zone_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }

        siteVm.zones.observe(viewLifecycleOwner) { renderList(it) }
        siteVm.loadZones()
    }

    private fun renderList(raw: List<Zone>) {
        UiComponents.clearChildren(listContainer)
        var items = raw
        if (query.isNotBlank()) {
            items = items.filter { it.name.contains(query, ignoreCase = true) }
        }
        items = if (sortByNameAsc) items.sortedBy { it.name } else items.sortedByDescending { it.name }
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateZoneCard(
                    parent = listContainer,
                    name = item.name,
                    lastInspection = "—",
                    colorHex = String.format("#%06X", 0xFFFFFF and item.colorArgb),
                    selected = item.id == selectedZoneId,
                    onClick = {
                        selectedZoneId = item.id
                        siteVm.selectZone(item.id)
                        renderList(raw)
                    },
                ),
            )
        }
    }
}
