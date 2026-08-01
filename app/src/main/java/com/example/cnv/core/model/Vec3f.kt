package com.example.cnv.core.model

/**
 * Shared 3D vector used across IMU / future fusion (feature modules keep domain-specific models).
 */
data class Vec3f(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)
