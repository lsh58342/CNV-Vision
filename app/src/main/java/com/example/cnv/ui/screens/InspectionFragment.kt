package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

/**
 * Inspection skeleton only — no Camera/OpenCV/Fusion wiring in UI-3.
 */
class InspectionFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_inspection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.button_inspection_start).setOnClickListener {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_inspection_stop).setOnClickListener {
            Toast.makeText(requireContext(), R.string.skeleton_feature_later, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_inspection_to_result)
            .setOnClickListener { nav().navigate(CnvDestination.INSPECTION_RESULT) }
        view.findViewById<MaterialButton>(R.id.button_inspection_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
