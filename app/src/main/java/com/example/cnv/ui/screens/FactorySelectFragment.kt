package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

class FactorySelectFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_select_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.select_title).setText(R.string.nav_factory_title)
        siteVm.contextSummary.observe(viewLifecycleOwner) {
            view.findViewById<TextView>(R.id.select_subtitle).text = it
        }
        siteVm.factories.observe(viewLifecycleOwner) { list ->
            val labels = list.map { it.name }
            val lv = view.findViewById<ListView>(R.id.select_list)
            lv.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
            lv.setOnItemClickListener { _, _, position, _ ->
                siteVm.selectFactory(list[position].id)
                nav().navigate(CnvDestination.BUILDING_SELECT)
            }
        }
        siteVm.loadFactories()
        view.findViewById<MaterialButton>(R.id.button_select_settings)
            .setOnClickListener { nav().navigate(CnvDestination.SETTINGS) }
        view.findViewById<MaterialButton>(R.id.button_select_back)
            .setOnClickListener { requireActivity().finish() }
    }
}
