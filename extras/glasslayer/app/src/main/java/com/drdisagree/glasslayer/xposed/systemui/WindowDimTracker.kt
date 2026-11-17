package com.drdisagree.glasslayer.xposed.systemui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.Guard
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore

object WindowDimTracker {

    private const val VIEW_ROOT = "android.view.ViewRootImpl"
    private const val ATTACH_TIMEOUT_MS = 1000L
    private const val DIM_FADE_MS = 350L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun install(): Int = HookEnv.findClass(VIEW_ROOT).hookAllBefore("setView") { chain ->
        val root = chain.args.getOrNull(0) as? View ?: return@hookAllBefore
        val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams ?: return@hookAllBefore
        Guard.run("WindowDimTracker.window", Unit) { track(root, params) }
    }

    private fun track(root: View, params: WindowManager.LayoutParams) {
        if (!GlassConfig.isActive || Looper.myLooper() != Looper.getMainLooper()) return
        if (params.type < WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) return
        if (params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND == 0 || params.dimAmount <= 0f) return
        ComposeLiquidGlass.hold(root)
        root.addOnAttachStateChangeListener(DimWindowRelease { window -> releaseLater(window) })
        mainHandler.postDelayed({ if (!root.isAttachedToWindow) ComposeLiquidGlass.release(root) }, ATTACH_TIMEOUT_MS)
    }

    private fun releaseLater(window: View) {
        mainHandler.postDelayed({ Guard.run("WindowDimTracker.release", Unit) { ComposeLiquidGlass.release(window) } }, DIM_FADE_MS)
    }
}
