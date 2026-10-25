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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

/** A moon with a face: lit disc, grey seas and craters, a crescent cut from it. */
internal fun DrawScope.moonDisc(cx: Float, cy: Float, r: Float, crescent: Boolean = false) {
    val lit = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx - r, cy - r), Size(2 * r, 2 * r))) }
    clipPath(lit) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFBEA), Color(0xFFE9E3CC), Color(0xFFC9C3AD)), Offset(cx - r * .3f, cy - r * .3f), r * 1.5f), r, Offset(cx, cy))
        // the seas and craters
        val maria = listOf(Triple(-.28f, -.2f, .3f), Triple(.12f, -.35f, .22f), Triple(.25f, .08f, .26f), Triple(-.1f, .3f, .2f))
        maria.forEach { (x, y, k) -> drawCircle(Color(0xFF8C8672).copy(alpha = .35f), r * k, Offset(cx + r * x, cy + r * y)) }
        listOf(Triple(.4f, -.45f, .09f), Triple(-.5f, .15f, .08f), Triple(.05f, .55f, .07f), Triple(.55f, .42f, .06f)).forEach { (x, y, k) ->
            drawCircle(Color(0xFF8C8672).copy(alpha = .45f), r * k, Offset(cx + r * x, cy + r * y), style = Stroke(r * .03f))
        }
        // a crescent: a disc of sky colour cut over the left side
        if (crescent) drawCircle(Color(0xFF14213D).copy(alpha = .93f), r * .92f, Offset(cx - r * .55f, cy - r * .12f))
    }
}

/** A soft cloud: many round puffs, brighter on top, so it reads as vapour and not as a flat shape. */
private fun DrawScope.softCloud(cx: Float, cy: Float, w: Float, c: Color, alpha: Float) {
    val h = w * .5f
    val puffs = listOf(Triple(-.30f, .10f, .26f), Triple(-.12f, -.06f, .32f), Triple(.10f, -.12f, .36f), Triple(.30f, .02f, .28f), Triple(.02f, .12f, .34f), Triple(-.2f, .16f, .24f), Triple(.22f, .16f, .26f))
    puffs.forEach { (x, y, k) ->
        val r = w * k * .6f; val o = Offset(cx + w * x, cy + h * y)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * .55f), c.copy(alpha = 0f)), o, r * 1.25f), r * 1.25f, o)
    }
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
fun WeatherGlyph(code: Int, size: Dp, modifier: Modifier = Modifier, day: Boolean = true) {
    val k = skyOf(code)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = s / 2f
        when (k) {
            Sky.CLEAR -> if (day) sun(c, c, s * .24f, 0f) else moonDisc(c, c, s * .34f)
            Sky.PARTLY -> { if (day) sun(s * .38f, s * .36f, s * .17f, 0f) else moonDisc(s * .38f, s * .36f, s * .22f); cloud(s * .56f, s * .6f, s * .7f, CLOUD) }
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
fun WeatherScene(code: Int, modifier: Modifier = Modifier, day: Boolean = true) {
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
        // by night the sky is dark whatever the weather; the colours are pulled down
        if (day) drawRect(Brush.verticalGradient(listOf(a, b))) else drawRect(Brush.verticalGradient(listOf(lerp(a, Color(0xFF050914), .75f), lerp(b, Color(0xFF16213C), .6f))))
        if (!day && k != Sky.RAIN && k != Sky.STORM && k != Sky.CLOUD) for (i in 0 until 40) {
            val seed = (i * 0.6180339f) % 1f; val tw = .5f + .5f * sin((t * 2f + seed * 7f) * 2f * PI.toFloat())
            drawCircle(Color.White.copy(alpha = .25f + .6f * tw), 1.2f + seed * 1.6f, Offset(seed * w, ((i * 0.381966f) % 1f) * h * .7f))
        }
        if (k == Sky.CLEAR || k == Sky.PARTLY) { if (day) sun(w * .8f, h * .3f, h * .13f, t * 360f) else moonDisc(w * .8f, h * .3f, h * .16f) }
        if (k != Sky.CLEAR) {
            // clouds in layers: far ones small and slow, near ones large and quick, each bobbing a little; soft puffs, not flat shapes
            val dark = k == Sky.RAIN || k == Sky.DRIZZLE || k == Sky.STORM
            val heavy = dark || k == Sky.CLOUD || k == Sky.FOG
            val n = if (heavy) 7 else 5
            for (i in 0 until n) {
                val depth = (i % 3) / 2f                         // 0 far, 1 near
                val sp = .45f + depth * .9f
                val x = (((t * sp) + i * .29f) % 1f) * (w + h * 1.4f) - h * .7f
                val y = h * (.14f + ((i * 0.37f) % 1f) * .62f) + sin((t * 2f + i) * PI.toFloat()) * h * .012f
                val base = if (dark) CLOUD_DARK else CLOUD
                softCloud(x, y, h * (.7f + depth * .6f), if (!day) lerp(base, Color(0xFF38425A), .55f) else base, (.34f + depth * .3f) * (if (heavy) 1.15f else 1f))
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
