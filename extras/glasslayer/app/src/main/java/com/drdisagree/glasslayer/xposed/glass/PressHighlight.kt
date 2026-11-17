package com.drdisagree.glasslayer.xposed.glass

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

internal object PressHighlight {

    private const val FILL_ALPHA = 0.08f
    private const val SPOT_ALPHA = 0.15f
    private const val SPOT_RADIUS = 1.5f
    private const val SPOT_CORE = 0.5f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }

    fun draw(canvas: Canvas, shape: Path, width: Float, height: Float, x: Float, y: Float, press: Float) {
        if (press <= 0f || width <= 0f || height <= 0f) return
        val count = canvas.save()
        try {
            canvas.clipPath(shape)
            fillPaint.color = white(FILL_ALPHA * press)
            canvas.drawRect(0f, 0f, width, height, fillPaint)
            val radius = minOf(width, height) * SPOT_RADIUS
            spotPaint.shader = RadialGradient(
                x.coerceIn(0f, width),
                y.coerceIn(0f, height),
                radius,
                intArrayOf(white(SPOT_ALPHA * press), white(SPOT_ALPHA * press), Color.TRANSPARENT),
                floatArrayOf(0f, SPOT_CORE, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width, height, spotPaint)
        } finally {
            canvas.restoreToCount(count)
        }
    }

    private fun white(alpha: Float): Int = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)
}
