package com.example.cnv.opencv

import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Detects ORB keypoints on a grayscale frame and draws them as an overlay.
 * Does not compute optical flow or distance.
 */
class OrbFeatureDetector(
    maxFeatures: Int = DEFAULT_MAX_FEATURES,
) {

    private val orb: ORB = ORB.create(maxFeatures)

    /**
     * @return RGBA [Mat] with green keypoint overlay. Caller must [Mat.release].
     */
    fun detectAndDraw(gray: Mat): Mat {
        val keypoints = MatOfKeyPoint()
        orb.detect(gray, keypoints)

        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)

        val keypointArray = keypoints.toArray()
        for (keypoint in keypointArray) {
            Imgproc.circle(
                overlay,
                Point(keypoint.pt.x, keypoint.pt.y),
                KEYPOINT_RADIUS_PX,
                KEYPOINT_COLOR,
                KEYPOINT_THICKNESS,
            )
        }
        keypoints.release()
        return overlay
    }

    companion object {
        const val DEFAULT_MAX_FEATURES = 500
        const val KEYPOINT_RADIUS_PX = 3
        const val KEYPOINT_THICKNESS = -1
        private val KEYPOINT_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
    }
}
