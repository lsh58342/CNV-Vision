package com.example.cnv.ui.screen.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADLayer
import com.example.cnv.cad.CADView
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.CoordinateDebugOverlay
import com.example.cnv.heatmap.CoordinateValidationBuilder
import com.example.cnv.heatmap.CoordinateValidationSnapshot
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton

/**
 * Coordinate Validation viewer (STEP 14-1).
 * Shows Route Position → Drawing Coordinate mapping; no HeatMap calc/UI.
 */
class CoordinateValidationScreen : BaseScreen() {

    private var cadController: CADController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_coordinate_validation, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cadView = view.findViewById<CADView>(R.id.coord_cad_view)
        val overlay = view.findViewById<CoordinateDebugOverlay>(R.id.coord_debug_overlay)
        val statsView = view.findViewById<TextView>(R.id.coord_stats)

        cadController = CADController(
            routeRepository = FactoryCatalog.get().routes.underlying(),
            cadView = cadView,
            mapperProvider = { null },
            debugHud = null,
        ).also {
            it.setLayerEnabled(CADLayer.DEBUG, false)
            it.setLayerEnabled(CADLayer.ROUTE, true)
            it.setLayerEnabled(CADLayer.POSITION, true)
            it.start()
            it.fitToRoute()
        }

        overlay.setCameraProvider { cadView.viewport().camera }
        overlay.setOverlayEnabled(true)

        fun reload() {
            buildSnapshotAsync { snap ->
                if (!isAdded) return@buildSnapshotAsync
                overlay.setSnapshot(snap)
                statsView.text = snap.stats.summaryLines().joinToString("\n")
                if (snap.points.isEmpty()) {
                    statsView.append("\n")
                    statsView.append(getString(R.string.coord_no_session))
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.button_coord_fit).setOnClickListener {
            cadController?.fitToRoute()
            overlay.invalidate()
        }
        view.findViewById<MaterialButton>(R.id.button_coord_reload).setOnClickListener { reload() }
        view.findViewById<MaterialButton>(R.id.button_coord_back).setOnClickListener {
            nav().navigateBack()
        }

        reload()
    }

    override fun onResume() {
        super.onResume()
        cadController?.start()
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

    private fun buildSnapshotAsync(onResult: (CoordinateValidationSnapshot) -> Unit) {
        val catalog = FactoryCatalog.get()
        val drawing = catalog.drawings.current()
        if (drawing == null) {
            onResult(CoordinateValidationSnapshot.empty(""))
            return
        }
        val route = catalog.routes.currentRoute()
        if (route == null) {
            onResult(CoordinateValidationSnapshot.empty(drawing.id))
            return
        }
        InspectionDbGate.submit(
            block = {
                val summaries = catalog.inspections.loadHistorySummaries(drawing.id)
                val sessions = summaries.mapNotNull { catalog.inspections.loadSession(it.sessionId) }
                CoordinateValidationBuilder.build(
                    drawing = drawing,
                    route = route,
                    sessions = sessions,
                    mapper = null,
                )
            },
            onMain = onResult,
        )
    }
}
