package com.example.cnv.rule

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.analysis.ZoneStatistics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Inspection Rule Engine (STEP 18).
 * Evaluates [RuleDefinition]s from [RuleDefinitionRepository] against Analysis Result only.
 * Rules are independent — no cross-rule calls. Sequential by priority.
 */
class InspectionRuleEngine(
    private val definitions: RuleDefinitionRepository,
) {

    fun evaluate(
        analysis: InspectionAnalysisResult,
        context: RuleEvaluationContext,
    ): InspectionRuleResult {
        val sessionDefs = definitions.resolveEffective(context)
            .filter { it.category != RuleCategory.ZONE }
        val zoneDefs = definitions.resolveEffective(context)
            .filter { it.category == RuleCategory.ZONE }

        val hits = ArrayList<RuleHit>()
        val now = System.currentTimeMillis()

        for (def in sessionDefs) {
            val value = metricValue(def.metric, analysis, zone = null)
            val triggered = isTriggered(def, value)
            hits += toHit(def, triggered, value, analysis, zone = null, now, context)
        }

        for (zone in analysis.zones) {
            val zoneCtx = context.copy(zoneId = zone.zoneId)
            val effectiveZoneDefs = if (zoneDefs.isEmpty()) {
                emptyList()
            } else {
                definitions.resolveEffective(zoneCtx).filter { it.category == RuleCategory.ZONE }
            }
            for (def in effectiveZoneDefs) {
                val value = metricValue(def.metric, analysis, zone)
                val triggered = isTriggered(def, value)
                hits += toHit(def, triggered, value, analysis, zone, now, zoneCtx)
            }
        }

        val triggered = hits.filter { it.triggered }
        val warnings = triggered
            .filter { it.relatedZoneId == null }
            .map { hit ->
                InspectionWarning(
                    ruleId = hit.ruleId,
                    severity = hit.severity,
                    label = hit.description,
                    detail = "value=%.2f · %s".format(hit.metricValue, hit.recommendation.displayLabel()),
                    recommendation = hit.recommendation,
                    category = hit.category,
                )
            }
        val issues = triggered
            .filter { it.relatedZoneId != null }
            .map { hit ->
                InspectionIssue(
                    zoneId = hit.relatedZoneId.orEmpty(),
                    zoneName = hit.relatedZoneName ?: hit.relatedZoneId.orEmpty(),
                    ruleId = hit.ruleId,
                    severity = hit.severity,
                    occurrenceCount = max(1, hit.metricValue.toInt()),
                    recommendation = hit.recommendation,
                    issueType = hit.category.name,
                )
            }
            .sortedWith(
                compareBy<InspectionIssue> { it.severity.ordinal }
                    .thenByDescending { it.occurrenceCount },
            )

        val zoneSummaries = analysis.zones.map { zone ->
            InspectionRuleZoneSummary(
                zoneId = zone.zoneId,
                zoneName = zone.zoneName,
                distanceMm = zone.distanceMm,
                shockCount = zone.shockCount,
                coverage = zone.coverage,
                validationScore = zoneValidationScore(zone, analysis.validationScore),
            )
        }

        return InspectionRuleResult(
            sessionId = analysis.sessionId,
            drawingId = analysis.drawingId,
            ruleCatalogVersion = context.ruleCatalogVersionSnapshot,
            evaluatedAtMs = now,
            hits = hits,
            warnings = warnings,
            issues = issues,
            zoneSummaries = zoneSummaries,
        )
    }

    private fun toHit(
        def: RuleDefinition,
        triggered: Boolean,
        value: Float,
        analysis: InspectionAnalysisResult,
        zone: ZoneStatistics?,
        now: Long,
        context: RuleEvaluationContext,
    ): RuleHit {
        val severity = if (triggered) elevateSeverity(def, value) else def.severity
        return RuleHit(
            ruleId = def.ruleId,
            ruleVersion = def.version,
            severity = severity,
            triggered = triggered,
            description = def.description,
            recommendation = def.recommendation,
            relatedBuildingId = context.buildingId,
            relatedFloorId = context.floorId,
            relatedDrawingId = analysis.drawingId,
            relatedZoneId = zone?.zoneId,
            relatedZoneName = zone?.zoneName,
            metricValue = value,
            timestampMs = now,
            category = def.category,
        )
    }

    private fun elevateSeverity(def: RuleDefinition, value: Float): RuleSeverity {
        if (def.severity == RuleSeverity.INFO || def.severity == RuleSeverity.CRITICAL) {
            return def.severity
        }
        val ratio = when (def.compareOp) {
            RuleCompareOp.GTE -> if (def.threshold <= 0f) 1f else value / def.threshold
            RuleCompareOp.LTE -> if (value <= 0f) 2f else def.threshold / value.coerceAtLeast(1e-6f)
        }
        return if (ratio >= 2f && def.severity.ordinal > RuleSeverity.CRITICAL.ordinal) {
            RuleSeverity.entries[max(0, def.severity.ordinal - 1)]
        } else {
            def.severity
        }
    }

    private fun isTriggered(def: RuleDefinition, value: Float): Boolean {
        val t = def.threshold
        val tol = def.tolerance
        return when (def.compareOp) {
            RuleCompareOp.GTE -> value >= t - tol
            RuleCompareOp.LTE -> value <= t + tol
        }
    }

    private fun metricValue(
        metric: RuleMetric,
        a: InspectionAnalysisResult,
        zone: ZoneStatistics?,
    ): Float {
        return when (metric) {
            RuleMetric.MAX_SHOCK -> a.shock.maximumShock
            RuleMetric.AVG_SHOCK -> a.shock.averageShock
            RuleMetric.SHOCK_COUNT -> a.shock.shockCount.toFloat()
            RuleMetric.SHOCK_DENSITY -> a.shock.shockDensityPerMeter
            RuleMetric.TRACKING_CONFIDENCE_MIN -> a.tracking.minimumConfidence
            RuleMetric.TRACKING_LOSS -> a.tracking.trackingLossCount.toFloat()
            RuleMetric.LOW_CONFIDENCE_COUNT -> a.tracking.lowConfidenceCount.toFloat()
            RuleMetric.ROUTE_COVERAGE -> a.coverage.routeCoverage
            RuleMetric.DRAWING_COVERAGE -> a.coverage.drawingCoverage
            RuleMetric.INSPECTION_RATIO -> a.coverage.inspectionRatio
            RuleMetric.AVG_SPEED_MM_PER_SEC -> a.speed.averageSpeedMmPerSec
            RuleMetric.NOMINAL_SPEED_DIFF_MM_PER_SEC -> abs(a.speed.speedDifferenceMmPerSec)
            RuleMetric.SPEED_STABILITY_RANGE ->
                (a.speed.maximumSpeedMmPerSec - a.speed.minimumSpeedMmPerSec).coerceAtLeast(0f)
            RuleMetric.VALIDATION_SCORE -> a.validationScore
            RuleMetric.ZONE_SHOCK_COUNT -> zone?.shockCount?.toFloat() ?: 0f
            RuleMetric.ZONE_COVERAGE -> zone?.coverage ?: 0f
            RuleMetric.ZONE_INSPECTION_TIME_MS -> zone?.inspectionTimeMs?.toFloat() ?: 0f
            RuleMetric.ZONE_VALIDATION_SCORE ->
                zone?.let { zoneValidationScore(it, a.validationScore) } ?: a.validationScore
            RuleMetric.SESSION_DURATION_MS -> a.summary.durationMs.toFloat()
            RuleMetric.SESSION_DISTANCE_MM -> a.distance.totalDistanceMm
            RuleMetric.SESSION_COMPLETENESS ->
                min(1f, max(a.coverage.routeCoverage, a.coverage.inspectionRatio))
        }
    }

    private fun zoneValidationScore(zone: ZoneStatistics, sessionValidation: Float): Float {
        val coverageFactor = zone.coverage.coerceIn(0f, 1f)
        val shockPenalty = min(1f, zone.shockCount / 10f)
        val blended = sessionValidation * 0.5f + coverageFactor * 0.5f - shockPenalty * 0.2f
        return max(0f, min(1f, blended))
    }
}
