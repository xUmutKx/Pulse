package com.drdisagree.glasslayer.xposed.systemui

import com.drdisagree.glasslayer.xposed.glass.LiquidGlassStyle

data class SurfaceStyle(
    val blurRadiusPx: Float,
    val style: LiquidGlassStyle
)
