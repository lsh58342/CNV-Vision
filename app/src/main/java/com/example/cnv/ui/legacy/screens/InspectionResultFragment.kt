package com.example.cnv.ui.legacy.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.ui.navigation.CnvDestination
import com.google.android.material.button.MaterialButton

/**
 * Inspection Result -Summary + HeatMap / CSV / History entry points.
 */
class InspectionResultFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.legacy_fragment_inspection_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val body = view.findViewById<TextView>(R.id.result_body)
        siteVm.latestResult.observe(viewLifecycleOwner) { result ->
            body.text = formatSummary(result)
        }
        siteVm.loadLatestResult()

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

    private fun formatSummary(result: InspectionResult?): String {
        if (result == null) return getString(R.string.result_summary_empty)
        val s = result.statistics
        return getString(
            R.string.result_summary_format,
            result.sessionId.take(8),
            s.totalDistanceMm,
            result.durationMs / 1000L,
            s.shockCount,
            s.maximumShockLevel,
            s.averageConfidence,
        )
    }
}
