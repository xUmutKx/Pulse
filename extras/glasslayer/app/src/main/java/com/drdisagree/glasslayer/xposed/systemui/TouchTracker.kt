package com.drdisagree.glasslayer.xposed.systemui

import android.view.MotionEvent
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.hook.HookEnv
import com.drdisagree.glasslayer.xposed.hook.hookAllBefore

internal object TouchTracker {

    private const val COMPOSE_VIEW = "androidx.compose.ui.platform.AndroidComposeView"

    var down = false
        private set
    var downX = 0f
        private set
    var downY = 0f
        private set
    var x = 0f
        private set
    var y = 0f
        private set

    @Volatile
    var listening = false

    fun install(): Int = HookEnv.findClass(COMPOSE_VIEW).hookAllBefore("dispatchTouchEvent") { chain ->
        val event = chain.args.firstOrNull() as? MotionEvent ?: return@hookAllBefore
        track(event)
    }

    fun pressing(left: Float, top: Float, width: Float, height: Float): Boolean =
        down && downX >= left && downX <= left + width && downY >= top && downY <= top + height

    private fun track(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                downX = event.rawX
                downY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> down = false
        }
        x = event.rawX
        y = event.rawY
        if (listening) ComposeLiquidGlass.requestFrame()
    }
}
