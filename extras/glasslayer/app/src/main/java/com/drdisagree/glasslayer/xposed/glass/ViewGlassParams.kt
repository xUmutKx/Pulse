package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Color

class ViewGlassParams(
    val blurRadius: Float,
    val style: LiquidGlassStyle,
    val openEdges: Boolean = false,
    val join: GlassJoin? = null,
    val dim: Int = Color.TRANSPARENT,
    val rimOnly: Boolean = false,
    val imageContent: Boolean = false,
    val rimOnTop: Boolean = false,
    val segmented: Boolean = false
)
