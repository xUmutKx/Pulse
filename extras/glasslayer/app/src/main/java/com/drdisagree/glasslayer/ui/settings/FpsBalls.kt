package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.theme.Accent
import com.drdisagree.glasslayer.ui.theme.OnInk
import kotlin.math.pow

@Composable
internal fun FpsBalls(fps: Int, displayRate: Int, modifier: Modifier = Modifier, ball: Dp? = null) {
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = it - start }
    }
    val lane = OnInk.copy(alpha = LANE_ALPHA)
    val accent = Accent
    val second = lerp(Accent, Color.White, SECOND_LIGHT)
    Canvas(modifier) {
        val radius = ball?.toPx() ?: (size.minDimension * SCENE_BALL)
        val rate = displayRate.coerceAtLeast(1)
        val step = NANOS / fps.coerceIn(1, rate)
        val stepped = time / step * step
        val ground = size.height - GROUND_GAP.toPx()
        drawLine(lane, Offset(0f, ground), Offset(size.width, ground), strokeWidth = LANE_WIDTH.toPx(), cap = StrokeCap.Round)
        drawThrow(stepped + LOOP_NANOS / 2, ground, radius * SECOND_SIZE, SECOND_DROP, second)
        drawThrow(stepped, ground, radius, 1f, accent)
    }
}

private fun DrawScope.drawThrow(nanos: Long, ground: Float, radius: Float, drop: Float, color: Color) {
    val progress = (nanos % LOOP_NANOS).toFloat() / THROW_NANOS
    if (progress > 1f) return
    val x = -radius + (size.width + radius * 2f) * progress
    val rest = ground - radius - LANE_WIDTH.toPx()
    val top = radius + (rest - radius) * (1f - drop)
    val y = rest - bounceHeight(progress) * (rest - top)
    drawBall(x, y, radius, color)
}

private fun bounceHeight(progress: Float): Float {
    var time = progress * TOTAL_TIME
    if (time < 1f) return 1f - time * time
    time -= 1f
    var speed = 2f
    repeat(BOUNCES) {
        speed *= RESTITUTION
        if (time < speed) return (speed * time - time * time).coerceAtLeast(0f)
        time -= speed
    }
    return 0f
}

private val TOTAL_TIME = 1f + (1..BOUNCES).sumOf { 2.0 * RESTITUTION.pow(it) }.toFloat()

private fun DrawScope.drawBall(x: Float, y: Float, radius: Float, color: Color) {
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(color, Color.White, LIGHT), color),
            center = Offset(x - radius * LIGHT_OFFSET, y - radius * LIGHT_OFFSET),
            radius = radius * LIGHT_REACH
        ),
        radius,
        Offset(x, y)
    )
}

private const val NANOS = 1_000_000_000L
private const val THROW_NANOS = 1_200_000_000L
private const val LOOP_NANOS = 1_800_000_000L
private const val BOUNCES = 2
private const val RESTITUTION = 0.7f
private val GROUND_GAP = 4.dp
private const val SECOND_SIZE = 0.75f
private const val SCENE_BALL = 0.198f
private const val SECOND_DROP = 0.75f
private const val SECOND_LIGHT = 0.4f
private const val LANE_ALPHA = 0.08f
private const val LIGHT = 0.45f
private const val LIGHT_OFFSET = 0.35f
private const val LIGHT_REACH = 1.6f
private val LANE_WIDTH = 2.dp
