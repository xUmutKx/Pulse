package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import kotlin.math.roundToInt

internal object ColorBlend {

    private const val OPAQUE = 255

    fun over(top: Int, bottom: Int): Int {
        val topAlpha = Color.alpha(top) / OPAQUE.toFloat()
        val bottomAlpha = Color.alpha(bottom) / OPAQUE.toFloat()
        val alpha = topAlpha + bottomAlpha * (1f - topAlpha)
        if (alpha <= 0f) return Color.TRANSPARENT
        fun channel(top: Int, bottom: Int): Int =
            ((top * topAlpha + bottom * bottomAlpha * (1f - topAlpha)) / alpha).roundToInt().coerceIn(0, OPAQUE)
        return Color.argb(
            (alpha * OPAQUE).roundToInt(),
            channel(Color.red(top), Color.red(bottom)),
            channel(Color.green(top), Color.green(bottom)),
            channel(Color.blue(top), Color.blue(bottom))
        )
    }

    fun scaleAlpha(color: Int, fraction: Float): Int =
        Color.argb(
            (Color.alpha(color) * fraction).roundToInt().coerceIn(0, OPAQUE),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
}
