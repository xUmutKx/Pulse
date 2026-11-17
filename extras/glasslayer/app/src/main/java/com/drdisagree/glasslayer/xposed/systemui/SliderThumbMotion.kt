package com.drdisagree.glasslayer.xposed.systemui

import kotlin.math.abs
import kotlin.math.exp

internal class SliderThumbMotion {

    val thumb = KyantThumb()
    var press = 0f
        private set
    var animating = false
        private set

    private var restingThickness = 0f
    private var lastAt = 0L

    fun update(thickness: Float, now: Long) {
        if (thickness > restingThickness) restingThickness = thickness
        val held = thickness < restingThickness * PRESSED_RATIO
        val elapsed = if (lastAt == 0L) FRAME_MS else (now - lastAt).coerceAtLeast(1L).toFloat()
        lastAt = now
        val goal = if (held) 1f else 0f
        press += (goal - press) * (1f - exp(-elapsed / RESPONSE_MS))
        if (abs(goal - press) < SETTLED) press = goal
        animating = press != goal
    }

    private companion object {
        const val PRESSED_RATIO = 0.85f
        const val FRAME_MS = 16f
        const val RESPONSE_MS = 70f
        const val SETTLED = 0.01f
    }
}
