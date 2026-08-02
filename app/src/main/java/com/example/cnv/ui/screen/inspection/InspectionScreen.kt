package com.example.cnv.ui.screen.inspection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/**
 * Inspection Screen — Camera preview + START/STOP + Live Dashboard + Shock Graph.
 */
class InspectionScreen : BaseScreen() {

    private val viewModel: InspectionViewModel by viewModels {
        InspectionViewModel.Factory(requireActivity() as AppCompatActivity)
    }

    private lateinit var trackingCardValue: TextView
    private lateinit var distanceCardValue: TextView
    private lateinit var shockCardValue: TextView
    private lateinit var elapsedCardValue: TextView
    private var dashboardBinder: LiveInspectionDashboardBinder? = null

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

        dashboardBinder = LiveInspectionDashboardBinder(
            view.findViewById(R.id.live_inspection_dashboard),
        )

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
        val liveRouteViewer = view.findViewById<LiveRouteViewer>(R.id.inspection_live_route)
        val shockGraph = view.findViewById<ShockGraphView>(R.id.inspection_shock_graph)
        val preflightBanner = view.findViewById<TextView>(R.id.inspection_preflight_banner)
        viewModel.attachPreview(preview)

        viewModel.dashboard.observe(viewLifecycleOwner) { dash ->
            dashboardBinder?.bind(dash)
        }

        viewModel.liveRoute.observe(viewLifecycleOwner) { overlay ->
            liveRouteViewer.bind(overlay)
        }

        viewModel.shockGraph.observe(viewLifecycleOwner) { graph ->
            shockGraph.bind(graph)
        }

        viewModel.preflight.observe(viewLifecycleOwner) { gate ->
            if (gate.ok) {
                preflightBanner.isVisible = false
            } else {
                preflightBanner.isVisible = true
                preflightBanner.text = getString(
                    R.string.insp_preflight_banner,
                    gate.blockers.joinToString("\n• ", prefix = "• "),
                )
            }
        }

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
            val result = viewModel.startInspection()
            if (!result.started) {
                showPreflightBlocked(result.preflight)
            }
        }
        view.findViewById<MaterialButton>(R.id.button_inspection_stop).setOnClickListener {
            viewModel.stopInspection()
            nav().navigate(CnvDestination.INSPECTION_RESULT)
        }

        viewModel.evaluatePreflight()
    }

    private fun showPreflightBlocked(preflight: InspectionPreflight) {
        val body = if (preflight.blockers.isEmpty()) {
            getString(R.string.insp_preflight_unknown)
        } else {
            preflight.blockers.joinToString("\n") { "• $it" }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.insp_preflight_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        dashboardBinder = null
        viewModel.detachPreview()
        super.onDestroyView()
    }
}
