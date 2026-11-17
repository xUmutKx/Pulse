package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Matrix
import android.view.View
import com.drdisagree.glasslayer.xposed.hook.field

internal object GhostPlacement {

    private const val GHOST_VIEW = "mGhostView"
    private const val MAX_DEPTH = 16

    private val matrix = Matrix()
    private val points = FloatArray(4)
    private val viewLocation = IntArray(2)
    private val ghostedLocation = IntArray(2)
    private val ghostLocation = IntArray(2)

    fun resolve(view: View, width: Float, height: Float, out: FloatArray): View? {
        var current: View? = view
        var depth = 0
        var ghosted: View? = null
        var ghost: View? = null
        while (current != null && depth < MAX_DEPTH) {
            val candidate = current.field(GHOST_VIEW) as? View
            if (candidate != null) {
                ghosted = current
                ghost = candidate
                break
            }
            current = current.parent as? View
            depth++
        }
        if (ghosted == null || ghost == null || !ghost.isAttachedToWindow) return null

        view.getLocationInWindow(viewLocation)
        ghosted.getLocationInWindow(ghostedLocation)
        ghost.getLocationOnScreen(ghostLocation)
        val offsetX = (viewLocation[0] - ghostedLocation[0]).toFloat()
        val offsetY = (viewLocation[1] - ghostedLocation[1]).toFloat()

        matrix.set(ghosted.matrix)
        matrix.postTranslate(ghosted.left.toFloat(), ghosted.top.toFloat())
        ghost.animationMatrix?.let { matrix.postConcat(it) }
        points[0] = offsetX
        points[1] = offsetY
        points[2] = offsetX + width
        points[3] = offsetY + height
        matrix.mapPoints(points)

        out[0] = ghostLocation[0] + points[0]
        out[1] = ghostLocation[1] + points[1]
        out[2] = if (width > 0f) (points[2] - points[0]) / width else 1f
        out[3] = if (height > 0f) (points[3] - points[1]) / height else 1f
        ghost.postInvalidateOnAnimation()
        view.postInvalidateOnAnimation()
        return ghost.rootView
    }
}
