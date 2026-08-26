package com.example.cnv.vio

/**
 * Camera mount relative to CNV / Route frame (phone fixed on tray).
 * Origin pick remains separate; this only defines axis mapping.
 */
data class CameraMountCalibration(
    /** Degrees: camera forward axis relative to CNV travel when heading = 0. */
    val yawOffsetDeg: Float = 0f,
    val pitchOffsetDeg: Float = 0f,
    val rollOffsetDeg: Float = 0f,
    /** +1 if camera optical forward aligns with CNV forward after offsets. */
    val forwardSign: Float = 1f,
) {
    companion object {
        val IDENTITY: CameraMountCalibration = CameraMountCalibration()
    }
}
