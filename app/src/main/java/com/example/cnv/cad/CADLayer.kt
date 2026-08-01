package com.example.cnv.cad

/**
 * CAD draw layers. Toggle independently via [CADLayerState].
 * Session-scoped: [CADLayerState] lives on [CADView] for the Activity session.
 */
enum class CADLayer {
    ROUTE,
    NODE,
    BRANCH,
    POSITION,
    DEBUG,
    GRID,
}

class CADLayerState(
    initiallyEnabled: Set<CADLayer> = CADLayer.entries.toSet(),
) {
    private val enabled = BooleanArray(CADLayer.entries.size) { index ->
        CADLayer.entries[index] in initiallyEnabled
    }

    fun isEnabled(layer: CADLayer): Boolean = enabled[layer.ordinal]

    fun setEnabled(layer: CADLayer, value: Boolean) {
        enabled[layer.ordinal] = value
    }

    fun toggle(layer: CADLayer) {
        enabled[layer.ordinal] = !enabled[layer.ordinal]
    }
}
