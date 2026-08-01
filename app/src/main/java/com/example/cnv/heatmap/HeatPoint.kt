package com.example.cnv.heatmap

import com.example.cnv.route.WorldCoordinate

/**
 * Shock heat sample. [intensity] equals [shockLevel] (no extra scoring).
 */
data class HeatPoint(
    val position: WorldCoordinate,
    val shockLevel: Float,
    val timestampNs: Long,
    val confidence: Float,
    val sessionId: String,
    val intensity: Float,
)
