package com.example.cnv.dwg

/**
 * Named CAD layer metadata.
 */
data class DWGLayer(
    val name: String,
    val visible: Boolean = true,
    val frozen: Boolean = false,
)
