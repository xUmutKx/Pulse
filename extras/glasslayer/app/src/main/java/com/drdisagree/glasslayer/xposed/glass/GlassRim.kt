package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

internal object GlassRim {

    private const val STROKE_DP = 1f
    private const val TOP_ALPHA = 0.2f
    private const val BOTTOM_ALPHA = 0.06f
    private const val SHEEN_ALPHA = 0.14f
    private const val SHEEN_SPAN = 0.55f
    private const val SHEEN_MAX_DP = 32f
    private const val FADE_STOP = 0.5f
    private const val SEAM_ALPHA = 0.04f
    private const val SEAM_RADIUS_DP = 8f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun draw(canvas: Canvas, bounds: Rect, radii: FloatArray, density: Float, alpha: Float, sheen: Boolean = true, rim: Float = 1f, reflection: Float = 1f) {
        rect.set(bounds)
        draw(canvas, rect, radii, density, alpha, sheen, rim, reflection)
    }

    fun draw(canvas: Canvas, bounds: RectF, radii: FloatArray, density: Float, alpha: Float, sheen: Boolean = true, rim: Float = 1f, reflection: Float = 1f) {
        if (bounds.isEmpty || alpha <= 0f) return
        val stroke = STROKE_DP * density
        if (sheen && reflection > 0f) {
            path.rewind()
            path.addRoundRect(bounds, radii, Path.Direction.CW)
            sheenPaint.shader = LinearGradient(
                0f,
                bounds.top,
                0f,
                bounds.top + minOf(bounds.height() * SHEEN_SPAN, SHEEN_MAX_DP * density),
                white(SHEEN_ALPHA * alpha * reflection),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, sheenPaint)
        }
        if (rim <= 0f) return
        path.rewind()
        rect.set(bounds.left + stroke / 2f, bounds.top + stroke / 2f, bounds.right - stroke / 2f, bounds.bottom - stroke / 2f)
        val inset = FloatArray(radii.size) { (radii[it] - stroke / 2f).coerceAtLeast(0f) }
        path.addRoundRect(rect, inset, Path.Direction.CW)
        strokePaint.strokeWidth = stroke
        strokePaint.shader = LinearGradient(
            0f,
            bounds.top,
            0f,
            bounds.bottom,
            intArrayOf(white(TOP_ALPHA * alpha * rim), Color.TRANSPARENT, white(BOTTOM_ALPHA * alpha * rim)),
            floatArrayOf(0f, FADE_STOP, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, strokePaint)
    }

    fun drawSegment(canvas: Canvas, bounds: Rect, radii: FloatArray, density: Float, alpha: Float, rim: Float = 1f, reflection: Float = 1f) {
        if (bounds.isEmpty || alpha <= 0f) return
        val seam = SEAM_RADIUS_DP * density
        val openTop = maxOf(radii[0], radii[2]) <= seam
        val openBottom = maxOf(radii[4], radii[6]) <= seam
        rect.set(bounds)
        if (!openTop && reflection > 0f) {
            path.rewind()
            path.addRoundRect(rect, radii, Path.Direction.CW)
            sheenPaint.shader = LinearGradient(
                0f,
                rect.top,
                0f,
                rect.top + minOf(rect.height() * SHEEN_SPAN, SHEEN_MAX_DP * density),
                white(SHEEN_ALPHA * alpha * reflection),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, sheenPaint)
        }
        if (rim <= 0f) return
        val stroke = STROKE_DP * density
        rect.set(bounds.left + stroke / 2f, bounds.top + stroke / 2f, bounds.right - stroke / 2f, bounds.bottom - stroke / 2f)
        val inset = FloatArray(radii.size) { (radii[it] - stroke / 2f).coerceAtLeast(0f) }
        path.rewind()
        path.addRoundRect(rect, inset, Path.Direction.CW)
        strokePaint.strokeWidth = stroke
        strokePaint.shader = LinearGradient(
            0f,
            bounds.top.toFloat(),
            0f,
            bounds.bottom.toFloat(),
            intArrayOf(
                white((if (openTop) SEAM_ALPHA else TOP_ALPHA) * alpha * rim),
                white(SEAM_ALPHA * alpha * rim),
                white((if (openBottom) SEAM_ALPHA else BOTTOM_ALPHA) * alpha * rim)
            ),
            floatArrayOf(0f, FADE_STOP, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, strokePaint)
    }

    private fun white(alpha: Float): Int = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
}
