package com.example.cnv.report

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.rule.InspectionRuleResult
import com.example.cnv.rule.RuleHit
import com.example.cnv.rule.RuleRecommendation
import com.example.cnv.rule.RuleSeverity

/**
 * Projects Analysis Result + Rule Result into [MaintenanceReport] (STEP 19).
 * Performs no Inspection Event analysis, Rule re-evaluation, or HeatMap generation.
 */
object MaintenanceReportAssembler {

    fun assemble(
        analysis: InspectionAnalysisResult,
        rules: InspectionRuleResult,
        buildingId: String? = null,
        buildingName: String = "",
        floorId: String? = null,
        floorName: String = "",
        drawingName: String = "",
    ): MaintenanceReport {
        val triggered = rules.triggered()
            .sortedWith(compareBy<RuleHit> { it.severity.ordinal }.thenByDescending { it.metricValue })
        val summary = buildMaintenanceSummary(triggered, analysis.validationScore)
        val zoneIssues = buildZoneIssues(rules, analysis)
        val actions = buildRecommendedActions(triggered)

        return MaintenanceReport(
            sessionId = analysis.sessionId,
            drawingId = analysis.drawingId,
            reportVersion = ReportVersion.CURRENT,
            inspectionDateMs = analysis.summary.startTimeMs,
            durationMs = analysis.summary.durationMs,
            distanceMm = analysis.distance.totalDistanceMm,
            coverage = maxOf(analysis.coverage.drawingCoverage, analysis.coverage.routeCoverage),
            validationScore = analysis.validationScore,
            averageSpeedMmPerSec = analysis.speed.averageSpeedMmPerSec,
            averageShock = analysis.shock.averageShock,
            maximumShock = analysis.shock.maximumShock,
            shockCount = analysis.shock.shockCount,
            maintenanceSummary = summary,
            priorityIssue = triggered.firstOrNull(),
            zoneIssues = zoneIssues,
            issueDetails = triggered,
            recommendedActions = actions,
            buildingId = buildingId,
            buildingName = buildingName,
            floorId = floorId,
            floorName = floorName,
            drawingName = drawingName,
        )
    }

    private fun buildMaintenanceSummary(
        triggered: List<RuleHit>,
        validationScore: Float,
    ): MaintenanceSummary {
        var critical = 0
        var high = 0
        var medium = 0
        var low = 0
        var info = 0
        for (hit in triggered) {
            when (hit.severity) {
                RuleSeverity.CRITICAL -> critical++
                RuleSeverity.HIGH -> high++
                RuleSeverity.MEDIUM -> medium++
                RuleSeverity.LOW -> low++
                RuleSeverity.INFO -> info++
            }
        }
        val overall = when {
            critical > 0 -> OverallStatus.CRITICAL
            high > 0 -> OverallStatus.WARNING
            medium > 0 -> OverallStatus.ATTENTION
            else -> OverallStatus.NORMAL
        }
        val grade = gradeFor(validationScore, overall)
        return MaintenanceSummary(
            criticalCount = critical,
            highCount = high,
            mediumCount = medium,
            lowCount = low,
            infoCount = info,
            inspectionGrade = grade,
            overallStatus = overall,
        )
    }

    private fun gradeFor(validationScore: Float, overall: OverallStatus): InspectionGrade {
        if (overall == OverallStatus.CRITICAL) return InspectionGrade.F
        if (overall == OverallStatus.WARNING) return InspectionGrade.D
        return when {
            validationScore >= 0.9f -> InspectionGrade.A
            validationScore >= 0.75f -> InspectionGrade.B
            validationScore >= 0.6f -> InspectionGrade.C
            validationScore >= 0.4f -> InspectionGrade.D
            else -> InspectionGrade.F
        }
    }

    private fun buildZoneIssues(
        rules: InspectionRuleResult,
        @Suppress("UNUSED_PARAMETER") analysis: InspectionAnalysisResult,
    ): List<ZoneIssueRow> {
        val zoneHits = rules.triggered().filter { !it.relatedZoneId.isNullOrBlank() }
            .groupBy { it.relatedZoneId!! }
        val summaries = rules.zoneSummaries.associateBy { it.zoneId }
        val ids = (zoneHits.keys + summaries.keys).distinct()
        return ids.map { zoneId ->
            val hits = zoneHits[zoneId].orEmpty()
            val summary = summaries[zoneId]
            val highest = hits.minByOrNull { it.severity.ordinal }?.severity
                ?: RuleSeverity.INFO
            ZoneIssueRow(
                zoneId = zoneId,
                zoneName = summary?.zoneName
                    ?: hits.firstOrNull()?.relatedZoneName
                    ?: zoneId,
                issueCount = hits.size,
                highestSeverity = highest,
                shockCount = summary?.shockCount ?: 0,
                coverage = summary?.coverage ?: 0f,
                validationScore = summary?.validationScore ?: 0f,
            )
        }.sortedWith(
            compareBy<ZoneIssueRow> { it.highestSeverity.ordinal }
                .thenByDescending { it.issueCount },
        )
    }

    private fun buildRecommendedActions(triggered: List<RuleHit>): List<RecommendedAction> {
        return triggered
            .groupBy { it.recommendation }
            .map { (rec, hits) ->
                RecommendedAction(
                    recommendation = rec,
                    relatedRuleIds = hits.map { it.ruleId }.distinct(),
                    highestSeverity = hits.minByOrNull { it.severity.ordinal }?.severity
                        ?: RuleSeverity.INFO,
                )
            }
            .sortedBy { it.highestSeverity.ordinal }
    }

    fun toWorkOrder(
        report: MaintenanceReport,
        hit: RuleHit,
    ): WorkOrder = WorkOrder(
        sessionId = report.sessionId,
        buildingId = report.buildingId ?: hit.relatedBuildingId,
        buildingName = report.buildingName,
        floorId = report.floorId ?: hit.relatedFloorId,
        floorName = report.floorName,
        drawingId = report.drawingId,
        drawingName = report.drawingName,
        zoneId = hit.relatedZoneId,
        zoneName = hit.relatedZoneName.orEmpty(),
        ruleId = hit.ruleId,
        ruleVersion = hit.ruleVersion,
        issueDescription = hit.description,
        severity = hit.severity,
        recommendation = hit.recommendation,
        inspectionDateMs = report.inspectionDateMs,
        status = WorkOrderStatus.OPEN,
    )
}
