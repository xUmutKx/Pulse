package com.umutk.pulse.extras.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The kinds of sky the forecast codes fall into. */
enum class Sky { CLEAR, PARTLY, CLOUD, FOG, DRIZZLE, RAIN, SNOW, STORM }

fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY
    3 -> Sky.CLOUD
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> Sky.CLOUD
}

private val SUN = Color(0xFFFFC83D)
private val CLOUD = Color(0xFFE6EBF2)
private val CLOUD_DARK = Color(0xFF9AA7B8)
private val DROP = Color(0xFF4FA8FF)
private val BOLT = Color(0xFFFFD54F)

private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, c: Color) {
    val h = w * .55f
    drawCircle(c, h * .5f, Offset(cx - w * .22f, cy))
    drawCircle(c, h * .62f, Offset(cx + w * .02f, cy - h * .2f))
    drawCircle(c, h * .46f, Offset(cx + w * .26f, cy + h * .02f))
    drawRoundRect(c, Offset(cx - w * .36f, cy - h * .02f), Size(w * .78f, h * .5f), androidx.compose.ui.geometry.CornerRadius(h * .25f))
}

private fun DrawScope.sun(cx: Float, cy: Float, r: Float, spin: Float) {
    rotate(spin, Offset(cx, cy)) {
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            drawLine(SUN, Offset(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f), Offset(cx + cos(a) * r * 1.8f, cy + sin(a) * r * 1.8f), r * .22f, StrokeCap.Round)
        }
    }
    drawCircle(SUN, r, Offset(cx, cy))
}

private fun DrawScope.bolt(cx: Float, top: Float, h: Float) {
    val p = Path().apply {
        moveTo(cx + h * .1f, top); lineTo(cx - h * .28f, top + h * .55f); lineTo(cx - h * .02f, top + h * .55f)
        lineTo(cx - h * .16f, top + h); lineTo(cx + h * .3f, top + h * .38f); lineTo(cx + h * .04f, top + h * .38f); close()
    }
    drawPath(p, BOLT)
}

/** A small flat picture of the sky: sun, cloud, rain, snow, fog or a storm. Drawn, so every look has the same clear one. */
@Composable
fun WeatherGlyph(code: Int, size: Dp, modifier: Modifier = Modifier) {
    val k = skyOf(code)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = s / 2f
        when (k) {
            Sky.CLEAR -> sun(c, c, s * .24f, 0f)
            Sky.PARTLY -> { sun(s * .38f, s * .36f, s * .17f, 0f); cloud(s * .56f, s * .6f, s * .7f, CLOUD) }
            Sky.CLOUD -> { cloud(s * .4f, s * .46f, s * .5f, CLOUD_DARK); cloud(s * .56f, s * .6f, s * .7f, CLOUD) }
            Sky.FOG -> {
                cloud(c, s * .38f, s * .66f, CLOUD)
                for (i in 0..2) drawLine(CLOUD_DARK, Offset(s * (.2f + i * .05f), s * (.66f + i * .09f)), Offset(s * (.8f - i * .05f), s * (.66f + i * .09f)), s * .05f, StrokeCap.Round)
            }
            Sky.DRIZZLE, Sky.RAIN -> {
                cloud(c, s * .38f, s * .7f, CLOUD)
                val n = if (k == Sky.RAIN) 3 else 2
                for (i in 0 until n) {
                    val x = s * (.34f + i * (if (n == 3) .16f else .3f))
                    drawLine(DROP, Offset(x + s * .03f, s * .66f), Offset(x - s * .02f, s * .82f), s * .06f, StrokeCap.Round)
                }
            }
            Sky.SNOW -> {
                cloud(c, s * .38f, s * .7f, CLOUD)
                for (i in 0..2) drawCircle(Color.White, s * .045f, Offset(s * (.32f + i * .18f), s * (.72f + (i % 2) * .08f)))
            }
            Sky.STORM -> { cloud(c, s * .36f, s * .72f, CLOUD_DARK); bolt(c, s * .5f, s * .42f) }
        }
    }
}

/** The sky behind the hero card: a gradient by weather, with the weather moving in it (sun rays turning, clouds drifting, rain and snow falling, lightning). */
@Composable
fun WeatherScene(code: Int, modifier: Modifier = Modifier) {
    val k = skyOf(code)
    val t by rememberInfiniteTransition(label = "sky").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val flash by rememberInfiniteTransition(label = "flash").animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "f")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val (a, b) = when (k) {
            Sky.CLEAR -> Color(0xFF2E8BEA) to Color(0xFF8CD0FF)
            Sky.PARTLY -> Color(0xFF3F86D6) to Color(0xFF9CC7EE)
            Sky.CLOUD -> Color(0xFF5E7491) to Color(0xFFA5B4C6)
            Sky.FOG -> Color(0xFF7D8896) to Color(0xFFC1C8D0)
            Sky.DRIZZLE, Sky.RAIN -> Color(0xFF34455E) to Color(0xFF6C7F99)
            Sky.SNOW -> Color(0xFF6D86A6) to Color(0xFFDCE6F2)
            Sky.STORM -> Color(0xFF1E2535) to Color(0xFF495770)
        }
        drawRect(Brush.verticalGradient(listOf(a, b)))
        if (k == Sky.CLEAR || k == Sky.PARTLY) sun(w * .8f, h * .3f, h * .13f, t * 360f)
        if (k != Sky.CLEAR) {
            // clouds drifting across, each at its own speed
            val dark = k == Sky.RAIN || k == Sky.DRIZZLE || k == Sky.STORM
            for (i in 0..2) {
                val sp = 1f + i * .5f
                val x = ((t * sp + i * .37f) % 1f) * (w + h) - h * .5f
                cloud(x, h * (.22f + i * .22f), h * (.85f - i * .1f), (if (dark) CLOUD_DARK else CLOUD).copy(alpha = .55f - i * .1f))
            }
        }
        if (k == Sky.RAIN || k == Sky.DRIZZLE || k == Sky.STORM) {
            val n = if (k == Sky.DRIZZLE) 22 else 40
            for (i in 0 until n) {
                val seed = (i * 0.6180339f) % 1f
                val x = seed * w
                val y = ((t * (if (k == Sky.DRIZZLE) 1f else 2f) + seed * 3f) % 1f) * h
                drawLine(DROP.copy(alpha = .8f), Offset(x, y), Offset(x - h * .02f, y + h * .09f), 2.2f, StrokeCap.Round)
            }
        }
        if (k == Sky.SNOW) for (i in 0 until 36) {
            val seed = (i * 0.6180339f) % 1f
            val x = seed * w + sin((t + seed) * 2f * PI.toFloat()) * 8f
            val y = ((t * .6f + seed * 2.3f) % 1f) * h
            drawCircle(Color.White.copy(alpha = .9f), 2.5f + seed * 2.5f, Offset(x, y))
        }
        if (k == Sky.FOG) for (i in 0..3) {
            val y = h * (.25f + i * .2f); val off = ((t + i * .25f) % 1f) * w
            drawRoundRect(Color.White.copy(alpha = .22f), Offset(off - w, y), Size(w, h * .09f), androidx.compose.ui.geometry.CornerRadius(h * .05f))
            drawRoundRect(Color.White.copy(alpha = .22f), Offset(off, y), Size(w, h * .09f), androidx.compose.ui.geometry.CornerRadius(h * .05f))
        }
        if (k == Sky.STORM && flash in .92f..1f) { drawRect(Color.White.copy(alpha = .35f)); bolt(w * .7f, 0f, h * .8f) }
    }
}
