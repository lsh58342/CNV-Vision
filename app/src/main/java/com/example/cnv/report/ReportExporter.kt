package com.example.cnv.report

/**
 * Export formats for Maintenance Report (STEP 19).
 */
enum class ReportExportFormat {
    PDF,
    CSV,
    JSON,
}

/**
 * Export payload — structure only; CMMS / Email / REST attach later.
 */
data class ReportExportPayload(
    val format: ReportExportFormat,
    val fileName: String,
    val mimeType: String,
    val content: String,
    val reportVersion: Int,
)

/**
 * Builds CSV / JSON / PDF-text from [MaintenanceReport] without recalculating metrics.
 */
object ReportExporter {

    fun export(report: MaintenanceReport, format: ReportExportFormat): ReportExportPayload {
        val base = "maintenance_report_${report.sessionId.take(8)}_v${report.reportVersion}"
        return when (format) {
            ReportExportFormat.CSV -> ReportExportPayload(
                format = format,
                fileName = "$base.csv",
                mimeType = "text/csv",
                content = toCsv(report),
                reportVersion = report.reportVersion,
            )
            ReportExportFormat.JSON -> ReportExportPayload(
                format = format,
                fileName = "$base.json",
                mimeType = "application/json",
                content = toJson(report),
                reportVersion = report.reportVersion,
            )
            ReportExportFormat.PDF -> ReportExportPayload(
                format = format,
                fileName = "$base.txt",
                mimeType = "text/plain",
                content = toPdfText(report),
                reportVersion = report.reportVersion,
            )
        }
    }

    private fun toCsv(r: MaintenanceReport): String = buildString {
        appendLine("field,value")
        appendLine("reportVersion,${r.reportVersion}")
        appendLine("sessionId,${r.sessionId}")
        appendLine("drawingId,${r.drawingId}")
        appendLine("drawingName,${escape(r.drawingName)}")
        appendLine("building,${escape(r.buildingName)}")
        appendLine("floor,${escape(r.floorName)}")
        appendLine("inspectionDateMs,${r.inspectionDateMs}")
        appendLine("durationMs,${r.durationMs}")
        appendLine("distanceMm,${r.distanceMm}")
        appendLine("coverage,${r.coverage}")
        appendLine("validationScore,${r.validationScore}")
        appendLine("averageSpeedMmPerSec,${r.averageSpeedMmPerSec}")
        appendLine("maximumShock,${r.maximumShock}")
        appendLine("shockCount,${r.shockCount}")
        appendLine("overallStatus,${r.maintenanceSummary.overallStatus}")
        appendLine("inspectionGrade,${r.maintenanceSummary.inspectionGrade}")
        appendLine("criticalCount,${r.maintenanceSummary.criticalCount}")
        appendLine("highCount,${r.maintenanceSummary.highCount}")
        appendLine("mediumCount,${r.maintenanceSummary.mediumCount}")
        appendLine("lowCount,${r.maintenanceSummary.lowCount}")
        appendLine("priorityRuleId,${r.priorityIssue?.ruleId.orEmpty()}")
        appendLine()
        appendLine("ruleId,ruleVersion,severity,description,recommendation,zoneId,timestampMs")
        for (hit in r.issueDetails) {
            appendLine(
                listOf(
                    hit.ruleId,
                    hit.ruleVersion.toString(),
                    hit.severity.name,
                    escape(hit.description),
                    hit.recommendation.name,
                    hit.relatedZoneId.orEmpty(),
                    hit.timestampMs.toString(),
                ).joinToString(","),
            )
        }
    }

    private fun toJson(r: MaintenanceReport): String = buildString {
        append("{")
        append("\"reportVersion\":${r.reportVersion},")
        append("\"sessionId\":\"${escapeJson(r.sessionId)}\",")
        append("\"drawingId\":\"${escapeJson(r.drawingId)}\",")
        append("\"drawingName\":\"${escapeJson(r.drawingName)}\",")
        append("\"buildingName\":\"${escapeJson(r.buildingName)}\",")
        append("\"floorName\":\"${escapeJson(r.floorName)}\",")
        append("\"inspectionDateMs\":${r.inspectionDateMs},")
        append("\"durationMs\":${r.durationMs},")
        append("\"distanceMm\":${r.distanceMm},")
        append("\"coverage\":${r.coverage},")
        append("\"validationScore\":${r.validationScore},")
        append("\"averageSpeedMmPerSec\":${r.averageSpeedMmPerSec},")
        append("\"maximumShock\":${r.maximumShock},")
        append("\"shockCount\":${r.shockCount},")
        append("\"overallStatus\":\"${r.maintenanceSummary.overallStatus}\",")
        append("\"inspectionGrade\":\"${r.maintenanceSummary.inspectionGrade}\",")
        append("\"criticalCount\":${r.maintenanceSummary.criticalCount},")
        append("\"highCount\":${r.maintenanceSummary.highCount},")
        append("\"mediumCount\":${r.maintenanceSummary.mediumCount},")
        append("\"lowCount\":${r.maintenanceSummary.lowCount},")
        append("\"priorityIssue\":")
        append(hitJson(r.priorityIssue))
        append(",\"issues\":[")
        append(r.issueDetails.joinToString(",") { hitJson(it) })
        append("],\"recommendedActions\":[")
        append(
            r.recommendedActions.joinToString(",") { a ->
                "{\"recommendation\":\"${a.recommendation}\"," +
                    "\"severity\":\"${a.highestSeverity}\"," +
                    "\"ruleIds\":[${a.relatedRuleIds.joinToString(",") { "\"${escapeJson(it)}\"" }}]}"
            },
        )
        append("]}")
    }

    private fun hitJson(hit: com.example.cnv.rule.RuleHit?): String {
        if (hit == null) return "null"
        return "{" +
            "\"ruleId\":\"${escapeJson(hit.ruleId)}\"," +
            "\"ruleVersion\":${hit.ruleVersion}," +
            "\"severity\":\"${hit.severity}\"," +
            "\"description\":\"${escapeJson(hit.description)}\"," +
            "\"recommendation\":\"${hit.recommendation}\"," +
            "\"zoneId\":\"${escapeJson(hit.relatedZoneId.orEmpty())}\"," +
            "\"timestampMs\":${hit.timestampMs}" +
            "}"
    }

    private fun toPdfText(r: MaintenanceReport): String = buildString {
        appendLine("CNV Maintenance Report v${r.reportVersion}")
        appendLine("================================")
        appendLine("Session: ${r.sessionId}")
        appendLine("Drawing: ${r.drawingName} (${r.drawingId})")
        appendLine("Building / Floor: ${r.buildingName} / ${r.floorName}")
        appendLine("Status: ${r.maintenanceSummary.overallStatus} · Grade ${r.maintenanceSummary.inspectionGrade}")
        appendLine("Distance: ${r.distanceMm} mm · Coverage: ${r.coverage * 100f}%")
        appendLine("Validation: ${r.validationScore} · Shocks: ${r.shockCount} (max ${r.maximumShock})")
        appendLine()
        appendLine("Priority Issue:")
        val p = r.priorityIssue
        if (p == null) {
            appendLine("  (none)")
        } else {
            appendLine("  ${p.ruleId} v${p.ruleVersion} [${p.severity}] ${p.description}")
            appendLine("  → ${p.recommendation.displayLabel()}")
        }
        appendLine()
        appendLine("Issues:")
        for (hit in r.issueDetails) {
            appendLine(
                "  - ${hit.ruleId} v${hit.ruleVersion} [${hit.severity}] ${hit.description}" +
                    " · ${hit.recommendation.displayLabel()}",
            )
        }
        appendLine()
        appendLine("Recommended Actions:")
        for (a in r.recommendedActions) {
            appendLine("  - ${a.recommendation.displayLabel()} [${a.highestSeverity}]")
        }
        appendLine()
        appendLine("(PDF binary rendering reserved for future — text export structure ready)")
    }

    private fun escape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
