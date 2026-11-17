package com.drdisagree.glasslayer.xposed.glass

import kotlin.math.abs
import kotlin.math.sqrt

internal class PressSpring {

    var value = 0f
        private set
    var animating = false
        private set

    private var velocity = 0f
    private var lastAt = 0L

    fun update(pressed: Boolean, now: Long) {
        val goal = if (pressed) 1f else 0f
        var elapsed = if (lastAt == 0L) FRAME_S else ((now - lastAt).coerceIn(1L, MAX_STEP_MS) / MILLIS)
        lastAt = now
        val damping = 2f * DAMPING_RATIO * sqrt(STIFFNESS)
        while (elapsed > 0f) {
            val step = minOf(elapsed, SUB_STEP_S)
            val force = -STIFFNESS * (value - goal) - damping * velocity
            velocity += force * step
            value += velocity * step
            elapsed -= step
        }
        if (abs(value - goal) < SETTLED && abs(velocity) < SETTLED) {
            value = goal
            velocity = 0f
        }
        animating = value != goal || velocity != 0f
    }

    private companion object {
        const val STIFFNESS = 300f
        const val DAMPING_RATIO = 0.5f
        const val FRAME_S = 0.016f
        const val SUB_STEP_S = 0.004f
        const val MAX_STEP_MS = 64L
        const val MILLIS = 1000f
        const val SETTLED = 0.001f
    }
}
