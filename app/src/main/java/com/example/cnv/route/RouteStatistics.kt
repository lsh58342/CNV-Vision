package com.example.cnv.route

/**
 * Numeric summary of a Route graph (read-only analysis).
 */
data class RouteStatistics(
    val totalRouteLengthMm: Float,
    val averageSegmentLengthMm: Float,
    val maximumSegmentLengthMm: Float,
    val minimumSegmentLengthMm: Float,
    val nodeCount: Int,
    val segmentCount: Int,
    val branchCount: Int,
) {
    companion object {
        val EMPTY = RouteStatistics(
            totalRouteLengthMm = 0f,
            averageSegmentLengthMm = 0f,
            maximumSegmentLengthMm = 0f,
            minimumSegmentLengthMm = 0f,
            nodeCount = 0,
            segmentCount = 0,
            branchCount = 0,
        )
    }
}
