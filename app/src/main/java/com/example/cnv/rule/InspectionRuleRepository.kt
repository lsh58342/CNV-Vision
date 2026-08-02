package com.example.cnv.rule

import com.example.cnv.analysis.AnalysisResultCodec
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.profile.InspectionProfileCodec
import com.example.cnv.profile.RuleProfile

/**
 * Cached session Rule Results (STEP 18 / 20-3).
 * Review / Replay / Report must use this — never re-evaluate in UI.
 * Prefers Session-persisted Rule Result; otherwise evaluates with Rule Profile snapshot overrides.
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
            RuleResultCodec.decode(persisted?.summary?.ruleResultJson)?.let { stored ->
                putCache(stored)
                AnalysisResultCodec.decode(persisted?.summary?.analysisResultJson)?.let {
                    catalog.analysis.putCached(it)
                }
                onResult(stored)
                return@loadSessionAsync
            }
            val snapshotVersion = persisted?.summary?.ruleCatalogVersion
                ?.takeIf { it > 0 }
                ?: catalogVersion()
            val drawingId = preferredDrawingId ?: persisted?.summary?.drawingId
            val profileJson = persisted?.summary?.inspectionProfileJson.orEmpty()
            catalog.analysis.getOrAnalyzeAsync(sessionId, drawingId) { analysis ->
                if (analysis == null) {
                    onResult(null)
                    return@getOrAnalyzeAsync
                }
                onResult(
                    evaluateAndCache(
                        analysis = analysis,
                        preferredDrawingId = drawingId,
                        ruleCatalogVersionSnapshot = snapshotVersion,
                        ruleProfileJson = profileJson,
                    ),
                )
            }
        }
    }

    /**
     * Finish-path evaluate with known analysis; caches result (persist done by caller).
     * Background-thread only.
     */
    fun evaluateAndPersistSync(
        sessionId: String,
        preferredDrawingId: String? = null,
        analysisOverride: InspectionAnalysisResult,
    ): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val persisted = catalog.inspections.loadSession(sessionId)
        val snapshotVersion = persisted?.summary?.ruleCatalogVersion
            ?.takeIf { it > 0 }
            ?: catalogVersion()
        return evaluateAndCache(
            analysis = analysisOverride,
            preferredDrawingId = preferredDrawingId ?: analysisOverride.drawingId,
            ruleCatalogVersionSnapshot = snapshotVersion,
            ruleProfileJson = persisted?.summary?.inspectionProfileJson.orEmpty(),
        )
    }

    /**
     * Background-thread sync path for Excel / Report exporters.
     */
    fun evaluateSync(
        sessionId: String,
        preferredDrawingId: String? = null,
    ): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val persisted = runCatching { catalog.inspections.loadSession(sessionId) }.getOrNull()
        RuleResultCodec.decode(persisted?.summary?.ruleResultJson)?.let { stored ->
            putCache(stored)
            return stored
        }
        val analysis = catalog.analysis.getCached(sessionId)
            ?: catalog.analysis.analyzeSync(sessionId, preferredDrawingId)
            ?: return null
        val snapshot = persisted?.summary?.ruleCatalogVersion?.takeIf { it > 0 }
            ?: catalogVersion()
        return evaluateAndCache(
            analysis = analysis,
            preferredDrawingId = preferredDrawingId ?: analysis.drawingId,
            ruleCatalogVersionSnapshot = snapshot,
            ruleProfileJson = persisted?.summary?.inspectionProfileJson.orEmpty(),
        )
    }

    fun evaluateFromCachedAnalysis(sessionId: String): InspectionRuleResult? {
        getCached(sessionId)?.let { return it }
        val analysis = catalog.analysis.getCached(sessionId) ?: return null
        return evaluateAndCache(
            analysis = analysis,
            preferredDrawingId = analysis.drawingId,
            ruleCatalogVersionSnapshot = catalogVersion(),
            ruleProfileJson = "",
        )
    }

    private fun evaluateAndCache(
        analysis: InspectionAnalysisResult,
        preferredDrawingId: String?,
        ruleCatalogVersionSnapshot: Int,
        ruleProfileJson: String,
    ): InspectionRuleResult {
        synchronized(lock) {
            cache[analysis.sessionId]?.let { return it }
        }
        val ctx = buildContext(
            drawingId = analysis.drawingId.ifBlank { preferredDrawingId },
            ruleCatalogVersionSnapshot = ruleCatalogVersionSnapshot,
        )
        val result = evaluateWithProfileSnapshot(analysis, ctx, ruleProfileJson)
        putCache(result)
        return result
    }

    /**
     * Applies Session Rule Profile overrides (enable / threshold / severity) without
     * mutating the live [RuleDefinitionRepository] or Rule Engine compare logic.
     */
    private fun evaluateWithProfileSnapshot(
        analysis: InspectionAnalysisResult,
        context: RuleEvaluationContext,
        ruleProfileJson: String,
    ): InspectionRuleResult {
        val profile = InspectionProfileCodec.decodeSnapshot(ruleProfileJson).rule
        if (profile.entries.isEmpty()) {
            return engine.evaluate(analysis, context)
        }
        val tempRepo = RuleDefinitionRepository()
        tempRepo.seed(applyOverrides(definitionRepository.all(), profile), reset = true)
        val sessionEngine = InspectionRuleEngine(tempRepo)
        return sessionEngine.evaluate(analysis, context)
    }

    private fun applyOverrides(
        definitions: List<RuleDefinition>,
        profile: RuleProfile,
    ): List<RuleDefinition> {
        val byId = profile.entries.associateBy { it.ruleId }
        return definitions.map { def ->
            val entry = byId[def.ruleId] ?: return@map def
            def.copy(
                enabled = entry.enabled,
                threshold = entry.thresholdOverride ?: def.threshold,
                severity = entry.severityOverride ?: def.severity,
                version = if (entry.ruleVersion > 0) entry.ruleVersion else def.version,
            )
        }
    }

    private fun putCache(result: InspectionRuleResult) {
        synchronized(lock) {
            cache[result.sessionId] = result
            while (cache.size > MAX_CACHE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
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
