package com.umutk.pulse.extras.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * The Windows looks are a PC: where a finger touches, a mouse arrow shows for a moment and fades. It only draws; the touch goes on to what is under it.
 */
@Composable
fun WindowsCursor(enabled: Boolean, content: @Composable () -> Unit) {
    var pos by remember { mutableStateOf<Offset?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(tick) { if (tick > 0) { alpha.snapTo(1f); delay(900); alpha.animateTo(0f, tween(400)) } }
    Box(Modifier.fillMaxSize().pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture { val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); pos = d.position; tick++ }
    }) {
        content()
        val p = pos
        if (enabled && p != null && alpha.value > 0.01f) Canvas(Modifier.fillMaxSize()) {
            val k = density * 1.25f
            val arrow = Path().apply {
                moveTo(0f, 0f); lineTo(0f, 17f * k); lineTo(4.2f * k, 13.2f * k); lineTo(7.2f * k, 20f * k); lineTo(9.8f * k, 18.9f * k); lineTo(6.9f * k, 12.3f * k); lineTo(12f * k, 12.3f * k); close()
            }
            val a = alpha.value
            translate(p.x, p.y) {
                drawPath(arrow, Color.White.copy(alpha = a))
                drawPath(arrow, Color.Black.copy(alpha = a), style = Stroke(width = 1.3f * density))
            }
        }
    }
}
