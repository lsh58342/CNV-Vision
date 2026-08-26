package com.example.cnv.ui.screen.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.analysis.AnalysisResultCodec
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.rule.InspectionIssue
import com.example.cnv.rule.InspectionRuleResult
import com.example.cnv.rule.InspectionRuleZoneSummary
import com.example.cnv.rule.InspectionWarning

/**
 * Inspection Review ViewModel (STEP 17-1 / 18 / 20-3).
 * Loads Analysis / Rule / HeatMap via Repositories — recompute when Session JSON is empty.
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
        println("LOG[Review][LOAD] session=$sessionId drawing=$drawingId")

        catalog.inspections.loadSessionAsync(sessionId) { persisted ->
            if (gen != loadGeneration) return@loadSessionAsync
            if (persisted == null) {
                println("LOG[Review][LOAD] session missing")
                _state.value = UiState(
                    loading = false,
                    errorMessage = "Session not found",
                    sessionId = sessionId,
                    drawingId = drawingId,
                )
                return@loadSessionAsync
            }

            val heatPoints = runCatching {
                catalog.heatMaps.loadHeatPointsForSession(drawingId, sessionId)
                    .ifEmpty {
                        catalog.heatMaps.restoreSessionPoints(
                            sessionId,
                            persisted.summary.heatPointsJson,
                        )
                    }
            }.getOrElse {
                println("LOG[Review][HEAT] restore failed: ${it.message}")
                emptyList()
            }

            AnalysisResultCodec.decode(persisted.summary.analysisResultJson)?.let {
                catalog.analysis.putCached(it)
            }

            // Always resolve Analysis (stored or recompute), then Rules — never leave UI loading.
            catalog.analysis.getOrAnalyzeAsync(sessionId, drawingId) { analysis ->
                if (gen != loadGeneration) return@getOrAnalyzeAsync
                println(
                    "LOG[Review][ANALYSIS] ok=${analysis != null} " +
                        "storedLen=${persisted.summary.analysisResultJson.length}",
                )
                catalog.rules.getOrEvaluateAsync(sessionId, drawingId) { rules ->
                    if (gen != loadGeneration) return@getOrEvaluateAsync
                    val resolvedAnalysis = analysis
                        ?: catalog.analysis.getCached(sessionId)
                        ?: AnalysisResultCodec.decode(persisted.summary.analysisResultJson)
                    val resolvedRules = rules ?: catalog.rules.getCached(sessionId)
                    println(
                        "LOG[Review][RULES] ok=${resolvedRules != null} " +
                            "storedLen=${persisted.summary.ruleResultJson.length}",
                    )
                    _state.value = UiState(
                        loading = false,
                        errorMessage = when {
                            resolvedAnalysis == null && resolvedRules == null ->
                                "Analysis / Rule Result unavailable — re-run Inspection"
                            resolvedAnalysis == null ->
                                "Analysis Result unavailable (rules loaded)"
                            resolvedRules == null ->
                                "Rule Result unavailable (summary loaded)"
                            else -> null
                        },
                        sessionId = sessionId,
                        drawingId = drawingId,
                        analysis = resolvedAnalysis,
                        rules = resolvedRules,
                        warnings = resolvedRules?.warnings.orEmpty(),
                        issues = resolvedRules?.issues.orEmpty(),
                        zoneSummaries = resolvedRules?.zoneSummaries.orEmpty(),
                        heatPoints = heatPoints,
                        heatPointCount = heatPoints.size,
                    )
                }
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InspectionReviewViewModel() as T
        }
    }
}
