package com.example.cnv.factory.context

/**
 * Access gate for Commissioning / Settings developer tools.
 * No cloud login in this STEP — local toggle only.
 */
enum class AccessRole {
    OPERATOR,
    ADMIN,
    DEVELOPER,
}

fun AccessRole.canAccessCommissioning(): Boolean =
    this == AccessRole.ADMIN || this == AccessRole.DEVELOPER
