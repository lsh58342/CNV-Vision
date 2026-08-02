package com.example.cnv.rule

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persist [InspectionRuleResult] with Session (STEP 20-3).
 * History / Review / Excel prefer stored result over re-evaluation.
 */
object RuleResultCodec {

    fun encode(result: InspectionRuleResult): String {
        val root = JSONObject()
        root.put("sessionId", result.sessionId)
        root.put("drawingId", result.drawingId)
        root.put("ruleCatalogVersion", result.ruleCatalogVersion)
        root.put("evaluatedAtMs", result.evaluatedAtMs)
        root.put("hits", encodeHits(result.hits))
        root.put("warnings", encodeWarnings(result.warnings))
        root.put("issues", encodeIssues(result.issues))
        root.put("zoneSummaries", encodeZoneSummaries(result.zoneSummaries))
        return root.toString()
    }

    fun decode(json: String?): InspectionRuleResult? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            InspectionRuleResult(
                sessionId = root.optString("sessionId", ""),
                drawingId = root.optString("drawingId", ""),
                ruleCatalogVersion = root.optInt("ruleCatalogVersion", 0),
                evaluatedAtMs = root.optLong("evaluatedAtMs", 0L),
                hits = decodeHits(root.optJSONArray("hits")),
                warnings = decodeWarnings(root.optJSONArray("warnings")),
                issues = decodeIssues(root.optJSONArray("issues")),
                zoneSummaries = decodeZoneSummaries(root.optJSONArray("zoneSummaries")),
            )
        }.getOrNull()
    }

    private fun encodeHits(hits: List<RuleHit>): JSONArray {
        val arr = JSONArray()
        hits.forEach { h ->
            arr.put(
                JSONObject()
                    .put("ruleId", h.ruleId)
                    .put("ruleVersion", h.ruleVersion)
                    .put("severity", h.severity.name)
                    .put("triggered", h.triggered)
                    .put("description", h.description)
                    .put("recommendation", h.recommendation.name)
                    .put("relatedBuildingId", h.relatedBuildingId ?: JSONObject.NULL)
                    .put("relatedFloorId", h.relatedFloorId ?: JSONObject.NULL)
                    .put("relatedDrawingId", h.relatedDrawingId ?: JSONObject.NULL)
                    .put("relatedZoneId", h.relatedZoneId ?: JSONObject.NULL)
                    .put("relatedZoneName", h.relatedZoneName ?: JSONObject.NULL)
                    .put("metricValue", h.metricValue.toDouble())
                    .put("timestampMs", h.timestampMs)
                    .put("category", h.category.name),
            )
        }
        return arr
    }

    private fun decodeHits(arr: JSONArray?): List<RuleHit> {
        if (arr == null) return emptyList()
        val out = ArrayList<RuleHit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += RuleHit(
                ruleId = o.optString("ruleId", ""),
                ruleVersion = o.optInt("ruleVersion", 0),
                severity = enumOr(o.optString("severity"), RuleSeverity.INFO),
                triggered = o.optBoolean("triggered", false),
                description = o.optString("description", ""),
                recommendation = enumOr(o.optString("recommendation"), RuleRecommendation.MANUAL_VERIFICATION),
                relatedBuildingId = o.optStringOrNull("relatedBuildingId"),
                relatedFloorId = o.optStringOrNull("relatedFloorId"),
                relatedDrawingId = o.optStringOrNull("relatedDrawingId"),
                relatedZoneId = o.optStringOrNull("relatedZoneId"),
                relatedZoneName = o.optStringOrNull("relatedZoneName"),
                metricValue = o.optDouble("metricValue", 0.0).toFloat(),
                timestampMs = o.optLong("timestampMs", 0L),
                category = enumOr(o.optString("category"), RuleCategory.SESSION),
            )
        }
        return out
    }

    private fun encodeWarnings(warnings: List<InspectionWarning>): JSONArray {
        val arr = JSONArray()
        warnings.forEach { w ->
            arr.put(
                JSONObject()
                    .put("ruleId", w.ruleId)
                    .put("severity", w.severity.name)
                    .put("label", w.label)
                    .put("detail", w.detail)
                    .put("recommendation", w.recommendation.name)
                    .put("category", w.category.name),
            )
        }
        return arr
    }

    private fun decodeWarnings(arr: JSONArray?): List<InspectionWarning> {
        if (arr == null) return emptyList()
        val out = ArrayList<InspectionWarning>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += InspectionWarning(
                ruleId = o.optString("ruleId", ""),
                severity = enumOr(o.optString("severity"), RuleSeverity.INFO),
                label = o.optString("label", ""),
                detail = o.optString("detail", ""),
                recommendation = enumOr(o.optString("recommendation"), RuleRecommendation.MANUAL_VERIFICATION),
                category = enumOr(o.optString("category"), RuleCategory.SESSION),
            )
        }
        return out
    }

    private fun encodeIssues(issues: List<InspectionIssue>): JSONArray {
        val arr = JSONArray()
        issues.forEach { issue ->
            arr.put(
                JSONObject()
                    .put("zoneId", issue.zoneId)
                    .put("zoneName", issue.zoneName)
                    .put("ruleId", issue.ruleId)
                    .put("severity", issue.severity.name)
                    .put("occurrenceCount", issue.occurrenceCount)
                    .put("recommendation", issue.recommendation.name)
                    .put("issueType", issue.issueType),
            )
        }
        return arr
    }

    private fun decodeIssues(arr: JSONArray?): List<InspectionIssue> {
        if (arr == null) return emptyList()
        val out = ArrayList<InspectionIssue>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += InspectionIssue(
                zoneId = o.optString("zoneId", ""),
                zoneName = o.optString("zoneName", ""),
                ruleId = o.optString("ruleId", ""),
                severity = enumOr(o.optString("severity"), RuleSeverity.INFO),
                occurrenceCount = o.optInt("occurrenceCount", 0),
                recommendation = enumOr(o.optString("recommendation"), RuleRecommendation.MANUAL_VERIFICATION),
                issueType = o.optString("issueType", ""),
            )
        }
        return out
    }

    private fun encodeZoneSummaries(zones: List<InspectionRuleZoneSummary>): JSONArray {
        val arr = JSONArray()
        zones.forEach { z ->
            arr.put(
                JSONObject()
                    .put("zoneId", z.zoneId)
                    .put("zoneName", z.zoneName)
                    .put("distanceMm", z.distanceMm.toDouble())
                    .put("shockCount", z.shockCount)
                    .put("coverage", z.coverage.toDouble())
                    .put("validationScore", z.validationScore.toDouble()),
            )
        }
        return arr
    }

    private fun decodeZoneSummaries(arr: JSONArray?): List<InspectionRuleZoneSummary> {
        if (arr == null) return emptyList()
        val out = ArrayList<InspectionRuleZoneSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += InspectionRuleZoneSummary(
                zoneId = o.optString("zoneId", ""),
                zoneName = o.optString("zoneName", ""),
                distanceMm = o.optDouble("distanceMm", 0.0).toFloat(),
                shockCount = o.optInt("shockCount", 0),
                coverage = o.optDouble("coverage", 0.0).toFloat(),
                validationScore = o.optDouble("validationScore", 0.0).toFloat(),
            )
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        runCatching { java.lang.Enum.valueOf(T::class.java, name.orEmpty()) }.getOrDefault(default)
}
