package com.example.cnv.heatmap

/**
 * Owns the active [HeatMapMode]. STEP 12-2: only [HeatMapMode.SHOCK] is selectable.
 */
class HeatMapModeController(
    initialMode: HeatMapMode = HeatMapMode.SHOCK,
) {
    @Volatile
    private var mode: HeatMapMode = initialMode

    fun currentMode(): HeatMapMode = mode

    /**
     * Selects a mode. Non-SHOCK modes are reserved and ignored until STEP 12-3+.
     * @return true when the mode actually changed.
     */
    fun setMode(next: HeatMapMode): Boolean {
        if (next != HeatMapMode.SHOCK) {
            // TODO(STEP12-3+): enable COVERAGE / CONFIDENCE selection.
            return false
        }
        if (mode == next) return false
        mode = next
        return true
    }

    fun availableModes(): List<HeatMapMode> = listOf(HeatMapMode.SHOCK)

    fun providerNameForCurrent(mapperProvider: () -> com.example.cnv.route.CoordinateMapper?): String {
        return HeatMapFactory.create(mode, mapperProvider).providerName
    }
}
