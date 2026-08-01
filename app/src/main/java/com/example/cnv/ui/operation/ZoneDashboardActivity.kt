package com.example.cnv.ui.operation

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cnv.R
import com.example.cnv.factory.seed.FactorySeedData
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.zone.dashboard.ZoneDashboardController
import com.google.android.material.button.MaterialButton

/**
 * Screen 5 — Zone Dashboard (Operation start for a Zone).
 */
class ZoneDashboardActivity : AppCompatActivity() {

    private val controller = ZoneDashboardController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zone_dashboard)
        FactorySeedData.ensureSeeded()
        bind()

        findViewById<MaterialButton>(R.id.button_zone_start_inspection).setOnClickListener {
            val state = controller.load()
            if (!state.canStartInspection) {
                Toast.makeText(this, R.string.zone_dash_not_ready, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppNavigator.openInspection(this, state.zone?.id)
        }
        findViewById<MaterialButton>(R.id.button_zone_heatmap).setOnClickListener {
            Toast.makeText(this, R.string.zone_dash_heatmap_hint, Toast.LENGTH_SHORT).show()
            AppNavigator.openInspection(this)
        }
        findViewById<MaterialButton>(R.id.button_zone_csv).setOnClickListener {
            Toast.makeText(this, R.string.zone_dash_csv_future, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.button_zone_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.button_zone_settings).setOnClickListener {
            AppNavigator.openSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val state = controller.load()
        val zone = state.zone
        findViewById<TextView>(R.id.zone_dash_title).text =
            zone?.name ?: getString(R.string.zone_dash_missing)
        findViewById<TextView>(R.id.zone_dash_body).text = buildString {
            appendLine(getString(R.string.zone_dash_dwg, if (state.dwgReady) "OK" else "MISSING"))
            appendLine(
                getString(
                    R.string.zone_dash_calibration,
                    if (state.calibrationReady) "OK" else "MISSING",
                ),
            )
            val last = state.lastInspection
            appendLine(
                getString(
                    R.string.zone_dash_last_inspection,
                    last?.sessionId?.take(8) ?: "—",
                ),
            )
            appendLine(getString(R.string.zone_dash_history_count, state.inspectionHistoryCount))
            appendLine(getString(R.string.zone_dash_heatmap_count, state.heatMapCount))
            if (zone != null) {
                appendLine("Route: ${zone.routeId}")
                appendLine("Start: ${zone.start}")
                appendLine("End: ${zone.end}")
            }
        }
    }
}
