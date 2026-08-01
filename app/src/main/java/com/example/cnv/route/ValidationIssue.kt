package com.example.cnv.route

/**
 * Categorized validation finding. Does not mutate Route.
 */
data class ValidationIssue(
    val type: ValidationIssueType,
    val message: String,
    val severity: ValidationSeverity,
    val segmentId: String? = null,
    val nodeId: String? = null,
)

enum class ValidationIssueType {
    ROUTE_MISSING,
    NODE_COUNT,
    SEGMENT_COUNT,
    BRANCH_COUNT,
    ISOLATED_NODE,
    UNCONNECTED_SEGMENT,
    ZERO_LENGTH_SEGMENT,
    SHORT_SEGMENT,
    DUPLICATE_NODE,
    DUPLICATE_SEGMENT,
    SELF_LOOP,
    INVALID_DIRECTION,
    CONTINUITY,
    ROUTE_LENGTH,
    DUPLICATE_ROUTE_ID,
}
