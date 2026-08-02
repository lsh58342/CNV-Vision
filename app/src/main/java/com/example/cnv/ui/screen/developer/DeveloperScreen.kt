package com.example.cnv.ui.screen.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.debug.PipelinePerfDebugHud
import com.example.cnv.debug.SpeedValidationDebugHud
import com.example.cnv.production.ProductionLog
import com.example.cnv.production.ProductionMetrics
import com.example.cnv.speed.SpeedValidatorEngine
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/**
 * Developer Options — overlays / metrics / tracking (STEP 20-4).
 * Always accessible; no Role checks.
 */
class DeveloperScreen : BaseScreen() {

    private var speedHud: SpeedValidationDebugHud? = null
    private var perfHud: PipelinePerfDebugHud? = null
    private var mode: HudMode = HudMode.PERF

    private enum class HudMode { SPEED, PERF }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val body = view.findViewById<TextView>(R.id.screen_body)
        startPerfHud(body)

        view.findViewById<Button>(R.id.button_dev_hud).setOnClickListener {
            mode = HudMode.SPEED
            stopHuds()
            val engine = SpeedValidatorEngine.sharedOrNull()
            if (engine != null) {
                speedHud = SpeedValidationDebugHud(body, engine).also { it.start() }
            } else {
                body.text = getString(R.string.speed_validation_developer_idle)
            }
            ProductionLog.debug("CNV.Dev", "Debug Overlay / Developer HUD active")
        }
        view.findViewById<Button>(R.id.button_dev_perf).setOnClickListener {
            mode = HudMode.PERF
            stopHuds()
            startPerfHud(body)
            val snap = ProductionMetrics.snapshot()
            ProductionLog.performance(
                "Perf/Frame stats fps=%.1f frame=%.1fms".format(snap.fps, snap.frameTimeMs),
            )
        }
        view.findViewById<Button>(R.id.button_dev_tracking).setOnClickListener {
            nav().navigate(CnvDestination.COORDINATE_VALIDATION)
        }
        view.findViewById<Button>(R.id.button_unlock_route).setOnClickListener {
            if (siteVm.unlockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_unlocked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_unlock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }

    override fun onResume() {
        super.onResume()
        val body = view?.findViewById<TextView>(R.id.screen_body) ?: return
        when (mode) {
            HudMode.PERF -> if (perfHud == null) startPerfHud(body)
            HudMode.SPEED -> {
                if (speedHud == null) {
                    val engine = SpeedValidatorEngine.sharedOrNull()
                    if (engine != null) {
                        speedHud = SpeedValidationDebugHud(body, engine).also { it.start() }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        stopHuds()
        super.onDestroyView()
    }

    private fun startPerfHud(body: TextView) {
        perfHud = PipelinePerfDebugHud(body).also { it.start() }
    }

    private fun stopHuds() {
        speedHud?.stop()
        speedHud = null
        perfHud?.stop()
        perfHud = null
    }
}
