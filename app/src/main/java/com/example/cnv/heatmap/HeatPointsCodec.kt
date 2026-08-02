package com.example.cnv.heatmap

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal HeatMap point persistence for History reproducibility (STEP 20-3).
 * Does not store Bitmaps — only [DrawingHeatPoint] payloads.
 */
object HeatPointsCodec {

    fun encode(points: List<DrawingHeatPoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("drawingX", p.drawingX)
                    .put("drawingY", p.drawingY)
                    .put("shockStrength", p.shockStrength.toDouble())
                    .put("intensity", p.intensity.name)
                    .put("timestampNs", p.timestampNs)
                    .put("routePositionMm", p.routePositionMm.toDouble())
                    .put("routePositionLabel", p.routePositionLabel)
                    .put("sessionId", p.sessionId),
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<DrawingHeatPoint> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val out = ArrayList<DrawingHeatPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += DrawingHeatPoint(
                    drawingX = o.optDouble("drawingX", 0.0),
                    drawingY = o.optDouble("drawingY", 0.0),
                    shockStrength = o.optDouble("shockStrength", 0.0).toFloat(),
                    intensity = runCatching {
                        HeatIntensity.valueOf(o.optString("intensity", HeatIntensity.LOW.name))
                    }.getOrDefault(HeatIntensity.LOW),
                    timestampNs = o.optLong("timestampNs", 0L),
                    routePositionMm = o.optDouble("routePositionMm", 0.0).toFloat(),
                    routePositionLabel = o.optString("routePositionLabel", ""),
                    sessionId = o.optString("sessionId", ""),
                )
            }
            out
        }.getOrElse { emptyList() }
    }
}
