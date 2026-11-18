package com.umutk.pulse.extras.ytx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun YtxSplash(onDone: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { pop.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            launch { ring.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
        }
        fade.animateTo(0f, tween(220))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Color.Black), contentAlignment = Alignment.Center) {
        Canvas(Modifier.graphicsLayer { val k = 0.55f + 0.45f * pop.value; scaleX = k; scaleY = k; alpha = pop.value }.size(112.dp)) {
            val w = size.width; val h = size.height * 0.72f; val top = (size.height - h) / 2f
            val r = h * 0.28f
            drawRoundRect(Color(0xFFFF0033), Offset(0f, top), Size(w, h), CornerRadius(r, r))
            val tri = Path().apply {
                moveTo(w * 0.40f, top + h * 0.26f); lineTo(w * 0.40f, top + h * 0.74f); lineTo(w * 0.68f, top + h * 0.50f); close()
            }
            drawPath(tri, Color.White)
            // a soft ring spreads from the button and fades
            drawCircle(Color(0x33FF0033), radius = size.minDimension * (0.45f + 0.7f * ring.value), alpha = 1f - ring.value)
        }
    }
}
