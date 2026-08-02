package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.heatmap.DrawingHeatLayer
import com.example.cnv.heatmap.HeatMapGenerator
import com.example.cnv.heatmap.HeatMapIntensityConfig
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper

/**
 * Drawing-scoped HeatMap store (STEP 14).
 * Holds generated [DrawingHeatLayer]s + lightweight refs for History.
 * Heat calculation is delegated only to [HeatMapGenerator] — never Viewer.
 */
class HeatMapRepository(
    private val generator: HeatMapGenerator = HeatMapGenerator(HeatMapIntensityConfig.DEFAULT),
) {

    data class HeatMapRef(
        val drawingId: String,
        val sessionId: String,
        val label: String,
        val updatedAtMs: Long = System.currentTimeMillis(),
        val pointCount: Int = 0,
    )

    private val lock = Any()
    private val byDrawing = LinkedHashMap<String, ArrayDeque<HeatMapRef>>()
    private val layers = LinkedHashMap<String, DrawingHeatLayer>()

    fun put(ref: HeatMapRef) {
        synchronized(lock) {
            val q = byDrawing.getOrPut(ref.drawingId) { ArrayDeque() }
            q.addLast(ref)
            while (q.size > 20) q.removeFirst()
        }
    }

    fun forDrawing(drawingId: String): List<HeatMapRef> =
        synchronized(lock) { byDrawing[drawingId]?.toList().orEmpty() }

    fun latestForDrawing(drawingId: String): HeatMapRef? =
        forDrawing(drawingId).lastOrNull()

    fun forCurrentDrawing(context: CurrentContext = CurrentContext.get()): List<HeatMapRef> {
        val drawingId = context.drawingId ?: return emptyList()
        return forDrawing(drawingId)
    }

    /**
     * Generate Heat Layer from Inspection sessions for a Drawing.
     * Replaces previous layer content (regenerate semantics).
     */
    fun generateHeatLayer(
        drawingId: String,
        sessions: List<PersistedInspectionSession>,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): DrawingHeatLayer {
        val layer = generator.generateLayer(drawingId, sessions, route, mapper)
        synchronized(lock) {
            layers[drawingId] = layer
            val q = byDrawing.getOrPut(drawingId) { ArrayDeque() }
            q.clear()
            layer.sourceSessionIds.forEach { sid ->
                q.addLast(
                    HeatMapRef(
                        drawingId = drawingId,
                        sessionId = sid,
                        label = "HeatLayer",
                        pointCount = layer.pointCount,
                    ),
                )
            }
            if (layer.sourceSessionIds.isEmpty()) {
                q.addLast(
                    HeatMapRef(
                        drawingId = drawingId,
                        sessionId = "",
                        label = "HeatLayer",
                        pointCount = 0,
                    ),
                )
            }
        }
        return layer
    }

    fun loadHeatLayer(drawingId: String): DrawingHeatLayer? =
        synchronized(lock) { layers[drawingId] }

    fun deleteHeatLayer(drawingId: String) {
        synchronized(lock) {
            layers.remove(drawingId)
            byDrawing.remove(drawingId)
        }
    }

    /**
     * Rebuild layer from current Inspection history for the Drawing.
     * Does not permanently discard until new layer is written (replace in place).
     */
    fun regenerateHeatLayer(
        drawingId: String,
        inspectionRepository: InspectionRepository,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): DrawingHeatLayer {
        val summaries = inspectionRepository.loadHistory(drawingId)
        val sessions = summaries.mapNotNull { inspectionRepository.loadSession(it.sessionId) }
        return generateHeatLayer(drawingId, sessions, route, mapper)
    }

    fun removeForDrawing(drawingId: String) {
        deleteHeatLayer(drawingId)
    }

    fun clear() {
        synchronized(lock) {
            byDrawing.clear()
            layers.clear()
        }
    }
}
