package com.example.cnv.cad

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import com.example.cnv.map.Route
import com.example.cnv.route.WorldCoordinate
import kotlin.math.hypot

/**
 * Smooth viewport navigation. Does not compute Route/Position — only camera transforms.
 */
class CADNavigator(
    private val viewport: CADViewport,
    private val invalidate: () -> Unit,
    private val viewSize: () -> Pair<Float, Float>,
    private val layoutProvider: () -> CADRenderer.Layout?,
    private val routeProvider: () -> Route?,
    private val currentWorldProvider: () -> WorldCoordinate?,
) {
    private var animator: ValueAnimator? = null

    fun fitToRoute(animate: Boolean = true) {
        val (w, h) = viewSize()
        val points = layoutProvider()?.nodeWorld?.values.orEmpty()
        val bounds = viewport.boundsFromPoints(points)
        if (!animate) {
            viewport.fitToRoute(bounds, w, h)
            invalidate()
            return
        }
        val camera = viewport.camera
        val fromScale = camera.scale
        val fromOx = camera.offsetX
        val fromOy = camera.offsetY
        viewport.fitToRoute(bounds, w, h)
        val toScale = camera.scale
        val toOx = camera.offsetX
        val toOy = camera.offsetY
        camera.setTransform(fromScale, fromOx, fromOy)
        animateCamera(fromScale, fromOx, fromOy, toScale, toOx, toOy)
    }

    fun goToStart(animate: Boolean = true) {
        val layout = layoutProvider() ?: return
        val id = layout.startNodeId ?: return
        val world = layout.nodeWorld[id] ?: return
        centerOn(world, animate)
    }

    fun goToEnd(animate: Boolean = true) {
        val layout = layoutProvider() ?: return
        val id = layout.endNodeId ?: return
        val world = layout.nodeWorld[id] ?: return
        centerOn(world, animate)
    }

    fun goToCurrentPosition(animate: Boolean = true) {
        val world = currentWorldProvider() ?: return
        centerOn(world, animate)
    }

    fun centerCurrentPosition(animate: Boolean = true) = goToCurrentPosition(animate)

    fun searchAndGo(query: String, animate: Boolean = true): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        val layout = layoutProvider() ?: return false
        val route = routeProvider()
        layout.nodeWorld[q]?.let {
            centerOn(it, animate)
            return true
        }
        route?.nodes?.firstOrNull { it.id.equals(q, ignoreCase = true) }?.id?.let { id ->
            layout.nodeWorld[id]?.let {
                centerOn(it, animate)
                return true
            }
        }
        layout.segmentWorld[q]?.let { (start, end) ->
            centerOn(
                WorldCoordinate((start.x + end.x) * 0.5, (start.y + end.y) * 0.5),
                animate,
            )
            return true
        }
        route?.segments?.firstOrNull { it.id.equals(q, ignoreCase = true) }?.id?.let { id ->
            layout.segmentWorld[id]?.let { (start, end) ->
                centerOn(
                    WorldCoordinate((start.x + end.x) * 0.5, (start.y + end.y) * 0.5),
                    animate,
                )
                return true
            }
        }
        return false
    }

    fun centerOn(world: WorldCoordinate, animate: Boolean = true) {
        val (w, h) = viewSize()
        if (w <= 0f || h <= 0f) return
        val camera = viewport.camera
        val fromScale = camera.scale
        val fromOx = camera.offsetX
        val fromOy = camera.offsetY
        val toOx = w * 0.5f - (world.x * fromScale).toFloat()
        val toOy = h * 0.5f - (world.y * fromScale).toFloat()
        if (!animate) {
            camera.setTransform(fromScale, toOx, toOy)
            invalidate()
            return
        }
        animateCamera(fromScale, fromOx, fromOy, fromScale, toOx, toOy)
    }

    fun fling(vx: Float, vy: Float) {
        animator?.cancel()
        val camera = viewport.camera
        val duration = (hypot(vx.toDouble(), vy.toDouble()) / 8.0).toLong().coerceIn(120L, 600L)
        val startOx = camera.offsetX
        val startOy = camera.offsetY
        val endOx = startOx + vx / 4f
        val endOy = startOy + vy / 4f
        animateCamera(camera.scale, startOx, startOy, camera.scale, endOx, endOy, duration)
    }

    fun zoomAt(factor: Float, focusX: Float, focusY: Float) {
        viewport.camera.zoomAt(factor, focusX, focusY)
        invalidate()
    }

    fun panBy(dx: Float, dy: Float) {
        viewport.pan(dx, dy)
        invalidate()
    }

    fun cancel() {
        animator?.cancel()
        animator = null
    }

    private fun animateCamera(
        fromScale: Float,
        fromOx: Float,
        fromOy: Float,
        toScale: Float,
        toOx: Float,
        toOy: Float,
        durationMs: Long = 280L,
    ) {
        animator?.cancel()
        val camera = viewport.camera
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                val t = value.animatedValue as Float
                camera.setTransform(
                    scale = fromScale + (toScale - fromScale) * t,
                    offsetX = fromOx + (toOx - fromOx) * t,
                    offsetY = fromOy + (toOy - fromOy) * t,
                )
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                }
            })
        }
        animator = anim
        anim.start()
    }
}
