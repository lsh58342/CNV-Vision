package com.example.cnv.heatmap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.example.cnv.cad.CADCamera

/**
 * HeatMap Viewer overlay — renders Repository [DrawingHeatLayer] only (STEP 15).
 * Does not generate HeatPoints or run HeatMapGenerator.
 */
class HeatLayerViewerOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var cameraProvider: (() -> CADCamera?)? = null
    private var points: List<DrawingHeatPoint> = emptyList()
    private var routePolyline: List<Pair<Double, Double>> = emptyList()
    private var origin: Pair<Double, Double>? = null
    private var zones: List<HeatMapZoneOverlay> = emptyList()
    private var highlightedZoneId: String? = null
    private var flags = HeatMapViewerLayerFlags()
    private var config = HeatMapDisplayConfig.DEFAULT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val zoneTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 26f
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun setCameraProvider(provider: () -> CADCamera?) {
        cameraProvider = provider
    }

    fun setDisplayConfig(cfg: HeatMapDisplayConfig) {
        config = cfg
        invalidate()
    }

    fun setLayerFlags(f: HeatMapViewerLayerFlags) {
        flags = f
        invalidate()
    }

    fun setHeatPoints(data: List<DrawingHeatPoint>) {
        points = data
        invalidate()
    }

    fun setRoutePolyline(data: List<Pair<Double, Double>>) {
        routePolyline = data
        invalidate()
    }

    fun setOriginWorld(xy: Pair<Double, Double>?) {
        origin = xy
        invalidate()
    }

    fun setZones(data: List<HeatMapZoneOverlay>) {
        zones = data
        invalidate()
    }

    fun setHighlightedZone(zoneId: String?) {
        highlightedZoneId = zoneId
        invalidate()
    }

    /** Hit-test zone near view coordinates (used by Screen; does not create HeatPoints). */
    fun hitTestZone(viewX: Float, viewY: Float): String? {
        if (!flags.zone) return null
        val camera = cameraProvider?.invoke() ?: return null
        val wx = camera.viewToWorldX(viewX)
        val wy = camera.viewToWorldY(viewY)
        return zones.firstOrNull { z ->
            z.points.any { (x, y) ->
                val dx = x - wx
                val dy = y - wy
                dx * dx + dy * dy <= ZONE_HIT_RADIUS_WORLD * ZONE_HIT_RADIUS_WORLD
            }
        }?.zoneId
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val camera = cameraProvider?.invoke() ?: return

        if (flags.route && routePolyline.size >= 2) {
            routePaint.color = config.routeColor
            for (i in 1 until routePolyline.size) {
                val (x0, y0) = routePolyline[i - 1]
                val (x1, y1) = routePolyline[i]
                canvas.drawLine(
                    camera.worldToViewX(x0),
                    camera.worldToViewY(y0),
                    camera.worldToViewX(x1),
                    camera.worldToViewY(y1),
                    routePaint,
                )
            }
        }

        if (flags.zone) {
            for (z in zones) {
                val highlight = z.zoneId == highlightedZoneId
                zonePaint.color = if (highlight) config.zoneHighlightColor else z.colorArgb
                zonePaint.strokeWidth = if (highlight) 6f else 4f
                if (z.points.size >= 2) {
                    for (i in 1 until z.points.size) {
                        val (x0, y0) = z.points[i - 1]
                        val (x1, y1) = z.points[i]
                        canvas.drawLine(
                            camera.worldToViewX(x0),
                            camera.worldToViewY(y0),
                            camera.worldToViewX(x1),
                            camera.worldToViewY(y1),
                            zonePaint,
                        )
                    }
                }
                val labelAt = z.points.firstOrNull() ?: continue
                canvas.drawText(
                    z.name,
                    camera.worldToViewX(labelAt.first) + 8f,
                    camera.worldToViewY(labelAt.second) - 8f,
                    zoneTextPaint,
                )
            }
        }

        if (flags.heatMap) {
            for (p in points) {
                fillPaint.color = config.colorFor(p.intensity)
                canvas.drawCircle(
                    camera.worldToViewX(p.drawingX),
                    camera.worldToViewY(p.drawingY),
                    config.pointRadiusPx,
                    fillPaint,
                )
                if (flags.shock && p.shockStrength >= config.shockEmphasisMinStrength) {
                    strokePaint.color = config.shockEmphasisColor
                    canvas.drawCircle(
                        camera.worldToViewX(p.drawingX),
                        camera.worldToViewY(p.drawingY),
                        config.shockRingRadiusPx,
                        strokePaint,
                    )
                }
            }
        } else if (flags.shock) {
            for (p in points) {
                if (p.shockStrength < config.shockEmphasisMinStrength) continue
                strokePaint.color = config.shockEmphasisColor
                canvas.drawCircle(
                    camera.worldToViewX(p.drawingX),
                    camera.worldToViewY(p.drawingY),
                    config.shockRingRadiusPx,
                    strokePaint,
                )
            }
        }

        if (flags.origin) {
            origin?.let { (x, y) ->
                fillPaint.color = config.originColor
                canvas.drawCircle(
                    camera.worldToViewX(x),
                    camera.worldToViewY(y),
                    config.originRadiusPx,
                    fillPaint,
                )
            }
        }

        drawLegend(canvas)
    }

    private fun drawLegend(canvas: Canvas) {
        val left = width - LEGEND_WIDTH - LEGEND_MARGIN
        var top = LEGEND_MARGIN
        val items = listOf(
            HeatIntensity.LOW to "LOW",
            HeatIntensity.MEDIUM to "MEDIUM",
            HeatIntensity.HIGH to "HIGH",
            HeatIntensity.CRITICAL to "CRITICAL",
        )
        for ((intensity, label) in items) {
            legendPaint.color = config.colorFor(intensity)
            canvas.drawCircle(left + 16f, top + 14f, 10f, legendPaint)
            canvas.drawText(label, left + 36f, top + 20f, legendTextPaint)
            top += LEGEND_ROW
        }
    }

    companion object {
        private const val LEGEND_MARGIN = 24f
        private const val LEGEND_WIDTH = 160f
        private const val LEGEND_ROW = 36f
        private const val ZONE_HIT_RADIUS_WORLD = 40.0
    }
}
