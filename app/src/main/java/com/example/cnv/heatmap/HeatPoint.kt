package com.example.cnv.heatmap

import com.example.cnv.route.WorldCoordinate

/**
 * Heat sample. [intensity] equals [shockLevel] for Shock mode (no extra scoring).
 * [segmentId]/[nodeId] are metadata for filtering only.
 */
data class HeatPoint(
    val position: WorldCoordinate,
    val shockLevel: Float,
    val timestampNs: Long,
    val confidence: Float,
    val sessionId: String,
    val intensity: Float,
    val segmentId: String? = null,
    val nodeId: String? = null,
)
