package com.example.cnv.ui.screen.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.debug.SpeedValidationDebugHud
import com.example.cnv.speed.SpeedValidatorEngine
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Developer — Route Unlock + Speed Validation overlay + Commissioning entry. */
class DeveloperScreen : BaseScreen() {

    private var speedHud: SpeedValidationDebugHud? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val body = view.findViewById<TextView>(R.id.screen_body)
        val engine = SpeedValidatorEngine.sharedOrNull()
        if (engine != null) {
            speedHud = SpeedValidationDebugHud(body, engine).also { it.start() }
        } else {
            body.text = getString(R.string.speed_validation_developer_idle)
        }

        view.findViewById<Button>(R.id.button_unlock_route).setOnClickListener {
            if (siteVm.unlockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_unlocked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_unlock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.button_coord_validation).setOnClickListener {
            nav().navigate(CnvDestination.COORDINATE_VALIDATION)
        }
        view.findViewById<Button>(R.id.button_screen_next).setOnClickListener {
            siteVm.enterCommissioningMode()
            nav().navigate(CnvDestination.DRAWING_WORKSPACE)
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }

    override fun onResume() {
        super.onResume()
        val body = view?.findViewById<TextView>(R.id.screen_body) ?: return
        val engine = SpeedValidatorEngine.sharedOrNull()
        if (engine != null && speedHud == null) {
            speedHud = SpeedValidationDebugHud(body, engine).also { it.start() }
        }
    }

    override fun onDestroyView() {
        speedHud?.stop()
        speedHud = null
        super.onDestroyView()
    }
}
