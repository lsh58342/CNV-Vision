package com.example.cnv.ui.screen.inspection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Real-time / history shock waveform — display only.
 */
class ShockGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64B5F6.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF81C784.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val maxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE57373.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFEB3B.toInt()
        style = Paint.Style.FILL
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEEEEEE.toInt()
        textSize = 28f
    }
    private val path = Path()

    private var state = ShockGraphState()

    fun bind(next: ShockGraphState) {
        state = next
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minH = suggestedMinimumHeight.coerceAtLeast(dp(160))
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val h = when (hMode) {
            MeasureSpec.EXACTLY -> hSize.coerceAtLeast(minH)
            MeasureSpec.AT_MOST -> minH.coerceAtMost(hSize).coerceAtLeast(minH)
            else -> minH
        }
        setMeasuredDimension(w, h)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF1A1A1A.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padL = 12f
        val padR = 12f
        val padT = 36f
        val padB = 12f
        val plotW = max(w - padL - padR, 1f)
        val plotH = max(h - padT - padB, 1f)

        // Grid
        for (i in 0..4) {
            val y = padT + plotH * i / 4f
            canvas.drawLine(padL, y, padL + plotW, y, gridPaint)
        }

        val samples = state.samples
        val yMax = max(state.maximum, max(state.threshold, 0.1f)) * 1.15f

        fun yOf(v: Float): Float = padT + plotH * (1f - (v / yMax).coerceIn(0f, 1f))

        // Threshold / avg / max lines
        canvas.drawLine(padL, yOf(state.threshold), padL + plotW, yOf(state.threshold), thresholdPaint)
        canvas.drawLine(padL, yOf(state.average), padL + plotW, yOf(state.average), avgPaint)
        if (state.maximum > 0f) {
            canvas.drawLine(padL, yOf(state.maximum), padL + plotW, yOf(state.maximum), maxPaint)
        }

        if (samples.size >= 2) {
            path.reset()
            val step = plotW / (samples.size - 1).toFloat()
            samples.forEachIndexed { i, v ->
                val x = padL + step * i
                val y = yOf(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, wavePaint)

            for (idx in state.peakIndices) {
                if (idx !in samples.indices) continue
                val x = padL + step * idx
                canvas.drawCircle(x, yOf(samples[idx]), 6f, peakPaint)
            }

            val lastX = padL + step * (samples.size - 1)
            canvas.drawCircle(lastX, yOf(state.current), 7f, currentPaint)
        } else if (samples.size == 1) {
            canvas.drawCircle(padL + plotW, yOf(samples[0]), 7f, currentPaint)
        }

        legendPaint.textSize = 26f
        canvas.drawText(
            "Cur %.2f  Avg %.2f  Max %.2f  Thr %.2f".format(
                state.current,
                state.average,
                state.maximum,
                state.threshold,
            ),
            padL,
            28f,
            legendPaint,
        )
    }
}
