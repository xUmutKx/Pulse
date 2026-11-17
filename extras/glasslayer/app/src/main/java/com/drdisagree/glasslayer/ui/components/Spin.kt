package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.isActive
import kotlin.math.ceil

@Composable
fun rememberSpin(active: Boolean, durationMs: Int): State<Float> {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (isActive) angle.animateTo(angle.value + FULL_TURN, tween(durationMs, easing = LinearEasing))
        } else if (angle.value % FULL_TURN != 0f) {
            angle.animateTo(ceil(angle.value / FULL_TURN) * FULL_TURN, tween(SETTLE_MS))
            angle.snapTo(0f)
        }
    }
    return angle.asState()
}

private const val FULL_TURN = 360f
private const val SETTLE_MS = 260
