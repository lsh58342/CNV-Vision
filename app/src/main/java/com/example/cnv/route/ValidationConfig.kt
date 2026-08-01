package com.example.cnv.route

/**
 * Route validation tolerances. All magic numbers live here.
 */
data class ValidationConfig(
    val minimumSegmentLength: Double = DEFAULT_MINIMUM_SEGMENT_LENGTH,
    val maximumSegmentLength: Double = DEFAULT_MAXIMUM_SEGMENT_LENGTH,
    val duplicateTolerance: Double = DEFAULT_DUPLICATE_TOLERANCE,
    val branchTolerance: Int = DEFAULT_BRANCH_TOLERANCE,
    val minimumNodeCount: Int = DEFAULT_MINIMUM_NODE_COUNT,
    val minimumSegmentCount: Int = DEFAULT_MINIMUM_SEGMENT_COUNT,
) {
    companion object {
        const val DEFAULT_MINIMUM_SEGMENT_LENGTH = 1.0
        const val DEFAULT_MAXIMUM_SEGMENT_LENGTH = 100_000.0
        const val DEFAULT_DUPLICATE_TOLERANCE = 0.0
        const val DEFAULT_BRANCH_TOLERANCE = 1
        const val DEFAULT_MINIMUM_NODE_COUNT = 2
        const val DEFAULT_MINIMUM_SEGMENT_COUNT = 1

        val DEFAULT: ValidationConfig = ValidationConfig()
    }
}
