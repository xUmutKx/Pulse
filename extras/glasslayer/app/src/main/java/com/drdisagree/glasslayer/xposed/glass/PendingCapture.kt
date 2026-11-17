package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Bitmap
import android.graphics.Rect

internal class PendingCapture(
    val bitmap: Bitmap,
    val captureRect: Rect,
    val scale: Float,
    val blurRadius: Float,
    val fullDisplay: Boolean,
    val startedAt: Long,
    val token: Any?
)
