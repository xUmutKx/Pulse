package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PopAnimation {

    private val stretchX = Animatable(CLOSED_SCALE)
    private val stretchY = Animatable(CLOSED_SCALE)
    private val fade = Animatable(0f)

    val scaleX: Float get() = stretchX.value
    val scaleY: Float get() = stretchY.value
    val alpha: Float get() = fade.value.coerceIn(0f, 1f)
    val hidden: Boolean get() = fade.value <= 0f

    suspend fun show() = coroutineScope {
        launch { stretchX.animateTo(1f, spring(DAMPING_X, STIFFNESS_X)) }
        launch { stretchY.animateTo(1f, spring(DAMPING_Y, STIFFNESS_Y)) }
        fade.animateTo(1f, tween(FADE_IN_MS))
    }

    suspend fun hide() = coroutineScope {
        launch { stretchX.animateTo(CLOSED_SCALE, tween(CLOSE_MS)) }
        launch { stretchY.animateTo(CLOSED_SCALE, tween(CLOSE_MS)) }
        fade.animateTo(0f, tween(CLOSE_MS))
    }
}

private const val CLOSED_SCALE = 0.7f
private const val DAMPING_X = 0.68f
private const val STIFFNESS_X = 460f
private const val DAMPING_Y = 0.58f
private const val STIFFNESS_Y = 360f
private const val FADE_IN_MS = 120
private const val CLOSE_MS = 150
