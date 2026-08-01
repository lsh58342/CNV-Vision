package com.example.cnv.cad

/**
 * Immutable selection snapshot for CAD Interaction (STEP 11-2).
 * Does not own Route or Inspection.
 */
data class SelectionState(
    val selectedNodeId: String? = null,
    val selectedSegmentId: String? = null,
    val selectedBranchNodeId: String? = null,
    val errorSegmentIds: Set<String> = emptySet(),
    val selectionCount: Int = 0,
) {
    val hasSelection: Boolean
        get() = selectedNodeId != null || selectedSegmentId != null || selectedBranchNodeId != null

    companion object {
        val EMPTY: SelectionState = SelectionState()
    }
}
