package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

class InspectionResultFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_inspection_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.button_result_heatmap)
            .setOnClickListener { nav().navigate(CnvDestination.HEATMAP_VIEWER) }
        view.findViewById<MaterialButton>(R.id.button_result_history)
            .setOnClickListener { nav().navigate(CnvDestination.INSPECTION_HISTORY) }
        view.findViewById<MaterialButton>(R.id.button_result_csv).setOnClickListener {
            Toast.makeText(requireContext(), R.string.zone_dash_csv_future, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_result_back)
            .setOnClickListener { nav().navigate(CnvDestination.ZONE_DASHBOARD) }
    }
}
