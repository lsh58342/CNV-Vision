package com.example.cnv.vio

import com.example.cnv.debug.TrackingAttitudeProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heading fusion: gyro short-term + visual correction.
 */
class VisualInertialFusionTest {

    @Test
    fun gyroIntegratesYawOverTime() {
        val fusion = VisualInertialFusion()
        // 90 deg/s for 1 second in 100ms steps → ~90°
        val wz = Math.toRadians(90.0).toFloat()
        var t = 0L
        fusion.onGyro(wz, t)
        repeat(10) {
            t += 100_000_000L
            fusion.onGyro(wz, t)
        }
        assertEquals(90f, fusion.headingDeg(), 12f)
        assertTrue(fusion.cumulativeRotationDeg() > 75f)
    }

    @Test
    fun visualCorrectsTowardFlowHeading() {
        val fusion = VisualInertialFusion(
            VisualInertialConfig(gyroWeight = 0.5f, visualHeadingWeight = 0.5f),
        )
        fusion.onGyro(0f, 0L, rotationVectorYawDeg = 0f)
        // Visual flow mostly +Y → ~90°
        repeat(20) {
            fusion.onVisualMotion(txPx = 0.0, tyPx = 5.0, inlierRatio = 0.8f, confidence = 0.9f)
        }
        val h = fusion.headingDeg()
        assertTrue("heading=$h", absHeadingDiff(h, 90f) < 45f)
    }

    @Test
    fun headingErrorDetects90DegreeTurn() {
        val fusion = VisualInertialFusion()
        val wz = Math.toRadians(90.0).toFloat()
        var t = 0L
        fusion.onGyro(wz, t)
        repeat(10) {
            t += 100_000_000L
            fusion.onGyro(wz, t)
        }
        assertTrue(fusion.headingErrorDeg(0f) > 80f)
        assertTrue(fusion.headingErrorDeg(90f) < 15f)
    }

    private fun absHeadingDiff(a: Float, b: Float): Float =
        kotlin.math.abs(TrackingAttitudeProbe.normalizeDeg(a - b))
}
