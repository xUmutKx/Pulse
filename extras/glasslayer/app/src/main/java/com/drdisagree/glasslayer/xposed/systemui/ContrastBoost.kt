package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.ColorUtils

internal object ContrastBoost {

    private const val DARKEN = 0.7f
    private const val LIGHTEN = 0.6f
    private const val CHANNEL = 255f
    private const val LIGHT_FILL = 0.5f

    private val hsl = FloatArray(3)

    val darkPaint = Paint().apply { colorFilter = scaleFilter(DARKEN, 0f) }
    val lightPaint = Paint().apply { colorFilter = scaleFilter(1f - LIGHTEN, CHANNEL * LIGHTEN) }

    fun color(text: Int, fill: Int): Int {
        ColorUtils.colorToHSL(text, hsl)
        hsl[2] = if (Color.luminance(text) >= Color.luminance(fill)) hsl[2] + (1f - hsl[2]) * LIGHTEN else hsl[2] * DARKEN
        return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(text))
    }

    fun paintFor(fill: Int): Paint = if (Color.luminance(fill) >= LIGHT_FILL) darkPaint else lightPaint

    private fun scaleFilter(scale: Float, offset: Float): ColorMatrixColorFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}
