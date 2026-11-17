package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.Rect

data class BackdropFrame(
    val bitmap: Bitmap,
    val screenRect: Rect,
    val scale: Float,
    val blurred: Bitmap? = null,
    val blurRadius: Float = 0f,
    val capturedAt: Long = 0L,
    val token: Any? = null
)
