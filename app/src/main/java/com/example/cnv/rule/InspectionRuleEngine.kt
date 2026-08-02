package com.example.cnv.rule

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.analysis.ZoneStatistics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Inspection Rule Engine (STEP 17-1).
 * Evaluates [InspectionAnalysisResult] only — never Inspection Events / HeatMap / Replay.
 */
class InspectionRuleEngine(
    private val config: InspectionRuleConfig = InspectionRuleConfig.DEFAULT,
) {

    fun evaluate(analysis: InspectionAnalysisResult): InspectionRuleResult {
        val warnings = buildSessionWarnings(analysis)
        val issues = buildZoneIssues(analysis.zones, analysis.validationScore)
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
            warnings = warnings,
            issues = issues.sortedWith(
                compareBy<InspectionIssue> { it.severity.ordinal }
                    .thenByDescending { it.occurrenceCount },
            ),
            zoneSummaries = zoneSummaries,
        )
    }

    private fun buildSessionWarnings(a: InspectionAnalysisResult): List<InspectionWarning> {
        val out = ArrayList<InspectionWarning>(5)
        if (a.shock.maximumShock >= config.highShockStrength ||
            a.shock.shockCount >= config.highShockCount
        ) {
            out += InspectionWarning(
                type = InspectionWarningType.HIGH_SHOCK,
                severity = if (a.shock.maximumShock >= config.highShockStrength * 1.5f ||
                    a.shock.shockCount >= config.highShockCount * 2
                ) {
                    InspectionRuleSeverity.CRITICAL
                } else {
                    InspectionRuleSeverity.HIGH
                },
                label = "High Shock",
                detail = "max=%.2f count=%d".format(a.shock.maximumShock, a.shock.shockCount),
            )
        }
        val coverage = max(a.coverage.drawingCoverage, a.coverage.routeCoverage)
        if (coverage < config.lowCoverage) {
            out += InspectionWarning(
                type = InspectionWarningType.LOW_COVERAGE,
                severity = if (coverage < config.lowCoverage * 0.5f) {
                    InspectionRuleSeverity.HIGH
                } else {
                    InspectionRuleSeverity.MEDIUM
                },
                label = "Low Coverage",
                detail = "%.0f%%".format(coverage * 100f),
            )
        }
        if (a.tracking.trackingLossCount >= config.trackingLossMin) {
            out += InspectionWarning(
                type = InspectionWarningType.TRACKING_LOSS,
                severity = if (a.tracking.trackingLossCount >= 5) {
                    InspectionRuleSeverity.HIGH
                } else {
                    InspectionRuleSeverity.MEDIUM
                },
                label = "Tracking Loss",
                detail = "losses=%d lowConf=%d".format(
                    a.tracking.trackingLossCount,
                    a.tracking.lowConfidenceCount,
                ),
            )
        }
        if (abs(a.speed.speedDifferenceMmPerSec) >= config.speedMismatchMmPerSec) {
            out += InspectionWarning(
                type = InspectionWarningType.SPEED_MISMATCH,
                severity = InspectionRuleSeverity.HIGH,
                label = "Speed Mismatch",
                detail = "Δ=%.1f mm/s".format(a.speed.speedDifferenceMmPerSec),
            )
        }
        if (a.validationScore < config.validationWarningScore) {
            out += InspectionWarning(
                type = InspectionWarningType.VALIDATION_WARNING,
                severity = if (a.validationScore < config.validationWarningScore * 0.5f) {
                    InspectionRuleSeverity.CRITICAL
                } else {
                    InspectionRuleSeverity.MEDIUM
                },
                label = "Validation Warning",
                detail = "score=%.2f".format(a.validationScore),
            )
        }
        return out
    }

    private fun buildZoneIssues(
        zones: List<ZoneStatistics>,
        sessionValidation: Float,
    ): List<InspectionIssue> {
        val issues = ArrayList<InspectionIssue>()
        for (zone in zones) {
            if (zone.shockCount >= config.zoneHighShockCount) {
                issues += InspectionIssue(
                    zoneId = zone.zoneId,
                    zoneName = zone.zoneName,
                    issueType = InspectionWarningType.HIGH_SHOCK,
                    severity = if (zone.shockCount >= config.zoneHighShockCount * 2) {
                        InspectionRuleSeverity.CRITICAL
                    } else {
                        InspectionRuleSeverity.HIGH
                    },
                    occurrenceCount = zone.shockCount,
                )
            }
            if (zone.coverage < config.zoneLowCoverage) {
                issues += InspectionIssue(
                    zoneId = zone.zoneId,
                    zoneName = zone.zoneName,
                    issueType = InspectionWarningType.LOW_COVERAGE,
                    severity = InspectionRuleSeverity.MEDIUM,
                    occurrenceCount = 1,
                )
            }
            val zScore = zoneValidationScore(zone, sessionValidation)
            if (zScore < config.validationWarningScore) {
                issues += InspectionIssue(
                    zoneId = zone.zoneId,
                    zoneName = zone.zoneName,
                    issueType = InspectionWarningType.VALIDATION_WARNING,
                    severity = InspectionRuleSeverity.LOW,
                    occurrenceCount = 1,
                )
            }
        }
        return issues
    }

    /**
     * Zone score from Analysis zone metrics + session validation — no event re-scan.
     */
    private fun zoneValidationScore(zone: ZoneStatistics, sessionValidation: Float): Float {
        val coverageFactor = zone.coverage.coerceIn(0f, 1f)
        val shockPenalty = min(1f, zone.shockCount / 10f)
        val blended = sessionValidation * 0.5f + coverageFactor * 0.5f - shockPenalty * 0.2f
        return max(0f, min(1f, blended))
    }
}
