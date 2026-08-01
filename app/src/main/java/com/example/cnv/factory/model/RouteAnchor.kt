package com.example.cnv.factory.model

/**
 * Route-relative anchor for Zone bounds (not CAD coordinates).
 */
data class RouteAnchor(
    val nodeId: String? = null,
    val segmentId: String? = null,
    val distanceFromSegmentStartMm: Float? = null,
    val progress: Float? = null,
) {
    fun isDefined(): Boolean =
        !nodeId.isNullOrBlank() ||
            (!segmentId.isNullOrBlank() && distanceFromSegmentStartMm != null) ||
            (!segmentId.isNullOrBlank() && progress != null)
}
