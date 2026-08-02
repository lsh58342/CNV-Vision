package com.example.cnv.analysis

import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfileConfig
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persist [InspectionAnalysisResult] with Session (STEP 20-3).
 * History / Excel / Review must use stored result — not re-analyze.
 */
object AnalysisResultCodec {

    fun encode(result: InspectionAnalysisResult): String {
        val root = JSONObject()
        root.put("sessionId", result.sessionId)
        root.put("drawingId", result.drawingId)
        root.put("analyzedAtMs", result.analyzedAtMs)
        root.put("validationScore", result.validationScore.toDouble())
        root.put("summary", encodeSummary(result.summary))
        root.put("distance", encodeDistance(result.distance))
        root.put("speed", encodeSpeed(result.speed))
        root.put("tracking", encodeTracking(result.tracking))
        root.put("shock", encodeShock(result.shock))
        root.put("coverage", encodeCoverage(result.coverage))
        root.put("conveyorProfile", encodeConveyor(result.conveyorProfile))
        val zones = JSONArray()
        result.zones.forEach { z ->
            zones.put(
                JSONObject()
                    .put("zoneId", z.zoneId)
                    .put("zoneName", z.zoneName)
                    .put("distanceMm", z.distanceMm.toDouble())
                    .put("shockCount", z.shockCount)
                    .put("coverage", z.coverage.toDouble())
                    .put("inspectionTimeMs", z.inspectionTimeMs),
            )
        }
        root.put("zones", zones)
        return root.toString()
    }

    fun decode(json: String?): InspectionAnalysisResult? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val zones = ArrayList<ZoneStatistics>()
            val zonesArr = root.optJSONArray("zones") ?: JSONArray()
            for (i in 0 until zonesArr.length()) {
                val o = zonesArr.getJSONObject(i)
                zones += ZoneStatistics(
                    zoneId = o.optString("zoneId", ""),
                    zoneName = o.optString("zoneName", ""),
                    distanceMm = o.optDouble("distanceMm", 0.0).toFloat(),
                    shockCount = o.optInt("shockCount", 0),
                    coverage = o.optDouble("coverage", 0.0).toFloat(),
                    inspectionTimeMs = o.optLong("inspectionTimeMs", 0L),
                )
            }
            InspectionAnalysisResult(
                sessionId = root.optString("sessionId", ""),
                drawingId = root.optString("drawingId", ""),
                analyzedAtMs = root.optLong("analyzedAtMs", 0L),
                summary = decodeSummary(root.optJSONObject("summary")),
                distance = decodeDistance(root.optJSONObject("distance")),
                speed = decodeSpeed(root.optJSONObject("speed")),
                tracking = decodeTracking(root.optJSONObject("tracking")),
                shock = decodeShock(root.optJSONObject("shock")),
                zones = zones,
                coverage = decodeCoverage(root.optJSONObject("coverage")),
                validationScore = root.optDouble("validationScore", 0.0).toFloat(),
                conveyorProfile = decodeConveyor(root.optJSONObject("conveyorProfile")),
            )
        }.getOrNull()
    }

    private fun encodeSummary(s: InspectionAnalysisSummary) = JSONObject()
        .put("startTimeMs", s.startTimeMs)
        .put("endTimeMs", s.endTimeMs)
        .put("durationMs", s.durationMs)
        .put("inspectionVersion", s.inspectionVersion)
        .put("appVersion", s.appVersion)
        .put("eventCount", s.eventCount)

    private fun decodeSummary(o: JSONObject?) = InspectionAnalysisSummary(
        startTimeMs = o?.optLong("startTimeMs", 0L) ?: 0L,
        endTimeMs = o?.optLong("endTimeMs", 0L) ?: 0L,
        durationMs = o?.optLong("durationMs", 0L) ?: 0L,
        inspectionVersion = o?.optString("inspectionVersion", "").orEmpty(),
        appVersion = o?.optString("appVersion", "").orEmpty(),
        eventCount = o?.optInt("eventCount", 0) ?: 0,
    )

    private fun encodeDistance(d: DistanceStatistics) = JSONObject()
        .put("totalDistanceMm", d.totalDistanceMm.toDouble())
        .put("averageDistanceMm", d.averageDistanceMm.toDouble())
        .put("maximumDeltaMm", d.maximumDeltaMm.toDouble())
        .put("minimumDeltaMm", d.minimumDeltaMm.toDouble())

    private fun decodeDistance(o: JSONObject?) = DistanceStatistics(
        totalDistanceMm = o?.optDouble("totalDistanceMm", 0.0)?.toFloat() ?: 0f,
        averageDistanceMm = o?.optDouble("averageDistanceMm", 0.0)?.toFloat() ?: 0f,
        maximumDeltaMm = o?.optDouble("maximumDeltaMm", 0.0)?.toFloat() ?: 0f,
        minimumDeltaMm = o?.optDouble("minimumDeltaMm", 0.0)?.toFloat() ?: 0f,
    )

    private fun encodeSpeed(s: SpeedStatistics) = JSONObject()
        .put("averageSpeedMmPerSec", s.averageSpeedMmPerSec.toDouble())
        .put("maximumSpeedMmPerSec", s.maximumSpeedMmPerSec.toDouble())
        .put("minimumSpeedMmPerSec", s.minimumSpeedMmPerSec.toDouble())
        .put("nominalSpeedMPerMin", s.nominalSpeedMPerMin ?: JSONObject.NULL)
        .put("speedDifferenceMmPerSec", s.speedDifferenceMmPerSec.toDouble())

    private fun decodeSpeed(o: JSONObject?) = SpeedStatistics(
        averageSpeedMmPerSec = o?.optDouble("averageSpeedMmPerSec", 0.0)?.toFloat() ?: 0f,
        maximumSpeedMmPerSec = o?.optDouble("maximumSpeedMmPerSec", 0.0)?.toFloat() ?: 0f,
        minimumSpeedMmPerSec = o?.optDouble("minimumSpeedMmPerSec", 0.0)?.toFloat() ?: 0f,
        nominalSpeedMPerMin = if (o == null || o.isNull("nominalSpeedMPerMin")) {
            null
        } else {
            o.optDouble("nominalSpeedMPerMin").toFloat()
        },
        speedDifferenceMmPerSec = o?.optDouble("speedDifferenceMmPerSec", 0.0)?.toFloat() ?: 0f,
    )

    private fun encodeTracking(t: TrackingStatistics) = JSONObject()
        .put("averageConfidence", t.averageConfidence.toDouble())
        .put("minimumConfidence", t.minimumConfidence.toDouble())
        .put("lowConfidenceCount", t.lowConfidenceCount)
        .put("trackingLossCount", t.trackingLossCount)

    private fun decodeTracking(o: JSONObject?) = TrackingStatistics(
        averageConfidence = o?.optDouble("averageConfidence", 0.0)?.toFloat() ?: 0f,
        minimumConfidence = o?.optDouble("minimumConfidence", 0.0)?.toFloat() ?: 0f,
        lowConfidenceCount = o?.optInt("lowConfidenceCount", 0) ?: 0,
        trackingLossCount = o?.optInt("trackingLossCount", 0) ?: 0,
    )

    private fun encodeShock(s: ShockStatistics) = JSONObject()
        .put("shockCount", s.shockCount)
        .put("maximumShock", s.maximumShock.toDouble())
        .put("averageShock", s.averageShock.toDouble())
        .put("shockDensityPerMeter", s.shockDensityPerMeter.toDouble())

    private fun decodeShock(o: JSONObject?) = ShockStatistics(
        shockCount = o?.optInt("shockCount", 0) ?: 0,
        maximumShock = o?.optDouble("maximumShock", 0.0)?.toFloat() ?: 0f,
        averageShock = o?.optDouble("averageShock", 0.0)?.toFloat() ?: 0f,
        shockDensityPerMeter = o?.optDouble("shockDensityPerMeter", 0.0)?.toFloat() ?: 0f,
    )

    private fun encodeCoverage(c: CoverageStatistics) = JSONObject()
        .put("drawingCoverage", c.drawingCoverage.toDouble())
        .put("routeCoverage", c.routeCoverage.toDouble())
        .put("inspectionRatio", c.inspectionRatio.toDouble())

    private fun decodeCoverage(o: JSONObject?) = CoverageStatistics(
        drawingCoverage = o?.optDouble("drawingCoverage", 0.0)?.toFloat() ?: 0f,
        routeCoverage = o?.optDouble("routeCoverage", 0.0)?.toFloat() ?: 0f,
        inspectionRatio = o?.optDouble("inspectionRatio", 0.0)?.toFloat() ?: 0f,
    )

    private fun encodeConveyor(p: ConveyorProfileSnapshot) = JSONObject()
        .put("nominalSpeedMPerMin", p.nominalSpeedMPerMin ?: JSONObject.NULL)
        .put("speedTolerancePercent", p.speedTolerancePercent.toDouble())
        .put("direction", p.direction.name)
        .put("expectedFps", p.expectedFps.toDouble())
        .put("motionProfile", p.motionProfile.name)

    private fun decodeConveyor(o: JSONObject?) = ConveyorProfileSnapshot(
        nominalSpeedMPerMin = if (o == null || o.isNull("nominalSpeedMPerMin")) {
            null
        } else {
            o.optDouble("nominalSpeedMPerMin").toFloat()
        },
        direction = runCatching {
            ConveyorDirection.valueOf(o?.optString("direction", ConveyorDirection.FORWARD.name).orEmpty())
        }.getOrDefault(ConveyorDirection.FORWARD),
        expectedFps = o?.optDouble("expectedFps", 0.0)?.toFloat() ?: 0f,
        motionProfile = runCatching {
            ConveyorMotionProfile.valueOf(
                o?.optString("motionProfile", ConveyorMotionProfile.CONSTANT.name).orEmpty(),
            )
        }.getOrDefault(ConveyorMotionProfile.CONSTANT),
        speedTolerancePercent = o?.optDouble("speedTolerancePercent", 0.0)?.toFloat()
            ?.takeIf { it > 0f }
            ?: ConveyorProfileConfig.DEFAULT_SPEED_TOLERANCE_PERCENT,
    )
}
