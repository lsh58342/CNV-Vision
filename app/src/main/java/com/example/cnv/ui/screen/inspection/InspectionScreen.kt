package com.example.cnv.ui.screen.inspection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/**
 * Inspection Screen — Camera preview + START/STOP + live status only.
 * No HeatMap / History / Settings / Developer / Commissioning chrome.
 */
class InspectionScreen : BaseScreen() {

    private val viewModel: InspectionViewModel by viewModels {
        InspectionViewModel.Factory(requireActivity() as AppCompatActivity)
    }

    private lateinit var trackingCardValue: TextView
    private lateinit var distanceCardValue: TextView
    private lateinit var shockCardValue: TextView
    private lateinit var elapsedCardValue: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_inspection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zoneName = siteVm.currentZoneName().takeIf { it != "—" }
            ?: getString(R.string.screen_inspection)
        view.findViewById<TextView>(R.id.inspection_zone_name).text = zoneName
        view.findViewById<TextView>(R.id.inspection_session_status).text =
            getString(R.string.insp_session_status, "IDLE")

        val headerSlot = view.findViewById<FrameLayout>(R.id.inspection_status_header_slot)
        headerSlot.addView(
            UiComponents.inflateSectionHeader(headerSlot, getString(R.string.insp_status_section)),
        )

        val cards = view.findViewById<LinearLayout>(R.id.inspection_status_cards)
        val tracking = UiComponents.inflateStatusCard(
            cards, getString(R.string.insp_tracking), "—",
            requireContext().getColor(R.color.cnv_text_primary),
        )
        val distance = UiComponents.inflateStatusCard(
            cards, getString(R.string.insp_distance), "—",
            requireContext().getColor(R.color.cnv_text_primary),
        )
        val shock = UiComponents.inflateStatusCard(
            cards, getString(R.string.insp_shock), "—",
            requireContext().getColor(R.color.cnv_text_primary),
        )
        val elapsed = UiComponents.inflateStatusCard(
            cards, getString(R.string.insp_elapsed), "—",
            requireContext().getColor(R.color.cnv_text_primary),
        )
        cards.addView(tracking)
        cards.addView(distance)
        cards.addView(shock)
        cards.addView(elapsed)
        trackingCardValue = tracking.findViewById(R.id.status_card_value)
        distanceCardValue = distance.findViewById(R.id.status_card_value)
        shockCardValue = shock.findViewById(R.id.status_card_value)
        elapsedCardValue = elapsed.findViewById(R.id.status_card_value)

        val preview = view.findViewById<PreviewView>(R.id.inspection_preview)
        viewModel.attachPreview(preview)

        viewModel.status.observe(viewLifecycleOwner) { status ->
            view.findViewById<TextView>(R.id.inspection_session_status).text =
                getString(R.string.insp_session_status, status.sessionState)
            trackingCardValue.text = status.trackingLabel
            distanceCardValue.text = getString(R.string.insp_distance_value, status.distanceMm)
            shockCardValue.text = getString(R.string.insp_shock_value, status.shockCount)
            elapsedCardValue.text = getString(R.string.insp_elapsed_value, status.elapsedSec)
            val warning = view.findViewById<TextView>(R.id.inspection_speed_warning)
            warning.visibility = if (status.speedMismatchWarning) View.VISIBLE else View.GONE
        }

        view.findViewById<MaterialButton>(R.id.button_inspection_start).setOnClickListener {
            if (!viewModel.startInspection()) {
                Toast.makeText(requireContext(), R.string.inspection_no_route, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_inspection_stop).setOnClickListener {
            viewModel.stopInspection()
            nav().navigate(CnvDestination.INSPECTION_RESULT)
        }
    }

    override fun onDestroyView() {
        viewModel.detachPreview()
        super.onDestroyView()
    }
}
