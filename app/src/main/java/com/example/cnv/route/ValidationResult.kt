package com.example.cnv.route

/**
 * Aggregate validation outcome. Read-only; never persisted.
 */
data class ValidationResult(
    val severity: ValidationSeverity,
    val issues: List<ValidationIssue>,
    val statistics: RouteStatistics,
) {
    val isSuccess: Boolean get() = severity == ValidationSeverity.SUCCESS
    val errorCount: Int get() = issues.count { it.severity == ValidationSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ValidationSeverity.WARNING }

    companion object {
        fun emptyMissing(): ValidationResult {
            return ValidationResult(
                severity = ValidationSeverity.ERROR,
                issues = listOf(
                    ValidationIssue(
                        type = ValidationIssueType.ROUTE_MISSING,
                        message = "Route is missing from repository",
                        severity = ValidationSeverity.ERROR,
                    ),
                ),
                statistics = RouteStatistics.EMPTY,
            )
        }
    }
}
