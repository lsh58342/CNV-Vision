package com.example.cnv.ui.screen.result

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionResult

/**
 * Result Screen ViewModel — reads existing InspectionResult only (no new calculations).
 */
class ResultViewModel : ViewModel() {

    data class SummaryUi(
        val sessionId: String,
        val distanceMm: Float,
        val durationSec: Long,
        val shockCount: Int,
        val coveragePercent: Float,
        val empty: Boolean = false,
    )

    private val catalog = FactoryCatalog.get()

    private val _summary = MutableLiveData<SummaryUi>()
    val summary: LiveData<SummaryUi> = _summary

    fun loadLatest() {
        val result: InspectionResult? = catalog.inspections.underlying().latest()
        _summary.value = if (result == null) {
            SummaryUi(
                sessionId = "—",
                distanceMm = 0f,
                durationSec = 0L,
                shockCount = 0,
                coveragePercent = 0f,
                empty = true,
            )
        } else {
            val stats = result.statistics
            SummaryUi(
                sessionId = result.sessionId,
                distanceMm = stats.totalDistanceMm,
                durationSec = result.durationMs / 1000L,
                shockCount = stats.shockCount,
                // Existing statistic field — display only, no new coverage algorithm.
                coveragePercent = stats.averageConfidence * 100f,
                empty = false,
            )
        }
    }
}
