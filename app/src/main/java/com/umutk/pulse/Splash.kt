package com.umutk.pulse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Opening screen. Each look opens its own way: Windows 7 a ring of dots, Windows 11 a sliding bar,
 * Windows 8 pulsing tiles, Windows 10 a row of dots, XP a green block bar, Windows 95 a block bar in a
 * bevel, Material a turning arc, AMOLED a pulse line drawn on black. The logo follows the look too.
 * Tap to skip.
 */
@Composable
fun Splash(th: PTheme, onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val t by rememberInfiniteTransition(label = "boot").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(200)) }
        delay(850)
        onDone()
    }
    val mono = th.id == "amoled"
    val background: Brush = when (th.id) {
        "win8" -> SolidColor(th.accent)
        "amoled" -> SolidColor(Color.Black)
        else -> th.title ?: SolidColor(th.bg)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().background(background).clickable { onDone() }.graphicsLayer { alpha = fade.value },
    ) {
        when (th.id) {
            "material" -> RobotMark(th.accent, th.panel)
            "amoled" -> PulseMark(Color.White)
            else -> WindowsMark(if (th.id == "win8") Color.White else th.accent)
        }
        Spacer(Modifier.height(18.dp))
        Text("Pulse", color = if (mono) Color.White else if (th.id == "win8") Color.White else th.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        when (th.id) {
            "win11" -> SlidingBar(th.accent, th.line, t)
            "win8" -> MetroTiles(Color.White, t)
            "win10" -> DotRow(th.accent, t)
            "win7" -> DotRing(th.accent, t)
            "xp" -> BlockBar(Color(0xFF2BB12B), Color(0xFF0A246A), t)
            "win95" -> BevelBlocks(Color(0xFF000080), th.bg, t)
            "material" -> TurningArc(th.accent, t)
            else -> PulseLine(Color.White, t)
        }
    }
}

/** Windows-style mark: four rounded panes. Generic shape, not Microsoft's artwork. */
@Composable
private fun WindowsMark(color: Color) {
    Canvas(Modifier.size(96.dp)) {
        val gap = size.minDimension * 0.06f
        val cell = (size.minDimension - gap) / 2f
        val r = CornerRadius(cell * 0.12f)
        for (x in listOf(0f, cell + gap)) for (y in listOf(0f, cell + gap)) {
            drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(cell, cell), cornerRadius = r)
        }
    }
}

/** Android-style robot head drawn here, not Google's artwork. */
@Composable
private fun RobotMark(color: Color, eye: Color) {
    Canvas(Modifier.size(96.dp)) {
        val k = size.minDimension / 96f
        drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(16f * k, 24f * k), size = Size(64f * k, 64f * k))
        drawRoundRect(color, topLeft = Offset(16f * k, 56f * k), size = Size(64f * k, 28f * k), cornerRadius = CornerRadius(6f * k))
        drawCircle(eye, radius = 5f * k, center = Offset(37f * k, 52f * k))
        drawCircle(eye, radius = 5f * k, center = Offset(59f * k, 52f * k))
        drawLine(color, Offset(34f * k, 26f * k), Offset(28f * k, 12f * k), strokeWidth = 4f * k, cap = StrokeCap.Round)
        drawLine(color, Offset(62f * k, 26f * k), Offset(68f * k, 12f * k), strokeWidth = 4f * k, cap = StrokeCap.Round)
    }
}

/** Monochrome pulse line inside a ring. */
@Composable
private fun PulseMark(color: Color) {
    Canvas(Modifier.size(96.dp)) {
        val k = size.minDimension / 108f
        drawCircle(color, radius = 46f * k, center = Offset(54f * k, 54f * k), style = Stroke(width = 4f * k))
        drawPath(pulsePath(k), color, style = Stroke(width = 4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun pulsePath(k: Float) = Path().apply {
    moveTo(26f * k, 54f * k); lineTo(38f * k, 54f * k); lineTo(44f * k, 40f * k)
    lineTo(52f * k, 68f * k); lineTo(60f * k, 34f * k); lineTo(66f * k, 54f * k); lineTo(82f * k, 54f * k)
}

/** 0 → 1 → 0 with eased ends, so a bar can go to one side and back. */
private fun pingPong(t: Float) = 0.5f - 0.5f * cos(2f * PI.toFloat() * t)

@Composable
private fun SlidingBar(accent: Color, track: Color, t: Float) {
    Canvas(Modifier.size(width = 160.dp, height = 4.dp)) {
        drawRect(track)
        val bw = size.width * 0.3f
        drawRect(accent, topLeft = Offset(pingPong(t) * (size.width - bw), 0f), size = Size(bw, size.height))
    }
}

@Composable
private fun MetroTiles(color: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val gap = size.minDimension * 0.08f
        val cell = (size.minDimension - gap) / 2f
        var i = 0
        for (y in listOf(0f, cell + gap)) for (x in listOf(0f, cell + gap)) {
            val wave = (sin(2f * PI.toFloat() * (t - i * 0.12f)) + 1f) / 2f
            drawRect(color.copy(alpha = 0.25f + 0.75f * wave), topLeft = Offset(x, y), size = Size(cell, cell))
            i++
        }
    }
}

@Composable
private fun DotRow(accent: Color, t: Float) {
    Canvas(Modifier.size(width = 120.dp, height = 24.dp)) {
        val step = size.width / 5f
        for (i in 0 until 5) {
            val wave = (sin(2f * PI.toFloat() * (t - i * 0.1f)) + 1f) / 2f
            drawCircle(accent.copy(alpha = 0.3f + 0.7f * wave), radius = 4.dp.toPx() + 3.dp.toPx() * wave, center = Offset(step * (i + 0.5f), size.height / 2f))
        }
    }
}

/** Eight dots in a ring; the head is the brightest and the ring turns once per cycle. */
@Composable
private fun DotRing(accent: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val r = size.minDimension / 2f - 6f
        val dot = size.minDimension * 0.07f
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 8) {
            val head = ((t - i / 8f) % 1f + 1f) % 1f
            val a = Math.toRadians(-90.0 + i * 45.0)
            val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
            drawCircle(accent.copy(alpha = 0.15f + 0.85f * (1f - head)), radius = dot, center = p)
        }
    }
}

/** XP style: a row of green blocks, a short run of them lit and moving along the bar. */
@Composable
private fun BlockBar(block: Color, box: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 16.dp)) {
        drawRect(box.copy(alpha = 0.35f), size = size)
        val n = 14
        val w = size.width / n
        val head = floor(t * n).toInt()
        for (i in 0 until n) {
            val lit = ((head - i) % n + n) % n < 5
            if (lit) drawRect(block, topLeft = Offset(i * w + w * 0.15f, size.height * 0.2f), size = Size(w * 0.7f, size.height * 0.6f))
        }
    }
}

/** Windows 95 style: a sunken bevel with navy blocks that fill up and start again. */
@Composable
private fun BevelBlocks(block: Color, face: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 18.dp)) {
        drawRect(Color(0xFF808080), size = size)
        drawRect(Color.White, topLeft = Offset(2f, 2f), size = Size(size.width - 2f, size.height - 2f))
        drawRect(Color(0xFFC0C0C0), topLeft = Offset(3f, 3f), size = Size(size.width - 6f, size.height - 6f))
        val n = 12
        val w = (size.width - 12f) / n
        val filled = floor(t * (n + 1)).toInt().coerceAtMost(n)
        for (i in 0 until filled) drawRect(block, topLeft = Offset(6f + i * w + 1f, 6f), size = Size(w - 3f, size.height - 12f))
    }
}

@Composable
private fun TurningArc(accent: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val stroke = 5.dp.toPx()
        val sweep = 60f + 240f * abs(sin(PI.toFloat() * t))
        drawArc(accent, startAngle = t * 360f - 90f, sweepAngle = sweep, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/** The pulse line is drawn left to right, then starts over. */
@Composable
private fun PulseLine(color: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 60.dp)) {
        val k = size.minDimension / 108f
        val p = pulsePath(k)
        drawPath(p, color.copy(alpha = 0.25f), style = Stroke(width = 3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        clipRect(right = size.width * t) {
            drawPath(p, color, style = Stroke(width = 3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
