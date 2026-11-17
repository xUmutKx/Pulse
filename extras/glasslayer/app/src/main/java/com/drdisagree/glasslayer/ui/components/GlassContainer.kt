package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun GlassContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: (() -> Color)? = null,
    blurRadius: Dp = 4.dp,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = rememberLayerBackdrop()
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .graphicsLayer { translationX = shake.value * SHAKE_DISTANCE.toPx() }
            .glass(LocalBackdrop.current, shape, tint = tint, blurRadius = blurRadius, exported = surface)
            .clip(shape)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier else Modifier.semantics { disabled() })
        ) {
            CompositionLocalProvider(LocalBackdrop provides surface) {
                content()
            }
        }
        if (!enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                            scope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(0f, keyframes {
                                    durationMillis = SHAKE_MS
                                    -1f at SHAKE_MS / 6
                                    1f at SHAKE_MS * 2 / 6
                                    -0.6f at SHAKE_MS * 3 / 6
                                    0.6f at SHAKE_MS * 4 / 6
                                    -0.2f at SHAKE_MS * 5 / 6
                                })
                            }
                        }
                    }
            )
        }
    }
}

private val SHAKE_DISTANCE = 6.dp
private const val SHAKE_MS = 360
