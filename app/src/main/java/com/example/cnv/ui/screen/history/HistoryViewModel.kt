package com.example.cnv.ui.screen.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionSessionSummary
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * History ViewModel — Drawing-scoped Inspection Session query/management (STEP 15-3).
 * Uses Inspection Repository only; no HeatMap/Replay/CSV generation.
 */
class HistoryViewModel(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    enum class SortMode {
        NEWEST,
        OLDEST,
        DURATION,
        DISTANCE,
        SHOCK_COUNT,
    }

    enum class DateFilter {
        ALL,
        TODAY,
        DAYS_7,
        DAYS_30,
        CUSTOM,
    }

    data class HistoryStats(
        val totalCount: Int = 0,
        val averageDistanceMm: Float = 0f,
        val averageShockCount: Float = 0f,
        val latestInspectionMs: Long = 0L,
    )

    data class UiState(
        val drawingId: String? = null,
        val drawingName: String = "—",
        val sessions: List<InspectionSessionSummary> = emptyList(),
        val stats: HistoryStats = HistoryStats(),
        val sortMode: SortMode = SortMode.NEWEST,
        val dateFilter: DateFilter = DateFilter.ALL,
        val searchQuery: String = "",
        val customFromMs: Long? = null,
        val customToMs: Long? = null,
        val emptyMessage: String? = null,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var allSessions: List<InspectionSessionSummary> = emptyList()

    fun refresh() {
        val drawing = catalog.drawings.current(CurrentContext.get())
        if (drawing == null) {
            allSessions = emptyList()
            _state.value = UiState(emptyMessage = "No Drawing selected")
            return
        }
        allSessions = catalog.inspections.loadHistorySummaries(drawing.id)
        val cur = _state.value ?: UiState()
        _state.value = applyFilters(
            cur.copy(
                drawingId = drawing.id,
                drawingName = drawing.name,
                emptyMessage = null,
            ),
        )
    }

    fun setSort(mode: SortMode) {
        val cur = _state.value ?: return
        _state.value = applyFilters(cur.copy(sortMode = mode))
    }

    fun setDateFilter(filter: DateFilter) {
        val cur = _state.value ?: return
        _state.value = applyFilters(cur.copy(dateFilter = filter))
    }

    fun setCustomRange(fromMs: Long, toMs: Long) {
        val cur = _state.value ?: return
        _state.value = applyFilters(
            cur.copy(
                dateFilter = DateFilter.CUSTOM,
                customFromMs = minOf(fromMs, toMs),
                customToMs = maxOf(fromMs, toMs),
            ),
        )
    }

    fun setSearchQuery(query: String) {
        val cur = _state.value ?: return
        _state.value = applyFilters(cur.copy(searchQuery = query))
    }

    /**
     * Delete Session + related HeatLayer points / CSV / Replay metadata.
     * Does not run HeatMap Generator — filters existing layer only.
     */
    fun deleteSession(sessionId: String): Boolean {
        val drawingId = _state.value?.drawingId ?: return false
        catalog.deleteInspectionSession(drawingId, sessionId)
        if (HistorySelection.selectedSessionId == sessionId) {
            HistorySelection.clear()
        }
        refresh()
        return true
    }

    fun selectSession(sessionId: String) {
        val drawingId = _state.value?.drawingId ?: return
        HistorySelection.select(drawingId, sessionId)
    }

    private fun applyFilters(base: UiState): UiState {
        var list = allSessions
        list = filterByDate(list, base)
        list = filterBySearch(list, base.searchQuery)
        list = sort(list, base.sortMode)
        val stats = computeStats(allSessions)
        val empty = when {
            base.drawingId == null -> "No Drawing selected"
            allSessions.isEmpty() -> "이 Drawing에 저장된 Inspection이 없습니다."
            list.isEmpty() -> "No sessions match filter / search"
            else -> null
        }
        return base.copy(sessions = list, stats = stats, emptyMessage = empty)
    }

    private fun filterByDate(
        list: List<InspectionSessionSummary>,
        state: UiState,
    ): List<InspectionSessionSummary> {
        val now = System.currentTimeMillis()
        return when (state.dateFilter) {
            DateFilter.ALL -> list
            DateFilter.TODAY -> {
                val start = startOfDayMs(now)
                list.filter { it.startTimeMs >= start }
            }
            DateFilter.DAYS_7 -> {
                val start = now - TimeUnit.DAYS.toMillis(7)
                list.filter { it.startTimeMs >= start }
            }
            DateFilter.DAYS_30 -> {
                val start = now - TimeUnit.DAYS.toMillis(30)
                list.filter { it.startTimeMs >= start }
            }
            DateFilter.CUSTOM -> {
                val from = state.customFromMs ?: return list
                val to = state.customToMs ?: return list
                list.filter { it.startTimeMs in from..to }
            }
        }
    }

    private fun filterBySearch(
        list: List<InspectionSessionSummary>,
        query: String,
    ): List<InspectionSessionSummary> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return list
        return list.filter { s ->
            s.sessionId.lowercase(Locale.US).contains(q) ||
                s.inspectionVersion.lowercase(Locale.US).contains(q) ||
                formatDateKey(s.startTimeMs).contains(q) ||
                formatDateKey(s.endTimeMs).contains(q)
        }
    }

    private fun sort(
        list: List<InspectionSessionSummary>,
        mode: SortMode,
    ): List<InspectionSessionSummary> = when (mode) {
        SortMode.NEWEST -> list.sortedByDescending { it.startTimeMs }
        SortMode.OLDEST -> list.sortedBy { it.startTimeMs }
        SortMode.DURATION -> list.sortedByDescending { it.durationMs }
        SortMode.DISTANCE -> list.sortedByDescending { it.totalDistanceMm }
        SortMode.SHOCK_COUNT -> list.sortedByDescending { it.shockCount }
    }

    private fun computeStats(list: List<InspectionSessionSummary>): HistoryStats {
        if (list.isEmpty()) return HistoryStats()
        val n = list.size.toFloat()
        return HistoryStats(
            totalCount = list.size,
            averageDistanceMm = list.sumOf { it.totalDistanceMm.toDouble() }.toFloat() / n,
            averageShockCount = list.sumOf { it.shockCount }.toFloat() / n,
            latestInspectionMs = list.maxOf { it.endTimeMs.takeIf { t -> t > 0L } ?: it.startTimeMs },
        )
    }

    private fun startOfDayMs(now: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatDateKey(ms: Long): String {
        if (ms <= 0L) return ""
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                return HistoryViewModel() as T
            }
            error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
