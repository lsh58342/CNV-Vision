package com.example.cnv.heatmap

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.example.cnv.cad.CADCamera

/**
 * Transparent Shock HeatMap overlay on CAD. Touches pass through to CAD.
 */
class HeatMapOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val renderer = HeatMapRenderer()

    private var cells: List<HeatCell> = emptyList()
    private var stats: HeatStatistics = HeatStatistics.EMPTY
    private var cameraProvider: (() -> CADCamera?)? = null
    private var enabled = true

    private var frameCount = 0
    private var fpsWindowStartNs = 0L
    private var currentFps = 0.0

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun setCameraProvider(provider: () -> CADCamera?) {
        cameraProvider = provider
    }

    fun setShockHeatData(cells: List<HeatCell>, statistics: HeatStatistics) {
        this.cells = cells
        this.stats = statistics
        invalidate()
    }

    fun setOverlayEnabled(value: Boolean) {
        enabled = value
        invalidate()
    }

    fun debugSnapshot(): HeatMapDebugSnapshot = HeatMapDebugSnapshot(
        fps = currentFps,
        renderTimeMs = renderer.lastStats.renderTimeMs,
        visibleCellCount = renderer.lastStats.visibleCellCount,
        heatPointCount = stats.heatPointCount,
        maximumShock = stats.maximumShock,
        averageShock = stats.averageShock,
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        trackFps()
        if (!enabled || cells.isEmpty()) return
        val camera = cameraProvider?.invoke() ?: return
        renderer.draw(
            canvas = canvas,
            cells = cells,
            camera = camera,
            maxShock = stats.maximumShock.coerceAtLeast(1f),
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    private fun trackFps() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (fpsWindowStartNs == 0L) fpsWindowStartNs = now
        frameCount++
        val elapsed = now - fpsWindowStartNs
        if (elapsed >= 1_000_000_000L) {
            currentFps = frameCount * 1_000_000_000.0 / elapsed
            frameCount = 0
            fpsWindowStartNs = now
        }
    }
}

data class HeatMapDebugSnapshot(
    val fps: Double,
    val renderTimeMs: Double,
    val visibleCellCount: Int,
    val heatPointCount: Int,
    val maximumShock: Float,
    val averageShock: Float,
)
