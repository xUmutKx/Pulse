package com.drdisagree.glasslayer.xposed.glass

import android.graphics.Rect
import android.view.SurfaceControl

interface BackdropClient {

    val displayId: Int

    val preferredScale: Float
        get() = 0.5f

    val preferredBlur: Float
        get() = 0f

    val wantsFullDisplay: Boolean
        get() = false

    val refreshRate: Float
        get() = DEFAULT_REFRESH_RATE

    val captureToken: Any?
        get() = null

    fun captureRect(out: Rect): Boolean

    fun excludeLayers(out: MutableCollection<SurfaceControl>)

    fun onBackdrop(frame: BackdropFrame)
}

const val DEFAULT_REFRESH_RATE = 60f
