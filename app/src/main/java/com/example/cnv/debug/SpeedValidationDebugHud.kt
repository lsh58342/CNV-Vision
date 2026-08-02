package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.speed.SpeedValidationSample
import com.example.cnv.speed.SpeedValidatorEngine

/**
 * Developer Mode Speed Validation overlay (STEP 15-2).
 * Shows Expected / Measured / Difference / Confidence / Nominal Speed.
 */
class SpeedValidationDebugHud(
    private val textView: TextView,
    private val engine: SpeedValidatorEngine,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(engine.latest(), engine.mismatchWarning(), engine.sessionSummary().validationScore)
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    fun start() {
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    fun stop() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun render(
        sample: SpeedValidationSample?,
        mismatchWarning: Boolean,
        validationScore: Float,
    ) {
        if (sample == null) {
            textView.text = buildString {
                append("Speed Validation\n")
                append("(waiting — set Nominal Speed in Conveyor Profile)\n")
                if (mismatchWarning) append("WARNING: Conveyor Speed Mismatch")
            }
            return
        }
        textView.text = buildString {
            append("Speed Validation\n")
            append("Nominal Speed: %.2f m/min\n".format(sample.nominalSpeedMPerMin))
            append("Expected Dist: %.3f mm\n".format(sample.expectedDistanceMm))
            append("Measured Dist: %.3f mm\n".format(sample.measuredDistanceMm))
            append("Difference: %.3f mm\n".format(sample.differenceMm))
            append("Confidence: %.2f\n".format(sample.confidence))
            sample.validatedFusionConfidence?.let {
                append("Validated Fusion Conf: %.2f\n".format(it))
            }
            append("Outlier: %s\n".format(if (sample.outlier) "YES" else "no"))
            append("Validation Score: %.2f\n".format(validationScore))
            if (mismatchWarning) {
                append("WARNING: Conveyor Speed Mismatch")
            }
        }
    }

    companion object {
        const val DEFAULT_REFRESH_MS = 200L
    }
}
