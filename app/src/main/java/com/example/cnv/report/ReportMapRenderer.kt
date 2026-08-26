package com.example.cnv.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.cnv.imu.ShockUnits
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.inspection.RouteSnapshot
import com.example.cnv.inspection.RouteSnapshotCodec
import com.example.cnv.heatmap.DrawingHeatPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Renders route + critical shock markers for Maintenance / Excel reports. */
object ReportMapRenderer {

    data class CriticalPoint(
        val x: Double,
        val y: Double,
        val shockG: Float,
        val routePositionMm: Float = 0f,
    )

    data class Input(
        val routePolyline: List<Pair<Double, Double>>,
        val criticalPoints: List<CriticalPoint>,
        val width: Int = 960,
        val height: Int = 640,
    )

    fun renderToFile(input: Input, output: File): Boolean {
        val bitmap = renderBitmap(input) ?: return false
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
        return output.exists() && output.length() > 0L
    }

    fun renderBitmap(input: Input): Bitmap? {
        if (input.routePolyline.isEmpty() && input.criticalPoints.isEmpty()) return null
        val bitmap = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#121212"))

        val bounds = computeBounds(input.routePolyline, input.criticalPoints)
        if (bounds == null) return null
        val (minX, minY, maxX, maxY) = bounds
        val spanX = max(1e-6, maxX - minX)
        val spanY = max(1e-6, maxY - minY)
        val pad = 48f
        val usableW = (input.width - pad * 2).coerceAtLeast(1f)
        val usableH = (input.height - pad * 2).coerceAtLeast(1f)
        val scale = min(usableW / spanX.toFloat(), usableH / spanY.toFloat())
        val ox = pad + (usableW - spanX.toFloat() * scale) / 2f
        val oy = pad + (usableH - spanY.toFloat() * scale) / 2f

        fun mapX(x: Double): Float = ox + ((x - minX) * scale).toFloat()
        fun mapY(y: Double): Float = oy + ((y - minY) * scale).toFloat()

        if (input.routePolyline.size >= 2) {
            val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#757575")
                strokeWidth = max(2f, 3f * (scale / 40f).coerceIn(0.6f, 2.5f))
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            input.routePolyline.forEachIndexed { index, (x, y) ->
                val sx = mapX(x)
                val sy = mapY(y)
                if (index == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            canvas.drawPath(path, routePaint)
        }

        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF5252")
            style = Paint.Style.FILL
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFAB91")
            style = Paint.Style.STROKE
            strokeWidth = max(2f, 2.5f * (scale / 40f).coerceIn(0.6f, 2f))
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = max(18f, 22f * (scale / 40f).coerceIn(0.7f, 1.6f))
        }
        val baseRadius = max(8f, 10f * (scale / 40f).coerceIn(0.6f, 2.2f))
        for (point in input.criticalPoints) {
            val sx = mapX(point.x)
            val sy = mapY(point.y)
            val radius = baseRadius * (1f + (point.shockG - ShockUnits.criticalThresholdG()).coerceAtLeast(0f) * 0.35f)
                .coerceIn(1f, 1.8f)
            canvas.drawCircle(sx, sy, radius.coerceIn(8f, 22f), markerPaint)
            canvas.drawCircle(sx, sy, radius.coerceIn(8f, 22f) + 4f, ringPaint)
            canvas.drawText("%.2fg".format(point.shockG), sx + radius + 4f, sy + 4f, labelPaint)
        }

        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0B0B0")
            textSize = 24f
        }
        canvas.drawText("Critical shocks only (≥ ${"%.2f".format(ShockUnits.criticalThresholdG())}g)", 24f, 32f, legendPaint)
        return bitmap
    }

    private fun computeBounds(
        route: List<Pair<Double, Double>>,
        points: List<CriticalPoint>,
    ): Quad? {
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun touch(x: Double, y: Double) {
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        route.forEach { (x, y) -> touch(x, y) }
        points.forEach { touch(it.x, it.y) }
        if (!minX.isFinite()) return null
        return Quad(minX, minY, maxX, maxY)
    }

    private data class Quad(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)
}

object ReportMapBuilder {

    fun fromSessionData(
        routeSnapshotJson: String,
        heatPoints: List<DrawingHeatPoint>,
        events: List<PersistedInspectionEvent>,
    ): ReportMapRenderer.Input {
        val snapshot = RouteSnapshotCodec.decode(routeSnapshotJson)
        return ReportMapRenderer.Input(
            routePolyline = routePolyline(snapshot),
            criticalPoints = criticalPoints(heatPoints, events),
        )
    }

    fun criticalPoints(
        heatPoints: List<DrawingHeatPoint>,
        events: List<PersistedInspectionEvent>,
    ): List<ReportMapRenderer.CriticalPoint> {
        val fromEvents = events.mapNotNull { event ->
            if (!event.hasShock) return@mapNotNull null
            val g = event.peakG.coerceAtLeast(event.shockStrength)
            if (!ShockUnits.isCriticalG(g)) return@mapNotNull null
            if (event.worldX == 0f && event.worldY == 0f) return@mapNotNull null
            ReportMapRenderer.CriticalPoint(
                x = event.worldX.toDouble(),
                y = event.worldY.toDouble(),
                shockG = g,
                routePositionMm = event.routePositionMm,
            )
        }
        if (fromEvents.isNotEmpty()) return fromEvents.distinctBy { "${it.x}:${it.y}:${it.shockG}" }

        return heatPoints
            .filter { ShockUnits.isCriticalG(it.shockStrength) }
            .map {
                ReportMapRenderer.CriticalPoint(
                    x = it.drawingX,
                    y = it.drawingY,
                    shockG = it.shockStrength,
                    routePositionMm = it.routePositionMm,
                )
            }
            .distinctBy { "${it.x}:${it.y}:${it.shockG}" }
    }

    fun routePolyline(snapshot: RouteSnapshot?): List<Pair<Double, Double>> {
        if (snapshot == null || snapshot.segments.isEmpty()) return emptyList()
        val points = ArrayList<Pair<Double, Double>>()
        for (segment in snapshot.segments) {
            val geom = snapshot.segmentGeometry[segment.id] ?: continue
            if (points.isEmpty()) points.add(geom.start.x to geom.start.y)
            points.add(geom.end.x to geom.end.y)
        }
        return points
    }
}
