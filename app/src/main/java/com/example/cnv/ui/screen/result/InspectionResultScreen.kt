package com.example.cnv.ui.screen.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/**
 * Inspection Result Screen — displays existing InspectionResult summary only.
 */
class InspectionResultScreen : BaseScreen() {

    private val viewModel: ResultViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zone = siteVm.currentZoneName()
        view.findViewById<TextView>(R.id.result_toolbar_subtitle).text =
            getString(R.string.result_zone_subtitle, zone)

        val summaryHeader = view.findViewById<FrameLayout>(R.id.result_summary_header_slot)
        summaryHeader.addView(
            UiComponents.inflateSectionHeader(summaryHeader, getString(R.string.result_summary_section)),
        )

        val summaryContainer = view.findViewById<LinearLayout>(R.id.result_summary_container)
        val primaryColor = requireContext().getColor(R.color.cnv_text_primary)

        val distanceCard = UiComponents.inflateStatusCard(
            summaryContainer, getString(R.string.result_distance), "—", primaryColor,
        )
        val durationCard = UiComponents.inflateStatusCard(
            summaryContainer, getString(R.string.result_duration), "—", primaryColor,
        )
        val shockCard = UiComponents.inflateStatusCard(
            summaryContainer, getString(R.string.result_shock), "—", primaryColor,
        )
        val coverageCard = UiComponents.inflateStatusCard(
            summaryContainer, getString(R.string.result_coverage), "—", primaryColor,
        )
        summaryContainer.addView(distanceCard)
        summaryContainer.addView(durationCard)
        summaryContainer.addView(shockCard)
        summaryContainer.addView(coverageCard)

        val distanceValue = distanceCard.findViewById<TextView>(R.id.status_card_value)
        val durationValue = durationCard.findViewById<TextView>(R.id.status_card_value)
        val shockValue = shockCard.findViewById<TextView>(R.id.status_card_value)
        val coverageValue = coverageCard.findViewById<TextView>(R.id.status_card_value)

        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            if (summary.empty) {
                view.findViewById<TextView>(R.id.result_toolbar_subtitle).text =
                    getString(R.string.result_empty)
            }
            distanceValue.text = getString(R.string.insp_distance_value, summary.distanceMm)
            durationValue.text = getString(R.string.result_duration_value, summary.durationSec)
            shockValue.text = getString(R.string.insp_shock_value, summary.shockCount)
            coverageValue.text = getString(R.string.result_coverage_value, summary.coveragePercent)
        }
        viewModel.loadLatest()

        val actionsHeader = view.findViewById<FrameLayout>(R.id.result_actions_header_slot)
        actionsHeader.addView(
            UiComponents.inflateSectionHeader(actionsHeader, getString(R.string.result_actions_section)),
        )

        val later = {
            Toast.makeText(requireContext(), R.string.op_feature_later, Toast.LENGTH_SHORT).show()
        }

        val heatmapSlot = view.findViewById<FrameLayout>(R.id.result_heatmap_slot)
        val heatmapBtn = UiComponents.inflatePrimaryButton(heatmapSlot, getString(R.string.result_open_heatmap))
        heatmapSlot.addView(heatmapBtn)
        heatmapBtn.setOnClickListener { later() }

        val historySlot = view.findViewById<FrameLayout>(R.id.result_history_slot)
        val historyBtn = UiComponents.inflatePrimaryButton(historySlot, getString(R.string.result_open_history))
        historySlot.addView(historyBtn)
        historyBtn.setOnClickListener { later() }

        val csvSlot = view.findViewById<FrameLayout>(R.id.result_csv_slot)
        val csvBtn = UiComponents.inflateSecondaryButton(csvSlot, getString(R.string.result_export_csv))
        csvSlot.addView(csvBtn)
        csvBtn.setOnClickListener { later() }

        val finishSlot = view.findViewById<FrameLayout>(R.id.result_finish_slot)
        val finishBtn = UiComponents.inflateSecondaryButton(finishSlot, getString(R.string.result_finish))
        finishSlot.addView(finishBtn)
        finishBtn.setOnClickListener {
            nav().navigateClearTo(CnvDestination.ZONE_DASHBOARD)
        }
    }
}
