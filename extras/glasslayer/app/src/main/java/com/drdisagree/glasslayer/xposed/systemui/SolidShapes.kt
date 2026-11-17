package com.drdisagree.glasslayer.xposed.systemui

import android.R as AndroidR
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

internal object SolidShapes {

    fun of(drawable: Drawable?): GradientDrawable? = when (drawable) {
        is GradientDrawable -> drawable.takeIf { (it.color?.defaultColor?.let { color -> Color.alpha(color) } ?: 0) > 0 }
        is DrawableWrapper -> of(drawable.drawable)
        is DrawableContainer -> of(drawable.current)
        is LayerDrawable -> (0 until drawable.numberOfLayers).firstNotNullOfOrNull { index ->
            if (drawable.getId(index) == AndroidR.id.mask) null else of(drawable.getDrawable(index))
        }
        else -> null
    }
}
