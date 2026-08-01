package com.example.cnv.opencv

import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Detects ORB keypoints on a grayscale frame and draws them as an overlay.
 */
class OrbFeatureDetector(
    maxFeatures: Int = DEFAULT_MAX_FEATURES,
) {

    private val orb: ORB = ORB.create(maxFeatures)

    fun detect(gray: Mat): Array<KeyPoint> {
        val keypoints = MatOfKeyPoint()
        orb.detect(gray, keypoints)
        val detected = keypoints.toArray()
        keypoints.release()
        return detected
    }

    /**
     * @return RGBA [Mat] with green keypoint overlay. Caller must [Mat.release].
     */
    fun detectAndDraw(gray: Mat): Mat {
        val keypoints = detect(gray)
        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)
        for (keypoint in keypoints) {
            Imgproc.circle(
                overlay,
                Point(keypoint.pt.x, keypoint.pt.y),
                KEYPOINT_RADIUS_PX,
                KEYPOINT_COLOR,
                KEYPOINT_THICKNESS,
            )
        }
        return overlay
    }

    companion object {
        const val DEFAULT_MAX_FEATURES = 500
        const val KEYPOINT_RADIUS_PX = 3
        const val KEYPOINT_THICKNESS = -1
        private val KEYPOINT_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
    }
}
