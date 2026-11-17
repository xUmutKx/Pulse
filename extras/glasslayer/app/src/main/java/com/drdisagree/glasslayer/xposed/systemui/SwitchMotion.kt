package com.drdisagree.glasslayer.xposed.systemui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal class SwitchMotion(checked: Boolean) {

    val thumb = KyantThumb()
    var fraction = if (checked) 1f else 0f
        private set
    var press = 0f
        private set
    var velocity = 0f
        private set
    var animating = false
        private set

    private var target = fraction
    private var from = fraction
    private var startAt = 0L
    private var lastAt = 0L

    fun update(checked: Boolean, now: Long) {
        val goal = if (checked) 1f else 0f
        if (goal != target) {
            from = fraction
            target = goal
            startAt = now
        }
        val progress = if (startAt == 0L) 1f else ((now - startAt) / DURATION_MS).coerceIn(0f, 1f)
        val previous = fraction
        fraction = (from + (target - from) * spring(progress)).coerceIn(0f, 1f)
        press = if (progress < 1f) sin(PI.toFloat() * progress) else 0f
        val elapsed = (now - lastAt).coerceAtLeast(1L)
        velocity = if (lastAt == 0L) 0f else (fraction - previous) * FRAME_MS / elapsed
        lastAt = now
        animating = progress < 1f
    }

    private fun spring(progress: Float): Float =
        1f - exp(-DAMPING * progress) * cos(FREQUENCY * progress)

    private companion object {
        const val DURATION_MS = 520f
        const val FRAME_MS = 16f
        const val DAMPING = 6f
        const val FREQUENCY = 9f
    }
}
