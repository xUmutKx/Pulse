package com.drdisagree.glasslayer.xposed.systemui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

internal object AccentColors {

    private const val SATURATION = 1
    private const val VALUE = 2
    private const val ACCENT_SATURATION = 0.4f
    private const val ACCENT_VALUE = 0.3f
    private const val LIGHT_FILL = 0.5f
    private const val DARK_TEXT = 0.3f
    private const val MAX_TEXT_DEPTH = 4

    private val hsv = FloatArray(3)

    fun isAccent(view: View): Boolean {
        val color = view.backgroundTintList?.let { it.getColorForState(view.drawableState, it.defaultColor) }
            ?: SolidShapes.of(view.background)?.color?.let { it.getColorForState(view.drawableState, it.defaultColor) }
            ?: return false
        if (isAccent(color)) return true
        if (Color.luminance(color) < LIGHT_FILL) return false
        val text = textColor(view, 0) ?: return false
        return Color.luminance(text) <= DARK_TEXT
    }

    private fun textColor(view: View, depth: Int): Int? {
        if (view is TextView) return view.currentTextColor
        if (view !is ViewGroup || depth >= MAX_TEXT_DEPTH) return null
        for (index in 0 until view.childCount) {
            textColor(view.getChildAt(index), depth + 1)?.let { return it }
        }
        return null
    }

    fun isAccent(color: Int): Boolean {
        Color.colorToHSV(color, hsv)
        return hsv[SATURATION] >= ACCENT_SATURATION && hsv[VALUE] >= ACCENT_VALUE
    }
}
