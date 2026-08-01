package com.example.cnv.ui.legacy.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.ui.legacy.feature.FeatureRuntime
import com.example.cnv.ui.legacy.feature.requireFeatureRuntime
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

/**
 * Inspection Screen -Camera + live status + START/STOP only.
 */
class InspectionFragment : BaseScreenFragment() {

    private var features: FeatureRuntime? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.legacy_fragment_inspection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val runtime = requireActivity().requireFeatureRuntime()
        features = runtime
        runtime.attachInspection(
            preview = view.findViewById(R.id.preview_view),
            status = FeatureRuntime.InspectionStatusViews(
                tracking = view.findViewById(R.id.inspection_tracking),
                distance = view.findViewById(R.id.inspection_distance),
                shock = view.findViewById(R.id.inspection_shock),
                elapsed = view.findViewById(R.id.inspection_elapsed),
            ),
        )

        view.findViewById<MaterialButton>(R.id.button_inspection_start).setOnClickListener {
            if (!runtime.startInspectionSession()) {
                Toast.makeText(requireContext(), R.string.inspection_no_route, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_inspection_stop).setOnClickListener {
            runtime.stopInspectionSession()
            nav().navigate(CnvDestination.INSPECTION_RESULT)
        }
    }

    override fun onDestroyView() {
        features?.detachInspection()
        features = null
        super.onDestroyView()
    }
}
