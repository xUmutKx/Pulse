package com.drdisagree.glasslayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassScene(
    modifier: Modifier = Modifier,
    accent: Color = SCENE_ACCENT,
    dim: Float = 0f,
    orbScale: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "glassScene")
    val phase by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "glassScenePhase"
    )
    val tone by animateColorAsState(accent, tween(COLOR_MS), label = "sceneAccent")
    Canvas(modifier) {
        val angle = phase * 2f * PI.toFloat()
        val unit = size.minDimension
        drawRect(Brush.linearGradient(BASE, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.7f, size.height)))
        drawRect(Brush.radialGradient(listOf(SKY, Color.Transparent), Offset(size.width * 0.15f, 0f), size.maxDimension * 0.7f))
        val margin = unit * MARGIN
        val iceR = (unit * ICE_R * orbScale).coerceAtMost(unit / 2f - margin)
        val orbR = (unit * ORB_R * orbScale).coerceAtMost(unit / 2f - margin)
        val ice = inside(iceR + margin, ICE_X + DRIFT_X * cos(angle), ICE_Y + DRIFT_Y * sin(angle))
        val orb = inside(orbR + margin, ORB_X - DRIFT_X * cos(angle + PHASE_SHIFT), ORB_Y - DRIFT_Y * sin(angle + PHASE_SHIFT))
        halo(orb, orbR, tone)
        halo(ice, iceR, ICE_HALO)
        sphere(orb, orbR, lerp(tone, Color.White, 0.45f), tone, lerp(tone, DEEP, 0.55f))
        sphere(ice, iceR, Color.White, ICE, ICE_EDGE)
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
    }
}

private fun DrawScope.inside(inset: Float, fx: Float, fy: Float): Offset = Offset(
    inset + (size.width - inset * 2f).coerceAtLeast(0f) * fx.coerceIn(0f, 1f),
    inset + (size.height - inset * 2f).coerceAtLeast(0f) * fy.coerceIn(0f, 1f)
)

private fun DrawScope.halo(center: Offset, radius: Float, color: Color) {
    val reach = radius * HALO_REACH
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = HALO_ALPHA),
            radius / reach to color.copy(alpha = HALO_ALPHA * 0.6f),
            1f to Color.Transparent,
            center = center,
            radius = reach
        ),
        reach,
        center
    )
}

private fun DrawScope.sphere(center: Offset, radius: Float, light: Color, body: Color, edge: Color) {
    drawCircle(
        Brush.radialGradient(
            0f to light,
            0.45f to body,
            1f to edge,
            center = Offset(center.x - radius * LIGHT_OFFSET, center.y - radius * LIGHT_OFFSET),
            radius = radius * LIGHT_REACH
        ),
        radius,
        center
    )
}

val SCENE_ACCENT = Color(0xFF3FA9F5)

private const val LOOP_MS = 22_000
private const val COLOR_MS = 700
private const val DRIFT_X = 0.2f
private const val DRIFT_Y = 0.3f
private const val PHASE_SHIFT = 2.2f
private const val MARGIN = 0.05f
private const val ICE_X = 0.22f
private const val ICE_Y = 0.6f
private const val ICE_R = 0.36f
private const val ORB_X = 0.8f
private const val ORB_Y = 0.35f
private const val ORB_R = 0.27f
private const val HALO_REACH = 1.9f
private const val HALO_ALPHA = 0.22f
private const val LIGHT_OFFSET = 0.35f
private const val LIGHT_REACH = 1.45f
private val BASE = listOf(Color(0xFF14374F), Color(0xFF0B2233), Color(0xFF06131D))
private val SKY = Color.White.copy(alpha = 0.08f)
private val DEEP = Color(0xFF06131D)
private val ICE = Color(0xFFDDF3FF)
private val ICE_EDGE = Color(0xFF9FD3F2)
private val ICE_HALO = Color(0xFFBFE9FF)
