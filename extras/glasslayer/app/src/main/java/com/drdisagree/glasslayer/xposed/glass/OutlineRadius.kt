package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable

internal object OutlineRadius {

    private val outline = Outline()
    private val rect = Rect()

    fun of(drawable: Drawable?): Float? {
        if (drawable == null || drawable.bounds.isEmpty) return null
        outline.setEmpty()
        drawable.getOutline(outline)
        if (!outline.getRect(rect)) return null
        return outline.radius.takeIf { it > 0f }
    }
}
