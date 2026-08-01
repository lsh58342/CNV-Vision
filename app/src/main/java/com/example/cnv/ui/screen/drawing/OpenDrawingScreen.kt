package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADView
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.ui.navigation.AppNavigator
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/**
 * Open Drawing — CAD Viewer + Commissioning actions only.
 * No Inspection start, no HeatMap, no Debug HUD on this screen chrome.
 */
class OpenDrawingScreen : BaseScreen() {

    private var cadController: CADController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_open_drawing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.open_drawing_title).text =
            siteVm.currentDrawingName().takeIf { it != "—" } ?: getString(R.string.draw_open_drawing)

        val cadView = view.findViewById<CADView>(R.id.open_drawing_cad)
        val routeRepo = FactoryCatalog.get().routes.underlying()
        cadController = CADController(
            routeRepository = routeRepo,
            cadView = cadView,
            mapperProvider = { null },
            debugHud = null,
        )

        view.findViewById<MaterialButton>(R.id.button_open_set_origin).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Structural origin pick (CAD touch wiring later).
            if (siteVm.setOriginForCurrentDrawing(0.5f, 0.5f)) {
                Toast.makeText(requireContext(), R.string.setup_origin_set, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_origin_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_calibration).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppNavigator.openCalibration(requireActivity())
            siteVm.markCalibrationReadyForCurrentDrawing()
        }
        view.findViewById<MaterialButton>(R.id.button_open_route).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (siteVm.generateRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_generated, Toast.LENGTH_SHORT).show()
                cadController?.start()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_zone).setOnClickListener {
            if (!siteVm.canCreateZone()) {
                Toast.makeText(requireContext(), R.string.setup_zone_need_route, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AppNavigator.openZoneEditor(requireActivity())) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_route_lock).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (siteVm.lockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_locked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_route_lock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<MaterialButton>(R.id.button_open_drawing_back).setOnClickListener {
            nav().navigate(CnvDestination.DRAWING_DASHBOARD)
        }
    }

    override fun onResume() {
        super.onResume()
        cadController?.start()
        siteVm.loadDrawingDashboard()
    }

    override fun onPause() {
        cadController?.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        cadController?.stop()
        cadController = null
        super.onDestroyView()
    }
}
