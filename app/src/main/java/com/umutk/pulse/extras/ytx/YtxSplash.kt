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

/**
 * YTXTube's opening: a white play mark and a thin line that fills (red over grey) on the dark page, over in about half a second, like the opening of any other app.
 * It covers the app while it gets ready underneath and then fades away.
 */
@Composable
internal fun YtxSplash(onDone: () -> Unit) {
    val fill = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(140))
        onDone()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Color(0xFF0F0F0F)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 300.dp, height = 40.dp)) {
            val h = size.height
            val mid = h / 2f
            // the play mark
            val tri = Path().apply { moveTo(0f, mid - h * 0.22f); lineTo(0f, mid + h * 0.22f); lineTo(h * 0.40f, mid); close() }
            drawPath(tri, Color.White)
            // the line: grey track, red fill
            val x0 = h * 0.40f + 14.dp.toPx(); val x1 = size.width
            val th = 3.dp.toPx()
            drawRoundRect(Color(0xFFD5D5D5), Offset(x0, mid - th / 2), Size(x1 - x0, th), CornerRadius(th / 2, th / 2))
            drawRoundRect(Color(0xFFFF0033), Offset(x0, mid - th / 2), Size((x1 - x0) * fill.value, th), CornerRadius(th / 2, th / 2))
        }
    }
}
