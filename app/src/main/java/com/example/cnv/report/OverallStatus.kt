package com.example.cnv.report

/**
 * Overall maintenance status derived from Rule Result severities (STEP 19).
 */
enum class OverallStatus {
    NORMAL,
    ATTENTION,
    WARNING,
    CRITICAL,
}

/**
 * Simple inspection grade projected from Validation Score + Overall Status.
 */
enum class InspectionGrade {
    A,
    B,
    C,
    D,
    F,
}
