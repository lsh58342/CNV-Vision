package com.example.cnv.cad

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Touch → gesture callbacks. No CAD domain logic.
 */
class CADGestureManager(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onSingleTap(x: Float, y: Float)
        fun onDoubleTap(x: Float, y: Float)
        fun onLongPress(x: Float, y: Float)
        fun onDrag(dx: Float, dy: Float)
        fun onPinch(scaleFactor: Float, focusX: Float, focusY: Float)
        fun onFling(vx: Float, vy: Float)
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                listener.onPinch(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener.onSingleTap(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                listener.onDoubleTap(e.x, e.y)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                listener.onLongPress(e.x, e.y)
            }
        },
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (dragging && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        listener.onDrag(dx, dy)
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                if (hypot(vx.toDouble(), vy.toDouble()) >= minFling) {
                    listener.onFling(vx, vy)
                }
                velocityTracker?.recycle()
                velocityTracker = null
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                dragging = false
            }
        }
        return true
    }
}
