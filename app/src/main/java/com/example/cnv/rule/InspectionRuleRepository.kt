package com.example.cnv.rule

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Cached session Rule Results (STEP 18).
 * Review / Replay / Report must use this — never re-evaluate in UI.
 * Definitions come from [RuleDefinitionRepository]; Analysis from Analysis Repository.
 */
class InspectionRuleRepository(
    private val catalog: FactoryCatalog,
    private val definitionRepository: RuleDefinitionRepository = RuleDefinitionRepository().also {
        DefaultRuleCatalog.seedInto(it)
    },
    private val engine: InspectionRuleEngine = InspectionRuleEngine(definitionRepository),
) {

    private val lock = Any()
    private val cache = LinkedHashMap<String, InspectionRuleResult>()

    fun definitions(): RuleDefinitionRepository = definitionRepository

    fun catalogVersion(): Int = definitionRepository.catalogVersion()

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
     * Analyze-once via Analysis Repository, then evaluate rules once (cached for session).
     * Changing Rule definitions does not re-evaluate existing cached sessions.
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
        catalog.inspections.loadSessionAsync(sessionId) { persisted ->
            if (preferredDrawingId != null &&
                persisted != null &&
                persisted.summary.drawingId != preferredDrawingId
            ) {
                onResult(null)
                return@loadSessionAsync
            }
            val snapshotVersion = persisted?.summary?.ruleCatalogVersion
                ?.takeIf { it > 0 }
                ?: catalogVersion()
            val drawingId = preferredDrawingId ?: persisted?.summary?.drawingId
            catalog.analysis.getOrAnalyzeAsync(sessionId, drawingId) { analysis ->
                if (analysis == null) {
                    onResult(null)
                    return@getOrAnalyzeAsync
                }
                onResult(evaluateAndCache(analysis, drawingId, snapshotVersion))
            }
        }
    }

    /**
     * Background-thread sync path for Excel / Report exporters.
     */
    fun evaluateSync(
        sessionId: String,
        preferredDrawingId: String? = null,
    ): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val analysis = catalog.analysis.getCached(sessionId)
            ?: catalog.analysis.analyzeSync(sessionId, preferredDrawingId)
            ?: return null
        val snapshot = runCatching {
            catalog.inspections.loadSession(sessionId)?.summary?.ruleCatalogVersion
        }.getOrNull()?.takeIf { it > 0 } ?: catalogVersion()
        return evaluateAndCache(analysis, preferredDrawingId ?: analysis.drawingId, snapshot)
    }

    fun evaluateFromCachedAnalysis(sessionId: String): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val analysis = catalog.analysis.getCached(sessionId) ?: return null
        return evaluateAndCache(analysis, analysis.drawingId, catalogVersion())
    }

    private fun evaluateAndCache(
        analysis: InspectionAnalysisResult,
        preferredDrawingId: String?,
        ruleCatalogVersionSnapshot: Int,
    ): InspectionRuleResult {
        synchronized(lock) {
            cache[analysis.sessionId]?.let { return it }
        }
        val ctx = buildContext(
            drawingId = analysis.drawingId.ifBlank { preferredDrawingId },
            ruleCatalogVersionSnapshot = ruleCatalogVersionSnapshot,
        )
        val result = engine.evaluate(analysis, ctx)
        synchronized(lock) {
            cache[analysis.sessionId] = result
            while (cache.size > MAX_CACHE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return result
    }

    fun buildContext(
        drawingId: String?,
        ruleCatalogVersionSnapshot: Int = catalogVersion(),
    ): RuleEvaluationContext {
        val ctx = CurrentContext.get()
        val drawing = drawingId?.let { catalog.drawings.get(it) }
            ?: catalog.drawings.current(ctx)
        val floor = drawing?.floorId?.let { catalog.floors.get(it) }
        return RuleEvaluationContext(
            factoryId = ctx.factoryId,
            buildingId = ctx.buildingId ?: floor?.buildingId,
            floorId = ctx.floorId ?: drawing?.floorId,
            drawingId = drawing?.id ?: drawingId,
            zoneId = null,
            ruleCatalogVersionSnapshot = ruleCatalogVersionSnapshot,
        )
    }

    companion object {
        private const val MAX_CACHE = 32
    }
}
