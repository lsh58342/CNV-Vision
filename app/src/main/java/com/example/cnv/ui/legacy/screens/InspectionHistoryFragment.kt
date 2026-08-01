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
 * Inspection History -Current Zone history via ViewModel / CurrentContext.
 */
class InspectionHistoryFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.legacy_fragment_inspection_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val body = view.findViewById<TextView>(R.id.history_body)
        siteVm.historyLines.observe(viewLifecycleOwner) { list ->
            body.text = formatHistory(list)
        }
        siteVm.loadHistory()

        view.findViewById<MaterialButton>(R.id.button_history_sort).setOnClickListener {
            val sorted = siteVm.historyLines.value.orEmpty().sortedByDescending { it.endTimeMs }
            body.text = formatHistory(sorted)
        }
        view.findViewById<MaterialButton>(R.id.button_history_heatmap)
            .setOnClickListener { nav().navigate(CnvDestination.HEATMAP_VIEWER) }
        view.findViewById<MaterialButton>(R.id.button_history_csv).setOnClickListener {
            Toast.makeText(requireContext(), R.string.zone_dash_csv_future, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.button_history_back)
            .setOnClickListener { nav().navigateBack() }
    }

    private fun formatHistory(list: List<InspectionResult>): String {
        if (list.isEmpty()) return getString(R.string.history_empty)
        return list.asReversed().joinToString("\n\n") { r ->
            getString(
                R.string.history_item_format,
                r.sessionId.take(8),
                r.durationMs / 1000L,
                r.statistics.totalDistanceMm,
                r.statistics.shockCount,
                r.statistics.maximumShockLevel,
            )
        }
    }
}
