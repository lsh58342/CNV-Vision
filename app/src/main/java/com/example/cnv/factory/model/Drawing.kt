package com.example.cnv.factory.model

/**
 * DWG drawing under a Floor. All Route / Zone / Inspection / HeatMap data belongs to a Drawing.
 * Hierarchy: Drawing → Drawing Info → Conveyor Profile → Calibration → Route → Zone → History.
 */
data class Drawing(
    val id: String,
    val floorId: String,
    val name: String,
    val dwgUri: String? = null,
    val description: String = "",
    val registeredAtMs: Long = System.currentTimeMillis(),
    val dwgRegistered: Boolean = false,
    /**
     * Conveyor layer used by Route extraction.
     * Default initial value is [com.example.cnv.dwg.DWGConfig.DEFAULT_LAYER_FILTER];
     * user may override after DXF import.
     */
    val conveyorLayerName: String = com.example.cnv.dwg.DWGConfig.DEFAULT_LAYER_FILTER,
    /** Conveyor start / Route Origin — set once after DWG registration. */
    val originSet: Boolean = false,
    val originX: Float? = null,
    val originY: Float? = null,
    /** Drawing metadata: conveyor speed / direction / FPS (STEP 15-1). */
    val conveyorProfile: ConveyorProfile = ConveyorProfile.fromConfig(),
    /** One Route per Drawing. */
    val routeId: String? = null,
    val routeLocked: Boolean = false,
    val calibrationReady: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
