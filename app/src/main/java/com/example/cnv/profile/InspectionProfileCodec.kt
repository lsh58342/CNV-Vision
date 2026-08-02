package com.example.cnv.profile

import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.rule.RuleSeverity
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for [InspectionProfile] / [InspectionProfileSnapshot] (STEP 19-2).
 */
object InspectionProfileCodec {

    fun encodeSnapshot(snapshot: InspectionProfileSnapshot): String {
        val root = JSONObject()
        root.put("capturedAtMs", snapshot.capturedAtMs)
        root.put("conveyor", encodeConveyorSnap(snapshot.conveyor))
        root.put("sensor", encodeSensor(snapshot.sensor))
        root.put("rule", encodeRule(snapshot.rule))
        root.put("export", encodeExport(snapshot.export))
        return root.toString()
    }

    fun decodeSnapshot(json: String?): InspectionProfileSnapshot {
        if (json.isNullOrBlank()) return InspectionProfileSnapshot.empty()
        return runCatching {
            val root = JSONObject(json)
            InspectionProfileSnapshot(
                conveyor = decodeConveyorSnap(root.optJSONObject("conveyor")),
                sensor = decodeSensor(root.optJSONObject("sensor")),
                rule = decodeRule(root.optJSONObject("rule")),
                export = decodeExport(root.optJSONObject("export")),
                capturedAtMs = root.optLong("capturedAtMs", 0L),
            )
        }.getOrElse { InspectionProfileSnapshot.empty() }
    }

    fun encodeProfile(profile: InspectionProfile): String {
        val root = JSONObject()
        root.put("updatedAtMs", profile.updatedAtMs)
        root.put("conveyor", encodeConveyor(profile.conveyor))
        root.put("sensor", encodeSensor(profile.sensor))
        root.put("rule", encodeRule(profile.rule))
        root.put("export", encodeExport(profile.export))
        return root.toString()
    }

    fun decodeProfile(json: String?, conveyorFallback: ConveyorProfile = ConveyorProfile.fromConfig()): InspectionProfile {
        if (json.isNullOrBlank()) return InspectionProfile.default(conveyorFallback)
        return runCatching {
            val root = JSONObject(json)
            InspectionProfile(
                conveyor = decodeConveyor(root.optJSONObject("conveyor"), conveyorFallback),
                sensor = decodeSensor(root.optJSONObject("sensor")),
                rule = decodeRule(root.optJSONObject("rule")),
                export = decodeExport(root.optJSONObject("export")),
                updatedAtMs = root.optLong("updatedAtMs", 0L),
            )
        }.getOrElse { InspectionProfile.default(conveyorFallback) }
    }

    private fun encodeConveyor(p: ConveyorProfile) = JSONObject().apply {
        put("nominalSpeedMPerMin", p.nominalSpeedMPerMin ?: JSONObject.NULL)
        put("speedTolerancePercent", p.speedTolerancePercent.toDouble())
        put("direction", p.direction.name)
        put("expectedFps", p.expectedFps.toDouble())
        put("motionProfile", p.motionProfile.name)
    }

    private fun encodeConveyorSnap(p: ConveyorProfileSnapshot) = JSONObject().apply {
        put("nominalSpeedMPerMin", p.nominalSpeedMPerMin ?: JSONObject.NULL)
        put("speedTolerancePercent", p.speedTolerancePercent.toDouble())
        put("direction", p.direction.name)
        put("expectedFps", p.expectedFps.toDouble())
        put("motionProfile", p.motionProfile.name)
    }

    private fun decodeConveyor(o: JSONObject?, fallback: ConveyorProfile): ConveyorProfile {
        if (o == null) return fallback
        return ConveyorProfile(
            nominalSpeedMPerMin = if (o.isNull("nominalSpeedMPerMin")) null
            else o.optDouble("nominalSpeedMPerMin").toFloat(),
            speedTolerancePercent = o.optDouble("speedTolerancePercent", fallback.speedTolerancePercent.toDouble()).toFloat(),
            direction = runCatching { ConveyorDirection.valueOf(o.optString("direction")) }
                .getOrDefault(fallback.direction),
            expectedFps = o.optDouble("expectedFps", fallback.expectedFps.toDouble()).toFloat(),
            motionProfile = runCatching { ConveyorMotionProfile.valueOf(o.optString("motionProfile")) }
                .getOrDefault(fallback.motionProfile),
        )
    }

    private fun decodeConveyorSnap(o: JSONObject?): ConveyorProfileSnapshot {
        if (o == null) return ConveyorProfileSnapshot.empty()
        return ConveyorProfileSnapshot(
            nominalSpeedMPerMin = if (o.isNull("nominalSpeedMPerMin")) null
            else o.optDouble("nominalSpeedMPerMin").toFloat(),
            speedTolerancePercent = o.optDouble("speedTolerancePercent", 5.0).toFloat(),
            direction = runCatching { ConveyorDirection.valueOf(o.optString("direction")) }
                .getOrDefault(ConveyorDirection.FORWARD),
            expectedFps = o.optDouble("expectedFps", 30.0).toFloat(),
            motionProfile = runCatching { ConveyorMotionProfile.valueOf(o.optString("motionProfile")) }
                .getOrDefault(ConveyorMotionProfile.CONSTANT),
        )
    }

    private fun encodeSensor(s: SensorProfile) = JSONObject().apply {
        put("gravityFilterAlpha", s.gravityFilterAlpha.toDouble())
        put("highPassAlpha", s.highPassAlpha.toDouble())
        put("minimumShockThreshold", s.minimumShockThreshold.toDouble())
        put("peakIntervalNs", s.peakIntervalNs)
        put("movingAverageWindow", s.movingAverageWindow)
        put("trackingConfidenceThreshold", s.trackingConfidenceThreshold.toDouble())
    }

    private fun decodeSensor(o: JSONObject?): SensorProfile {
        if (o == null) return SensorProfile.DEFAULT
        val d = SensorProfile.DEFAULT
        return SensorProfile(
            gravityFilterAlpha = o.optDouble("gravityFilterAlpha", d.gravityFilterAlpha.toDouble()).toFloat(),
            highPassAlpha = o.optDouble("highPassAlpha", d.highPassAlpha.toDouble()).toFloat(),
            minimumShockThreshold = o.optDouble("minimumShockThreshold", d.minimumShockThreshold.toDouble()).toFloat(),
            peakIntervalNs = o.optLong("peakIntervalNs", d.peakIntervalNs),
            movingAverageWindow = o.optInt("movingAverageWindow", d.movingAverageWindow),
            trackingConfidenceThreshold = o.optDouble(
                "trackingConfidenceThreshold",
                d.trackingConfidenceThreshold.toDouble(),
            ).toFloat(),
        )
    }

    private fun encodeRule(r: RuleProfile) = JSONObject().apply {
        put("catalogVersion", r.catalogVersion)
        val arr = JSONArray()
        for (e in r.entries) {
            arr.put(
                JSONObject().apply {
                    put("ruleId", e.ruleId)
                    put("enabled", e.enabled)
                    put("ruleVersion", e.ruleVersion)
                    put("thresholdOverride", e.thresholdOverride ?: JSONObject.NULL)
                    put("severityOverride", e.severityOverride?.name ?: JSONObject.NULL)
                },
            )
        }
        put("entries", arr)
    }

    private fun decodeRule(o: JSONObject?): RuleProfile {
        if (o == null) return RuleProfile.DEFAULT
        val arr = o.optJSONArray("entries") ?: JSONArray()
        val entries = ArrayList<RuleProfileEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            entries += RuleProfileEntry(
                ruleId = e.optString("ruleId"),
                enabled = e.optBoolean("enabled", true),
                ruleVersion = e.optInt("ruleVersion", 1),
                thresholdOverride = if (e.isNull("thresholdOverride")) null
                else e.optDouble("thresholdOverride").toFloat(),
                severityOverride = e.optString("severityOverride").takeIf { it.isNotBlank() }?.let {
                    runCatching { RuleSeverity.valueOf(it) }.getOrNull()
                },
            )
        }
        return RuleProfile(catalogVersion = o.optInt("catalogVersion", 1), entries = entries)
    }

    private fun encodeExport(p: ExportProfile) = JSONObject().apply {
        put("excelVersion", p.excelVersion)
        put("exportOption", p.exportOption)
        put("timeFormat", p.timeFormat)
        put("coordinateFormat", p.coordinateFormat)
    }

    private fun decodeExport(o: JSONObject?): ExportProfile {
        if (o == null) return ExportProfile.DEFAULT
        val d = ExportProfile.DEFAULT
        return ExportProfile(
            excelVersion = o.optInt("excelVersion", d.excelVersion),
            exportOption = o.optString("exportOption", d.exportOption),
            timeFormat = o.optString("timeFormat", d.timeFormat),
            coordinateFormat = o.optString("coordinateFormat", d.coordinateFormat),
        )
    }
}
