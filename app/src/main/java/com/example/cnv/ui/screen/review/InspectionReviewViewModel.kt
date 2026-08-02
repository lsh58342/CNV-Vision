package com.example.cnv.ui.screen.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.rule.InspectionIssue
import com.example.cnv.rule.InspectionRuleResult
import com.example.cnv.rule.InspectionRuleZoneSummary
import com.example.cnv.rule.InspectionWarning

/**
 * Inspection Review ViewModel (STEP 17-1 / 18).
 * Loads Analysis / Rule / HeatMap via Repositories only — no Engine direct calls.
 * Warning / Issue / Zone sections display Rule Result; rules are not re-evaluated in UI.
 */
class InspectionReviewViewModel(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val errorMessage: String? = null,
        val sessionId: String? = null,
        val drawingId: String? = null,
        val analysis: InspectionAnalysisResult? = null,
        val rules: InspectionRuleResult? = null,
        val warnings: List<InspectionWarning> = emptyList(),
        val issues: List<InspectionIssue> = emptyList(),
        val zoneSummaries: List<InspectionRuleZoneSummary> = emptyList(),
        val heatPoints: List<DrawingHeatPoint> = emptyList(),
        val heatPointCount: Int = 0,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private var loadGeneration = 0

    fun load(sessionId: String, drawingId: String) {
        val gen = ++loadGeneration
        _state.value = UiState(loading = true, sessionId = sessionId, drawingId = drawingId)

        // HeatMap: repository lookup only (no generation).
        val heatPoints = catalog.heatMaps.loadHeatPointsForSession(drawingId, sessionId)

        catalog.rules.getOrEvaluateAsync(sessionId, drawingId) { rules ->
            if (gen != loadGeneration) return@getOrEvaluateAsync
            val analysis = catalog.analysis.getCached(sessionId)
            if (analysis == null || rules == null) {
                _state.value = UiState(
                    loading = false,
                    errorMessage = when {
                        analysis == null -> "Analysis Result unavailable"
                        else -> "Rule Result unavailable"
                    },
                    sessionId = sessionId,
                    drawingId = drawingId,
                    analysis = analysis,
                    rules = rules,
                    warnings = rules?.warnings.orEmpty(),
                    issues = rules?.issues.orEmpty(),
                    zoneSummaries = rules?.zoneSummaries.orEmpty(),
                    heatPoints = heatPoints,
                    heatPointCount = heatPoints.size,
                )
                return@getOrEvaluateAsync
            }
            _state.value = UiState(
                loading = false,
                sessionId = sessionId,
                drawingId = drawingId,
                analysis = analysis,
                rules = rules,
                warnings = rules.warnings,
                issues = rules.issues,
                zoneSummaries = rules.zoneSummaries,
                heatPoints = heatPoints,
                heatPointCount = heatPoints.size,
            )
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InspectionReviewViewModel() as T
        }
    }
}
