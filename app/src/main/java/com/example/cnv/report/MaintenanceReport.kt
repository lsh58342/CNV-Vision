package com.example.cnv.report

import com.example.cnv.rule.RuleHit
import com.example.cnv.rule.RuleRecommendation
import com.example.cnv.rule.RuleSeverity

data class MaintenanceSummary(
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val lowCount: Int = 0,
    val infoCount: Int = 0,
    val inspectionGrade: InspectionGrade = InspectionGrade.A,
    val overallStatus: OverallStatus = OverallStatus.NORMAL,
)

/**
 * Zone-scoped issue rollup for Maintenance Report (projection only).
 */
data class ZoneIssueRow(
    val zoneId: String,
    val zoneName: String,
    val issueCount: Int,
    val highestSeverity: RuleSeverity,
    val shockCount: Int,
    val coverage: Float,
    val validationScore: Float,
)

data class RecommendedAction(
    val recommendation: RuleRecommendation,
    val relatedRuleIds: List<String>,
    val highestSeverity: RuleSeverity,
)

/**
 * Assembled Maintenance Report — display-only projection of Analysis + Rule Results.
 */
data class MaintenanceReport(
    val sessionId: String,
    val drawingId: String,
    val reportVersion: Int = ReportVersion.CURRENT,
    val generatedAtMs: Long = System.currentTimeMillis(),
    val inspectionDateMs: Long = 0L,
    val durationMs: Long = 0L,
    val distanceMm: Float = 0f,
    val coverage: Float = 0f,
    val validationScore: Float = 0f,
    val averageSpeedMmPerSec: Float = 0f,
    val averageShock: Float = 0f,
    val maximumShock: Float = 0f,
    val shockCount: Int = 0,
    val maintenanceSummary: MaintenanceSummary = MaintenanceSummary(),
    val priorityIssue: RuleHit? = null,
    val zoneIssues: List<ZoneIssueRow> = emptyList(),
    val issueDetails: List<RuleHit> = emptyList(),
    val recommendedActions: List<RecommendedAction> = emptyList(),
    val buildingId: String? = null,
    val buildingName: String = "",
    val floorId: String? = null,
    val floorName: String = "",
    val drawingName: String = "",
) {
    companion object {
        fun empty(sessionId: String = "", drawingId: String = "") = MaintenanceReport(
            sessionId = sessionId,
            drawingId = drawingId,
        )
    }
}
