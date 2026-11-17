package com.drdisagree.glasslayer.xposed.systemui

import kotlin.math.abs
import kotlin.math.exp

internal class ViewSwitchMotion {

    val thumb = KyantThumb()
    var press = 0f
        private set
    var velocity = 0f
        private set
    var animating = false
        private set

    private var lastPosition = Float.NaN
    private var lastAt = 0L

    fun update(position: Float, held: Boolean, now: Long) {
        val elapsed = if (lastAt == 0L) FRAME_MS else (now - lastAt).coerceAtLeast(1L).toFloat()
        velocity = if (lastPosition.isNaN()) 0f else (position - lastPosition) * FRAME_MS / elapsed
        lastPosition = position
        lastAt = now
        val moving = abs(velocity) > MOVING
        val goal = if (held || moving) 1f else 0f
        press += (goal - press) * (1f - exp(-elapsed / RESPONSE_MS))
        if (abs(goal - press) < SETTLED) press = goal
        animating = press != goal || moving
    }

    private companion object {
        const val FRAME_MS = 16f
        const val RESPONSE_MS = 70f
        const val MOVING = 0.002f
        const val SETTLED = 0.01f
    }
}
