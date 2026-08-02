package com.example.cnv.rule

import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Cached Rule Result access (STEP 17-1).
 * Inspection Review must use this — never re-evaluate rules in the UI.
 * Depends on Analysis Repository only (no Inspection Event analysis).
 */
class InspectionRuleRepository(
    private val catalog: FactoryCatalog,
    private val engine: InspectionRuleEngine = InspectionRuleEngine(),
) {

    private val lock = Any()
    private val cache = LinkedHashMap<String, InspectionRuleResult>()

    fun getCached(sessionId: String): InspectionRuleResult? =
        synchronized(lock) { cache[sessionId] }

    fun invalidate(sessionId: String) {
        synchronized(lock) { cache.remove(sessionId) }
    }

    fun invalidateDrawing(drawingId: String) {
        synchronized(lock) {
            val ids = cache.filterValues { it.drawingId == drawingId }.keys.toList()
            ids.forEach { cache.remove(it) }
        }
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    /**
     * Analyze-once via Analysis Repository, then evaluate rules (cached).
     */
    fun getOrEvaluateAsync(
        sessionId: String,
        preferredDrawingId: String? = null,
        onResult: (InspectionRuleResult?) -> Unit,
    ) {
        getCached(sessionId)?.let {
            onResult(it)
            return
        }
        catalog.analysis.getOrAnalyzeAsync(sessionId, preferredDrawingId) { analysis ->
            if (analysis == null) {
                onResult(null)
                return@getOrAnalyzeAsync
            }
            val result = evaluateAndCache(analysis)
            onResult(result)
        }
    }

    fun evaluateFromCachedAnalysis(sessionId: String): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val analysis = catalog.analysis.getCached(sessionId) ?: return null
        return evaluateAndCache(analysis)
    }

    private fun evaluateAndCache(
        analysis: com.example.cnv.analysis.InspectionAnalysisResult,
    ): InspectionRuleResult {
        synchronized(lock) {
            cache[analysis.sessionId]?.let { return it }
        }
        val result = engine.evaluate(analysis)
        synchronized(lock) {
            cache[analysis.sessionId] = result
            while (cache.size > MAX_CACHE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return result
    }

    companion object {
        private const val MAX_CACHE = 32
    }
}
