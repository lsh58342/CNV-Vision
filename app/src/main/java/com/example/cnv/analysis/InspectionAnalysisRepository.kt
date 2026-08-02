package com.example.cnv.analysis

import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatLayer
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.RouteSnapshotCodec
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.map.Route

/**
 * Cached Analysis Result access (STEP 17 / 20-3).
 * Replay / History / HeatMap / AI / Report must use this — not re-analyze sessions.
 * Prefers Session-persisted Analysis Result JSON when present.
 */
class InspectionAnalysisRepository(
    private val catalog: FactoryCatalog,
    private val engine: InspectionAnalysisEngine = InspectionAnalysisEngine(),
) {

    private val lock = Any()
    private val cache = LinkedHashMap<String, InspectionAnalysisResult>()

    fun getCached(sessionId: String): InspectionAnalysisResult? =
        synchronized(lock) { cache[sessionId] }

    /** Warm cache from Session-persisted JSON (main-thread safe). */
    fun putCached(result: InspectionAnalysisResult) {
        putCache(result)
    }

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

    /**
     * Analyze with optional session route / heat overrides, then cache (finish path).
     * Background-thread only.
     */
    fun analyzeAndPersistSync(
        sessionId: String,
        preferredDrawingId: String? = null,
        routeOverride: Route? = null,
        heatPointsOverride: List<DrawingHeatPoint>? = null,
    ): InspectionAnalysisResult? {
        val result = analyzeSync(
            sessionId = sessionId,
            preferredDrawingId = preferredDrawingId,
            routeOverride = routeOverride,
            heatPointsOverride = heatPointsOverride,
            allowRecompute = true,
        )
        return result
    }

    /** Background-thread only. */
    fun analyzeSync(
        sessionId: String,
        preferredDrawingId: String? = null,
        routeOverride: Route? = null,
        heatPointsOverride: List<DrawingHeatPoint>? = null,
        allowRecompute: Boolean = false,
    ): InspectionAnalysisResult? {
        synchronized(lock) {
            cache[sessionId]?.let { return it }
        }
        val persisted = catalog.inspections.loadSession(sessionId) ?: return null
        if (preferredDrawingId != null && persisted.summary.drawingId != preferredDrawingId) {
            return null
        }
        // Prefer stored Analysis Result — no re-analysis for History / Excel / Review.
        if (!allowRecompute) {
            AnalysisResultCodec.decode(persisted.summary.analysisResultJson)?.let { stored ->
                putCache(stored)
                return stored
            }
        }
        val drawingId = persisted.summary.drawingId
        val route = routeOverride
            ?: RouteSnapshotCodec.decode(persisted.summary.routeSnapshotJson)?.toRoute()
            ?: catalog.routes.currentRoute()
        val zones = catalog.zones.forDrawing(drawingId)
        val layout = route?.let { HeatMapRouteLayout.build(it) }
        val heatLayer = when {
            heatPointsOverride != null -> DrawingHeatLayer(
                drawingId = drawingId,
                points = heatPointsOverride,
                sourceSessionIds = listOf(sessionId),
            )
            else -> {
                val fromSession = com.example.cnv.heatmap.HeatPointsCodec
                    .decode(persisted.summary.heatPointsJson)
                if (fromSession.isNotEmpty()) {
                    DrawingHeatLayer(
                        drawingId = drawingId,
                        points = fromSession,
                        sourceSessionIds = listOf(sessionId),
                    )
                } else {
                    catalog.heatMaps.loadHeatLayer(drawingId)
                }
            }
        }
        val result = engine.analyze(
            InspectionAnalysisInput(
                session = persisted,
                heatLayer = heatLayer,
                zones = zones,
                route = route,
                layout = layout,
            ),
        )
        putCache(result)
        return result
    }

    private fun putCache(result: InspectionAnalysisResult) {
        synchronized(lock) {
            cache[result.sessionId] = result
            while (cache.size > MAX_CACHE) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }
    }

    companion object {
        private const val MAX_CACHE = 32
    }
}
