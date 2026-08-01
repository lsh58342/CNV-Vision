package com.example.cnv.cad

/**
 * Read-only info panel model for a selection. Strings only — no domain mutation.
 */
data class SelectionInfo(
    val routeName: String = "—",
    val segmentId: String = "—",
    val segmentLengthMm: Float = 0f,
    val direction: String = "—",
    val nodeId: String = "—",
    val progress: Float = 0f,
    val currentPositionText: String = "—",
    val inspectionState: String = "—",
) {
    fun toDisplayLines(): List<String> = listOf(
        "Route: $routeName",
        "Seg: $segmentId",
        "Len: %.1f mm".format(segmentLengthMm),
        "Dir: $direction",
        "Node: $nodeId",
        "Progress: %.2f".format(progress),
        "Pos: $currentPositionText",
        "Insp: $inspectionState",
    )
}
