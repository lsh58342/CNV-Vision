package com.example.cnv.analysis

import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.db.InspectionDbGate

/**
 * Cached Analysis Result access (STEP 17).
 * Replay / History / HeatMap / AI / Report must use this — not re-analyze sessions.
 */
class InspectionAnalysisRepository(
    private val catalog: FactoryCatalog,
    private val engine: InspectionAnalysisEngine = InspectionAnalysisEngine(),
) {

    private val lock = Any()
    private val cache = LinkedHashMap<String, InspectionAnalysisResult>()

    fun getCached(sessionId: String): InspectionAnalysisResult? =
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
     * Background analyze-once; returns cached result on subsequent calls.
     */
    fun getOrAnalyzeAsync(
        sessionId: String,
        preferredDrawingId: String? = null,
        onResult: (InspectionAnalysisResult?) -> Unit,
    ) {
        getCached(sessionId)?.let {
            onResult(it)
            return
        }
        InspectionDbGate.submit(
            block = { analyzeSync(sessionId, preferredDrawingId) },
            onMain = onResult,
            onError = { onResult(null) },
        )
    }

    /** Background-thread only. */
    fun analyzeSync(
        sessionId: String,
        preferredDrawingId: String? = null,
    ): InspectionAnalysisResult? {
        synchronized(lock) {
            cache[sessionId]?.let { return it }
        }
        val persisted = catalog.inspections.loadSession(sessionId) ?: return null
        if (preferredDrawingId != null && persisted.summary.drawingId != preferredDrawingId) {
            return null
        }
        val drawingId = persisted.summary.drawingId
        val route = catalog.routes.currentRoute()
        val zones = catalog.zones.forDrawing(drawingId)
        val layout = route?.let { HeatMapRouteLayout.build(it) }
        val heatLayer = catalog.heatMaps.loadHeatLayer(drawingId)
        val result = engine.analyze(
            InspectionAnalysisInput(
                session = persisted,
                heatLayer = heatLayer,
                zones = zones,
                route = route,
                layout = layout,
            ),
        )
        synchronized(lock) {
            cache[sessionId] = result
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
