package com.example.cnv.factory.model

/**
 * DWG drawing under a Floor. All Route / Zone / Inspection / HeatMap data belongs to a Drawing.
 */
data class Drawing(
    val id: String,
    val floorId: String,
    val name: String,
    val dwgUri: String? = null,
    val description: String = "",
    val registeredAtMs: Long = System.currentTimeMillis(),
    val dwgRegistered: Boolean = false,
    /** Conveyor start / Route Origin — set once after DWG registration. */
    val originSet: Boolean = false,
    val originX: Float? = null,
    val originY: Float? = null,
    /** One Route per Drawing. */
    val routeId: String? = null,
    val routeLocked: Boolean = false,
    val calibrationReady: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
