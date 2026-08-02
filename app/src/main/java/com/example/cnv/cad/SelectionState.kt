package com.example.cnv.cad

/**
 * Immutable selection snapshot for CAD Interaction (STEP 11-2).
 * Does not own Route or Inspection.
 */
data class SelectionState(
    val selectedNodeId: String? = null,
    val selectedSegmentId: String? = null,
    val selectedBranchNodeId: String? = null,
    /** Segment projection 0..1 from last hit-test (not PositionEvent). */
    val pickProgress: Float = 0f,
    val pickWorldX: Double? = null,
    val pickWorldY: Double? = null,
    val errorSegmentIds: Set<String> = emptySet(),
    val selectionCount: Int = 0,
) {
    val hasSelection: Boolean
        get() = selectedNodeId != null || selectedSegmentId != null || selectedBranchNodeId != null

    companion object {
        val EMPTY: SelectionState = SelectionState()
    }
}
