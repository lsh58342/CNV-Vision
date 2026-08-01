package com.example.cnv.ui.screen.factory

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.example.cnv.ui.screen.dummy.OperationDummyData
import com.example.cnv.ui.screen.dummy.OperationUiSelection

/** Factory Screen — Operation UI (dummy data, Phase 2). */
class FactoryScreen : BaseScreen() {

    private var query: String = ""
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_factory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listContainer = view.findViewById(R.id.factory_list_container)

        val recent = OperationDummyData.factories.firstOrNull {
            it.id == OperationDummyData.recentFactoryId
        }
        val recentSlot = view.findViewById<FrameLayout>(R.id.factory_recent_slot)
        val recentColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        recentSlot.addView(recentColumn)
        recentColumn.addView(
            UiComponents.inflateSectionHeader(recentColumn, getString(R.string.op_recent_factory)),
        )
        if (recent != null) {
            recentColumn.addView(
                UiComponents.inflateInfoCard(
                    recentColumn,
                    recent.name,
                    getString(R.string.op_recent_factory_body, recent.location),
                ),
            )
        }

        val searchSlot = view.findViewById<FrameLayout>(R.id.factory_search_slot)
        val searchBar = UiComponents.inflateSearchBar(searchSlot)
        searchSlot.addView(searchBar)
        UiComponents.searchInput(searchBar).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })

        val headerSlot = view.findViewById<FrameLayout>(R.id.factory_list_header_slot)
        headerSlot.addView(UiComponents.inflateSectionHeader(headerSlot, getString(R.string.op_factory_list)))

        val emptySlot = view.findViewById<FrameLayout>(R.id.factory_empty_slot)
        emptyView = UiComponents.inflateEmptyView(emptySlot, getString(R.string.op_empty_factories))
        emptySlot.addView(emptyView)

        val primarySlot = view.findViewById<FrameLayout>(R.id.factory_primary_slot)
        val continueBtn = UiComponents.inflatePrimaryButton(primarySlot, getString(R.string.op_continue))
        primarySlot.addView(continueBtn)
        continueBtn.setOnClickListener {
            if (OperationUiSelection.factoryId == null) {
                Toast.makeText(requireContext(), R.string.op_select_factory_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.BUILDING_SELECT)
        }

        val secondarySlot = view.findViewById<FrameLayout>(R.id.factory_secondary_slot)
        val backBtn = UiComponents.inflateSecondaryButton(secondarySlot, getString(R.string.nav_back))
        secondarySlot.addView(backBtn)
        backBtn.setOnClickListener { nav().navigateBack() }

        renderList()
    }

    private fun renderList() {
        UiComponents.clearChildren(listContainer)
        val items = OperationDummyData.factories.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                it.location.contains(query, ignoreCase = true)
        }
        UiComponents.setEmptyVisible(emptyView, items.isEmpty())
        items.forEach { item ->
            listContainer.addView(
                UiComponents.inflateSelectCard(
                    parent = listContainer,
                    title = item.name,
                    subtitle = item.location,
                    selected = item.id == OperationUiSelection.factoryId,
                    onClick = {
                        OperationUiSelection.factoryId = item.id
                        renderList()
                    },
                ),
            )
        }
    }
}
