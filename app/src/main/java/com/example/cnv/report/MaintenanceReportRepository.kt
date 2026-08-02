package com.example.cnv.report

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Cached Maintenance Report access (STEP 19).
 * Assembles from Analysis + Rule Repositories only — never re-runs engines.
 */
class MaintenanceReportRepository(
    private val catalog: FactoryCatalog,
) {

    private val lock = Any()
    private val cache = LinkedHashMap<String, MaintenanceReport>()

    fun getCached(sessionId: String): MaintenanceReport? =
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
     * Load Analysis + Rule Results (cached), assemble Report once.
     */
    fun getOrAssembleAsync(
        sessionId: String,
        preferredDrawingId: String? = null,
        onResult: (MaintenanceReport?) -> Unit,
    ) {
        getCached(sessionId)?.let {
            onResult(it)
            return
        }
        catalog.rules.getOrEvaluateAsync(sessionId, preferredDrawingId) { rules ->
            val analysis = catalog.analysis.getCached(sessionId)
            if (analysis == null || rules == null) {
                onResult(null)
                return@getOrEvaluateAsync
            }
            onResult(assembleAndCache(analysis, rules))
        }
    }

    private fun assembleAndCache(
        analysis: com.example.cnv.analysis.InspectionAnalysisResult,
        rules: com.example.cnv.rule.InspectionRuleResult,
    ): MaintenanceReport {
        synchronized(lock) {
            cache[analysis.sessionId]?.let { return it }
        }
        val ctx = CurrentContext.get()
        val drawing = catalog.drawings.get(analysis.drawingId)
            ?: catalog.drawings.current(ctx)
        val floor = drawing?.floorId?.let { catalog.floors.get(it) }
        val building = floor?.buildingId?.let { catalog.buildings.get(it) }
            ?: ctx.buildingId?.let { catalog.buildings.get(it) }
        val report = MaintenanceReportAssembler.assemble(
            analysis = analysis,
            rules = rules,
            buildingId = building?.id,
            buildingName = building?.name.orEmpty(),
            floorId = floor?.id ?: drawing?.floorId,
            floorName = floor?.name.orEmpty(),
            drawingName = drawing?.name.orEmpty(),
        )
        synchronized(lock) {
            cache[analysis.sessionId] = report
            while (cache.size > MAX_CACHE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
        return report
    }

    companion object {
        private const val MAX_CACHE = 32
    }
}
