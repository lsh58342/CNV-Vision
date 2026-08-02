package com.example.cnv.report

import com.example.cnv.rule.RuleRecommendation
import com.example.cnv.rule.RuleSeverity
import java.util.UUID

enum class WorkOrderStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

/**
 * Work Order created from a Rule Result hit (STEP 19).
 * Does not re-evaluate rules — copies fields from the selected issue.
 */
data class WorkOrder(
    val workOrderId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val buildingId: String? = null,
    val buildingName: String = "",
    val floorId: String? = null,
    val floorName: String = "",
    val drawingId: String,
    val drawingName: String = "",
    val zoneId: String? = null,
    val zoneName: String = "",
    val ruleId: String,
    val ruleVersion: Int,
    val issueDescription: String,
    val severity: RuleSeverity,
    val recommendation: RuleRecommendation,
    val inspectionDateMs: Long,
    val status: WorkOrderStatus = WorkOrderStatus.OPEN,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
