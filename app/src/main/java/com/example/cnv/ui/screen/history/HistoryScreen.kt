package com.example.cnv.ui.screen.history

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.cnv.R
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Inspection History — Drawing-scoped Session list (STEP 15-3).
 */
class HistoryScreen : BaseScreen() {

    private val vm: HistoryViewModel by viewModels { HistoryViewModel.Factory() }

    private var sortReady = false
    private var filterReady = false
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val dateOnlyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.button_history_back).setOnClickListener {
            nav().navigateBack()
        }

        setupSortSpinner(view.findViewById(R.id.history_sort_spinner))
        setupFilterSpinner(view.findViewById(R.id.history_filter_spinner))
        setupSearch(view.findViewById(R.id.history_search))

        vm.state.observe(viewLifecycleOwner) { state -> bindState(view, state) }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun setupSortSpinner(spinner: Spinner) {
        val labels = listOf(
            getString(R.string.history_sort_newest),
            getString(R.string.history_sort_oldest),
            getString(R.string.history_sort_duration),
            getString(R.string.history_sort_distance),
            getString(R.string.history_sort_shocks),
        )
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!sortReady) {
                    sortReady = true
                    return
                }
                val mode = when (position) {
                    1 -> HistoryViewModel.SortMode.OLDEST
                    2 -> HistoryViewModel.SortMode.DURATION
                    3 -> HistoryViewModel.SortMode.DISTANCE
                    4 -> HistoryViewModel.SortMode.SHOCK_COUNT
                    else -> HistoryViewModel.SortMode.NEWEST
                }
                vm.setSort(mode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        sortReady = true
    }

    private fun setupFilterSpinner(spinner: Spinner) {
        val labels = listOf(
            getString(R.string.history_filter_all),
            getString(R.string.history_filter_today),
            getString(R.string.history_filter_7d),
            getString(R.string.history_filter_30d),
            getString(R.string.history_filter_custom),
        )
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!filterReady) {
                    filterReady = true
                    return
                }
                when (position) {
                    1 -> vm.setDateFilter(HistoryViewModel.DateFilter.TODAY)
                    2 -> vm.setDateFilter(HistoryViewModel.DateFilter.DAYS_7)
                    3 -> vm.setDateFilter(HistoryViewModel.DateFilter.DAYS_30)
                    4 -> promptCustomRange()
                    else -> vm.setDateFilter(HistoryViewModel.DateFilter.ALL)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        filterReady = true
    }

    private fun promptCustomRange() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val from = cal.timeInMillis
                DatePickerDialog(
                    requireContext(),
                    { _, y2, m2, d2 ->
                        cal.set(y2, m2, d2, 23, 59, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        vm.setCustomRange(from, cal.timeInMillis)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun setupSearch(edit: TextInputEditText) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                vm.setSearchQuery(s?.toString().orEmpty())
            }
        })
    }

    private fun bindState(view: View, state: HistoryViewModel.UiState) {
        view.findViewById<TextView>(R.id.history_drawing_name).text =
            getString(R.string.history_drawing_label, state.drawingName)

        val latest = if (state.stats.latestInspectionMs > 0L) {
            dateFmt.format(Date(state.stats.latestInspectionMs))
        } else {
            "—"
        }
        view.findViewById<TextView>(R.id.history_stats).text = getString(
            R.string.history_stats_format,
            state.stats.totalCount,
            state.stats.averageDistanceMm,
            state.stats.averageShockCount,
            latest,
        )

        val empty = view.findViewById<TextView>(R.id.history_empty)
        val list = view.findViewById<LinearLayout>(R.id.history_session_list)
        list.removeAllViews()
        if (state.sessions.isEmpty()) {
            empty.isVisible = true
            empty.text = state.emptyMessage ?: getString(R.string.history_empty)
            return
        }
        empty.isVisible = false
        state.sessions.forEach { session ->
            list.addView(inflateSessionCard(list, session))
        }
    }

    private fun inflateSessionCard(
        parent: ViewGroup,
        session: InspectionSessionSummary,
    ): View {
        val card = layoutInflater.inflate(R.layout.item_session_card, parent, false) as MaterialCardView
        val date = dateOnlyFmt.format(Date(session.startTimeMs))
        val start = dateFmt.format(Date(session.startTimeMs))
        card.findViewById<TextView>(R.id.session_card_date).text =
            getString(R.string.history_card_date, date)
        val avgSpeedMPerMin = session.averageSpeedMmPerSec * 60f / 1000f
        card.findViewById<TextView>(R.id.session_card_body).text = getString(
            R.string.history_card_body,
            start,
            formatDuration(session.durationMs),
            session.totalDistanceMm,
            session.shockCount,
            session.coverage * 100f,
            avgSpeedMPerMin,
            session.speedValidation.validationScore,
            session.inspectionVersion,
        )
        val excelBtn = card.findViewById<MaterialButton>(R.id.session_card_open_excel)
        val archive = FactoryCatalog.get().excelArchives.get(session.sessionId)
            ?: session.excelFileUri.takeIf { it.isNotBlank() }?.let { uri ->
                com.example.cnv.report.excel.ExcelArchiveEntry(
                    sessionId = session.sessionId,
                    drawingId = session.drawingId,
                    fileUri = uri,
                    fileName = session.excelFileName.ifBlank { "report.xlsx" },
                ).also { FactoryCatalog.get().excelArchives.warm(it) }
            }
        if (archive != null) {
            excelBtn.isVisible = true
            excelBtn.text = getString(R.string.history_open_excel_named, archive.fileName)
            excelBtn.setOnClickListener {
                runCatching {
                    startActivity(
                        com.example.cnv.report.excel.ExcelExportUi.openIntent(
                            android.net.Uri.parse(archive.fileUri),
                        ),
                    )
                }.onFailure {
                    android.widget.Toast.makeText(
                        requireContext(),
                        R.string.excel_open_fail,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            excelBtn.isVisible = false
        }
        card.setOnClickListener {
            val drawingId = session.drawingId
            val args = android.os.Bundle().apply {
                putString(com.example.cnv.ui.navigation.NavArgs.DRAWING_ID, drawingId)
                putString(com.example.cnv.ui.navigation.NavArgs.SESSION_ID, session.sessionId)
            }
            nav().navigate(CnvDestination.INSPECTION_REVIEW, args = args)
        }
        return card
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(min, sec)
    }
}
