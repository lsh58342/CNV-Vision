package com.example.cnv.ui.screen.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History — Drawing-scoped Inspection Session summaries (STEP 13).
 */
class HistoryScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_screen_next).setOnClickListener {
            nav().navigate(CnvDestination.SETTINGS)
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
        bindHistory(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { bindHistory(it) }
    }

    private fun bindHistory(view: View) {
        val body = view.findViewById<TextView>(R.id.screen_body)
        val drawingId = CurrentContext.get().drawingId
        if (drawingId == null) {
            body.text = getString(R.string.history_no_drawing)
            return
        }
        val summaries = FactoryCatalog.get().inspections.loadHistorySummaries(drawingId)
        if (summaries.isEmpty()) {
            body.text = getString(R.string.history_empty)
            return
        }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        body.text = summaries.joinToString("\n\n") { s ->
            val profile = s.conveyorProfile
            val speed = profile.nominalSpeedMPerMin?.let { "%.2f m/min".format(it) }
                ?: getString(R.string.conveyor_nominal_unset)
            buildString {
                append(dateFmt.format(Date(s.endTimeMs)))
                append("\n")
                append(getString(R.string.history_line_distance, s.totalDistanceMm))
                append("\n")
                append(getString(R.string.history_line_shocks, s.shockCount))
                append("\n")
                append(getString(R.string.history_line_duration, s.durationMs / 1000f))
                append("\n")
                append(getString(R.string.history_line_session, s.sessionId.take(8)))
                append("\n")
                append(
                    getString(
                        R.string.history_line_profile,
                        speed,
                        profile.direction.name,
                        profile.expectedFps,
                        profile.motionProfile.name,
                    ),
                )
            }
        }
    }
}
