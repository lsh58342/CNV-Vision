package com.example.cnv.opencv

/**
 * Process-wide optical-flow counters for Tracking Debug HUD (STEP 20-22).
 * Updated by LK / DistanceEstimator — read-only for UI.
 */
object OpticalFlowDebugHub {
    @Volatile
    var trackedFeatureCount: Int = 0
        private set

    @Volatile
    var lostFeatureCount: Int = 0
        private set

    @Volatile
    var reinitializeCount: Int = 0
        private set

    @Volatile
    var headingDeg: Float = 0f
        private set

    @Volatile
    var lastMedianPixel: Float = 0f
        private set

    fun onTrack(tracked: Int, lost: Int) {
        trackedFeatureCount = tracked
        lostFeatureCount = lost
    }

    fun onReinitialize() {
        reinitializeCount += 1
    }

    fun onFlowHeading(headingDeg: Float, medianPixel: Float) {
        this.headingDeg = headingDeg
        lastMedianPixel = medianPixel
    }

    fun reset() {
        trackedFeatureCount = 0
        lostFeatureCount = 0
        reinitializeCount = 0
        headingDeg = 0f
        lastMedianPixel = 0f
    }
}
