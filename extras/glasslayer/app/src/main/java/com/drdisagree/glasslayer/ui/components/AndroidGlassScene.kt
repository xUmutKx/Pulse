package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AndroidGlassScene(modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val scene = rememberLayerBackdrop()
    val phase by rememberInfiniteTransition(label = "androidGlass").animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(HOVER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "androidGlassHover"
    )
    BoxWithConstraints(modifier) {
        val sceneHeight = maxHeight - topInset
        val radius = minOf(sceneHeight * HEIGHT_RATIO, maxWidth * WIDTH_RATIO)
        val centerX = maxWidth / 2
        val centerY = topInset + sceneHeight / 2
        val baseY = centerY + radius * BASE_SHIFT
        val paneSize = radius * PANE_RATIO
        val angle = phase * 2f * PI.toFloat()
        val paneCenterX = centerX + radius * PANE_X + radius * HOVER_X * cos(angle)
        val paneCenterY = baseY - radius * PANE_Y + radius * HOVER_Y * sin(angle)
        Canvas(
            Modifier
                .matchParentSize()
                .layerBackdrop(scene)
        ) {
            val base = Offset(centerX.toPx(), baseY.toPx())
            val r = radius.toPx()
            glow(base - Offset(0f, r * 0.45f), r * 2.1f, AndroidGreen, GREEN_GLOW)
            glow(Offset(size.width * 0.82f, base.y - r * 1.1f), r * 1.5f, Blue, BLUE_GLOW)
            robot(base, r)
        }
        Box(
            Modifier
                .offset(x = paneCenterX - paneSize / 2, y = paneCenterY - paneSize / 2)
                .size(paneSize)
                .drawBackdrop(
                    backdrop = scene,
                    shape = { RoundedCornerShape(paneSize * PANE_CORNER) },
                    effects = {
                        blur(PANE_BLUR.toPx())
                        lens(paneSize.toPx() * REFRACTION_HEIGHT, paneSize.toPx() * REFRACTION_AMOUNT, depthEffect = true)
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = { drawRect(PaneFill) }
                )
        )
    }
}

private fun DrawScope.robot(base: Offset, radius: Float) {
    val bounds = Rect(base.x - radius * 1.2f, base.y - radius * 1.3f, base.x + radius * 1.2f, base.y + 1f)
    drawIntoCanvas { it.saveLayer(bounds, Paint()) }
    drawArc(
        AndroidGreen,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(base.x - radius, base.y - radius),
        size = Size(radius * 2f, radius * 2f)
    )
    for (side in listOf(-1f, 1f)) {
        drawLine(
            AndroidGreen,
            Offset(base.x + side * radius * 0.40f, base.y - radius * 0.80f),
            Offset(base.x + side * radius * 0.58f, base.y - radius * 1.12f),
            strokeWidth = radius * 0.144f,
            cap = StrokeCap.Round
        )
        drawCircle(Color.Black, radius * 0.085f, Offset(base.x + side * radius * 0.40f, base.y - radius * 0.38f), blendMode = BlendMode.Clear)
    }
    drawIntoCanvas { it.restore() }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius), radius, center)
}

private const val HEIGHT_RATIO = 0.38f
private const val WIDTH_RATIO = 0.22f
private const val BASE_SHIFT = 0.46f
private const val PANE_RATIO = 1.13f
private const val PANE_CORNER = 0.21f
private const val PANE_X = 0.64f
private const val PANE_Y = 0.29f
private const val HOVER_X = 0.07f
private const val HOVER_Y = 0.05f
private const val HOVER_MS = 7000
private const val REFRACTION_HEIGHT = 0.2f
private const val REFRACTION_AMOUNT = 0.22f
private const val GREEN_GLOW = 0.18f
private const val BLUE_GLOW = 0.12f
private val PANE_BLUR = 3.dp
private val AndroidGreen = Color(0xFF3DDC84)
private val Blue = Color(0xFF0A6EC8)
private val PaneFill = Color.White.copy(alpha = 0.05f)
