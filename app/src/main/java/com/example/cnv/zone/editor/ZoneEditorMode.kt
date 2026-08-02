package com.example.cnv.zone.editor

/**
 * Zone Editor creation path (Commissioning only).
 */
enum class ZoneEditorMode {
    IDLE,
    CAD_PICK_START,
    CAD_PICK_END,
    CAD_CONFIRM,
    /** Multi polyline/segment toggle selection. */
    CAD_MULTI_SELECT,
    DRIVE_RECORDING,
    DRIVE_MARK_START,
    DRIVE_MARK_END,
    NAME_COLOR,
    SAVED,
}
