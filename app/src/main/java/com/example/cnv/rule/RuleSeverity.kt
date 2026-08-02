package com.example.cnv.rule

/**
 * Severity for Rule evaluation (STEP 18). Includes Info.
 */
enum class RuleSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
}

/** @deprecated Prefer [RuleSeverity]; kept for Review UI aliases. */
typealias InspectionRuleSeverity = RuleSeverity
