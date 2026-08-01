package com.example.cnv.core.config

/**
 * CameraX / analysis defaults used by the camera feature.
 */
data class CameraConfig(
    val preferBackCamera: Boolean = true,
    val analysisBackpressureKeepLatest: Boolean = true,
) {
    companion object {
        val DEFAULT: CameraConfig = CameraConfig()
    }
}
