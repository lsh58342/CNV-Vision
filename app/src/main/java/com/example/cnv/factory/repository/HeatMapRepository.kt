package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.heatmap.DrawingHeatLayer
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.heatmap.HeatMapGenerator
import com.example.cnv.heatmap.HeatMapIntensityConfig
import com.example.cnv.heatmap.HeatPointsCodec
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.inspection.RouteSnapshotCodec
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper

/**
 * Drawing-scoped HeatMap store (STEP 14 / 20-3).
 * Holds generated [DrawingHeatLayer]s + lightweight refs for History.
 * Heat calculation is delegated only to [HeatMapGenerator] — never Viewer.
 * Session heat points are cached / restored from Session JSON for reproducibility.
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
    private val sessionPoints = LinkedHashMap<String, List<DrawingHeatPoint>>()
    private val layerListeners = mutableListOf<(String) -> Unit>()

    /** Viewer observes HeatLayer updates (no generation in UI). */
    fun addLayerListener(listener: (drawingId: String) -> Unit) {
        synchronized(lock) { layerListeners.add(listener) }
    }

    fun removeLayerListener(listener: (drawingId: String) -> Unit) {
        synchronized(lock) { layerListeners.remove(listener) }
    }

    private fun notifyLayerChanged(drawingId: String) {
        val copy = synchronized(lock) { layerListeners.toList() }
        copy.forEach { it(drawingId) }
    }

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
     * Uses each session's Route Snapshot when available (STEP 20-3).
     */
    fun generateHeatLayer(
        drawingId: String,
        sessions: List<PersistedInspectionSession>,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): DrawingHeatLayer {
        val allPoints = ArrayList<DrawingHeatPoint>()
        for (session in sessions) {
            val sessionRoute = RouteSnapshotCodec.decode(session.summary.routeSnapshotJson)?.toRoute()
                ?: route
            val points = generator.generatePoints(listOf(session), sessionRoute, mapper)
            synchronized(lock) {
                sessionPoints[session.summary.sessionId] = points
            }
            allPoints.addAll(points)
        }
        val layer = DrawingHeatLayer(
            drawingId = drawingId,
            points = allPoints,
            sourceSessionIds = sessions.map { it.summary.sessionId }.distinct(),
        )
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
        notifyLayerChanged(drawingId)
        return layer
    }

    /**
     * Generate + cache points for one finished session using its Route Snapshot.
     * Does not change HeatMapGenerator algorithms.
     */
    fun generateSessionPoints(
        drawingId: String,
        session: PersistedInspectionSession,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): List<DrawingHeatPoint> {
        val points = generator.generatePoints(listOf(session), route, mapper)
        synchronized(lock) {
            sessionPoints[session.summary.sessionId] = points
        }
        return points
    }

    fun loadHeatLayer(drawingId: String): DrawingHeatLayer? =
        synchronized(lock) { layers[drawingId] }

    /**
     * Display filter — memory layer, then session cache.
     * Call [restoreSessionPoints] on background to hydrate from Session JSON.
     */
    fun loadHeatPointsForSession(drawingId: String, sessionId: String): List<DrawingHeatPoint> {
        if (sessionId.isBlank()) {
            return loadHeatLayer(drawingId)?.points.orEmpty()
        }
        synchronized(lock) {
            sessionPoints[sessionId]?.let { return it }
        }
        val layer = loadHeatLayer(drawingId) ?: return emptyList()
        return layer.points.filter { it.sessionId == sessionId }
    }

    /**
     * Background-thread: hydrate session points from persisted JSON if memory miss.
     */
    fun restoreSessionPoints(sessionId: String, heatPointsJson: String): List<DrawingHeatPoint> {
        synchronized(lock) {
            sessionPoints[sessionId]?.let { return it }
        }
        val decoded = HeatPointsCodec.decode(heatPointsJson)
        if (decoded.isNotEmpty()) {
            synchronized(lock) {
                sessionPoints[sessionId] = decoded
            }
        }
        return decoded
    }

    /**
     * Remove one Session's points/refs from stored HeatLayer (no Generator call).
     */
    fun removeSessionFromLayer(drawingId: String, sessionId: String) {
        synchronized(lock) {
            sessionPoints.remove(sessionId)
            val layer = layers[drawingId]
            if (layer != null) {
                val filteredPoints = layer.points.filter { it.sessionId != sessionId }
                val filteredSources = layer.sourceSessionIds.filter { it != sessionId }
                if (filteredPoints.isEmpty() && filteredSources.isEmpty()) {
                    layers.remove(drawingId)
                } else {
                    layers[drawingId] = layer.copy(
                        points = filteredPoints,
                        sourceSessionIds = filteredSources,
                    )
                }
            }
            byDrawing[drawingId]?.removeAll { it.sessionId == sessionId }
            if (byDrawing[drawingId]?.isEmpty() == true) {
                byDrawing.remove(drawingId)
            }
        }
        notifyLayerChanged(drawingId)
    }

    fun deleteHeatLayer(drawingId: String) {
        synchronized(lock) {
            val layer = layers.remove(drawingId)
            layer?.sourceSessionIds?.forEach { sessionPoints.remove(it) }
            byDrawing.remove(drawingId)
        }
        notifyLayerChanged(drawingId)
    }

    /**
     * Rebuild layer from Inspection history for the Drawing.
     * Each session uses its own Route Snapshot when present (STEP 20-3).
     */
    fun regenerateHeatLayer(
        drawingId: String,
        inspectionRepository: InspectionRepository,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): DrawingHeatLayer {
        val summaries = inspectionRepository.loadHistory(drawingId)
        val sessions = summaries.mapNotNull { inspectionRepository.loadSession(it.sessionId) }
        val allPoints = ArrayList<DrawingHeatPoint>()
        val sourceIds = ArrayList<String>()
        for (session in sessions) {
            val fromJson = HeatPointsCodec.decode(session.summary.heatPointsJson)
            val points = if (fromJson.isNotEmpty()) {
                synchronized(lock) { sessionPoints[session.summary.sessionId] = fromJson }
                fromJson
            } else {
                val sessionRoute = RouteSnapshotCodec.decode(session.summary.routeSnapshotJson)?.toRoute()
                    ?: route
                generateSessionPoints(drawingId, session, sessionRoute, mapper)
            }
            allPoints.addAll(points)
            sourceIds += session.summary.sessionId
        }
        val layer = DrawingHeatLayer(
            drawingId = drawingId,
            points = allPoints,
            sourceSessionIds = sourceIds.distinct(),
        )
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
                        pointCount = layer.points.count { it.sessionId == sid },
                    ),
                )
            }
        }
        notifyLayerChanged(drawingId)
        return layer
    }

    fun removeForDrawing(drawingId: String) {
        deleteHeatLayer(drawingId)
    }

    fun clear() {
        synchronized(lock) {
            byDrawing.clear()
            layers.clear()
            sessionPoints.clear()
        }
    }
}
