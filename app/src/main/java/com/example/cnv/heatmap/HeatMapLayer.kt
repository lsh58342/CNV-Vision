package com.example.cnv.heatmap

/**
 * STEP 12-1: Shock HeatMap layer only.
 */
enum class HeatMapLayer {
    SHOCK,
}

class HeatMapLayerState {
    @Volatile
    private var shockEnabled: Boolean = true

    fun isEnabled(layer: HeatMapLayer = HeatMapLayer.SHOCK): Boolean = when (layer) {
        HeatMapLayer.SHOCK -> shockEnabled
    }

    fun setEnabled(layer: HeatMapLayer, value: Boolean) {
        when (layer) {
            HeatMapLayer.SHOCK -> shockEnabled = value
        }
    }

    fun toggle(layer: HeatMapLayer = HeatMapLayer.SHOCK) {
        setEnabled(layer, !isEnabled(layer))
    }
}
