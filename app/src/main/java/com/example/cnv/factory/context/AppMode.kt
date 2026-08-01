package com.example.cnv.factory.context

/**
 * App operating mode. Operation is default for floor workers.
 * Commissioning is Admin/Developer only (initial site setup).
 */
enum class AppMode {
    OPERATION,
    COMMISSIONING,
}
