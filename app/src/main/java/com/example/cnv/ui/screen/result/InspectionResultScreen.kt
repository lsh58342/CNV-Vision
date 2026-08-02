package com.example.cnv.ui.screen.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.report.excel.ExcelExportUi
import com.example.cnv.report.excel.InspectionCsvExportService
import com.example.cnv.report.excel.InspectionExcelExportService
import com.example.cnv.report.excel.InspectionExcelReportGenerator
import com.example.cnv.ui.components.UiComponents
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavArgs
import com.example.cnv.ui.screen.BaseScreen

/**
 * Inspection Result Screen — summary + CSV / Excel export.
 */
class InspectionResultScreen : BaseScreen() {

    private val viewModel: ResultViewModel by viewModels()
    private val catalog = FactoryCatalog.get()
    private val csvExport = InspectionCsvExportService(catalog)
    private val excelExport = InspectionExcelExportService(catalog)

    private var pendingCsvName: String = "inspection.csv"
    private var pendingExcelName: String = InspectionExcelReportGenerator.defaultFileName()

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val summary = viewModel.summary.value ?: return@registerForActivityResult
        val drawingId = CurrentContext.get().drawingId ?: return@registerForActivityResult
        if (summary.empty) return@registerForActivityResult
        Toast.makeText(requireContext(), R.string.csv_exporting, Toast.LENGTH_SHORT).show()
        csvExport.exportAsync(
            sessionId = summary.sessionId,
            drawingId = drawingId,
            targetUri = uri,
            contentResolver = requireContext().contentResolver,
            fileName = pendingCsvName,
        ) { exportResult ->
            if (!isAdded) return@exportAsync
            Toast.makeText(
                requireContext(),
                if (exportResult.success) {
                    getString(R.string.csv_export_ok, exportResult.fileName.orEmpty())
                } else {
                    exportResult.errorMessage ?: getString(R.string.csv_export_fail)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private val createExcelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val summary = viewModel.summary.value ?: return@registerForActivityResult
        val drawingId = CurrentContext.get().drawingId ?: return@registerForActivityResult
        if (summary.empty) return@registerForActivityResult
        ExcelExportUi.takePersistablePermission(requireContext(), uri)
        Toast.makeText(requireContext(), R.string.excel_exporting, Toast.LENGTH_SHORT).show()
        excelExport.exportAsync(
            sessionId = summary.sessionId,
            drawingId = drawingId,
            targetUri = uri,
            contentResolver = requireContext().contentResolver,
            fileName = pendingExcelName,
        ) { exportResult ->
            if (!isAdded) return@exportAsync
            Toast.makeText(
                requireContext(),
                if (exportResult.success) {
                    getString(R.string.excel_export_ok, exportResult.fileName.orEmpty())
                } else {
                    exportResult.errorMessage ?: getString(R.string.excel_export_fail)
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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

        val heatmapSlot = view.findViewById<FrameLayout>(R.id.result_heatmap_slot)
        val heatmapBtn = UiComponents.inflatePrimaryButton(heatmapSlot, getString(R.string.result_open_heatmap))
        heatmapSlot.addView(heatmapBtn)
        heatmapBtn.setOnClickListener {
            val summary = viewModel.summary.value
            val drawingId = CurrentContext.get().drawingId
            if (summary == null || summary.empty || drawingId.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val args = Bundle().apply {
                putString(NavArgs.SESSION_ID, summary.sessionId)
                putString(NavArgs.DRAWING_ID, drawingId)
            }
            nav().navigate(CnvDestination.HEATMAP_VIEWER, args = args)
        }

        val historySlot = view.findViewById<FrameLayout>(R.id.result_history_slot)
        val historyBtn = UiComponents.inflatePrimaryButton(historySlot, getString(R.string.result_open_history))
        historySlot.addView(historyBtn)
        historyBtn.setOnClickListener {
            nav().navigate(CnvDestination.INSPECTION_HISTORY)
        }

        val csvSlot = view.findViewById<FrameLayout>(R.id.result_csv_slot)
        val csvBtn = UiComponents.inflateSecondaryButton(csvSlot, getString(R.string.result_export_csv))
        csvSlot.addView(csvBtn)
        csvBtn.setOnClickListener {
            val summary = viewModel.summary.value
            if (summary == null || summary.empty) {
                Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingCsvName = InspectionCsvExportService.defaultFileName(summary.sessionId)
            createCsvLauncher.launch(ExcelExportUi.createCsvDocumentIntent(pendingCsvName))
        }

        val excelSlot = view.findViewById<FrameLayout>(R.id.result_excel_slot)
        val excelBtn = UiComponents.inflateSecondaryButton(excelSlot, getString(R.string.result_export_excel))
        excelSlot.addView(excelBtn)
        excelBtn.setOnClickListener {
            val summary = viewModel.summary.value
            if (summary == null || summary.empty) {
                Toast.makeText(requireContext(), R.string.history_no_session_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingExcelName = InspectionExcelReportGenerator.defaultFileName()
            createExcelLauncher.launch(ExcelExportUi.createDocumentIntent(pendingExcelName))
        }

        val finishSlot = view.findViewById<FrameLayout>(R.id.result_finish_slot)
        val finishBtn = UiComponents.inflateSecondaryButton(finishSlot, getString(R.string.result_finish))
        finishSlot.addView(finishBtn)
        finishBtn.setOnClickListener {
            nav().navigateClearTo(CnvDestination.DRAWING_WORKSPACE)
        }
    }
}
