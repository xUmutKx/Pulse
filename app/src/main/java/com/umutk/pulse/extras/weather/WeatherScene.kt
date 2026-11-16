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
import kotlin.math.roundToInt
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

/**
 * The face of the moon, drawn (not a photograph) after the real one: bright highlands, a little darker towards the edge; the dark seas where they really are (Procellarum on the
 * left, Imbrium and Serenitatis above, Tranquillitatis, Fecunditatis and Crisium on the right, Nubium and Humorum below); Tycho with its rays at the foot, Copernicus and Aristarchus
 * as bright spots; small craters with a light rim. Drawn inside a disc of radius [r]; the caller clips it to what is lit.
 */
internal fun DrawScope.moonSurface(cx: Float, cy: Float, r: Float) {
    val c = Offset(cx, cy)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF6F2DE), Color(0xFFE7E1C9), Color(0xFFBEB8A1)), Offset(cx - r * .25f, cy - r * .3f), r * 1.45f), r, c)
    // a dark sea: a soft oval, strongest in the middle
    fun sea(x: Float, y: Float, rx: Float, ry: Float, a: Float, rot: Float = 0f) {
        val o = Offset(cx + r * x, cy + r * y)
        rotate(rot, o) {
            drawOval(Brush.radialGradient(listOf(Color(0xFF62605A).copy(alpha = a), Color(0xFF75726A).copy(alpha = a * .85f), Color.Transparent), o, r * maxOf(rx, ry)), Offset(o.x - r * rx, o.y - r * ry), Size(2 * r * rx, 2 * r * ry))
        }
    }
    sea(-.52f, -.12f, .34f, .40f, .72f); sea(-.62f, .10f, .20f, .26f, .62f)     // Oceanus Procellarum
    sea(-.14f, -.52f, .27f, .22f, .80f, -10f)                                  // Mare Imbrium
    sea(.17f, -.40f, .16f, .15f, .80f); sea(.02f, -.36f, .10f, .10f, .62f)      // Serenitatis, Vaporum
    sea(.38f, -.14f, .22f, .17f, .75f, 15f)                                    // Tranquillitatis
    sea(.62f, .14f, .10f, .15f, .60f, -25f)                                    // Fecunditatis
    sea(.70f, -.34f, .10f, .08f, .80f)                                         // Crisium
    sea(-.28f, .32f, .17f, .13f, .60f); sea(-.66f, .34f, .09f, .09f, .65f)      // Nubium, Humorum
    sea(.05f, -.84f, .30f, .06f, .45f)                                         // Frigoris
    // craters: a darker floor and a light rim
    fun crater(x: Float, y: Float, k: Float, a: Float = .4f) {
        val o = Offset(cx + r * x, cy + r * y)
        drawCircle(Color(0xFF6E6A5C).copy(alpha = a * .6f), r * k, o)
        drawCircle(Color.White.copy(alpha = a), r * k, Offset(o.x - r * k * .12f, o.y - r * k * .12f), style = Stroke(r * .012f))
    }
    listOf(Triple(-.36f, -.58f, .05f), Triple(.30f, .40f, .06f), Triple(.52f, .50f, .045f), Triple(-.05f, .12f, .04f), Triple(.14f, .22f, .035f), Triple(-.78f, -.05f, .04f),
        Triple(.80f, .12f, .035f), Triple(-.12f, .62f, .04f), Triple(.40f, -.62f, .035f), Triple(-.45f, .55f, .035f), Triple(.2f, .72f, .03f), Triple(.68f, -.55f, .03f)).forEach { (x, y, k) -> crater(x, y, k) }
    // Tycho with its rays, Copernicus and Aristarchus
    val tycho = Offset(cx + r * -.27f, cy + r * .60f)
    for (i in 0 until 16) {
        val a = i * (2 * PI.toFloat() / 16f) + .2f
        val len = r * (.30f + .22f * ((i * 37) % 7) / 7f)
        drawLine(Color.White.copy(alpha = .26f), tycho, Offset(tycho.x + cos(a) * len, tycho.y + sin(a) * len), r * .014f, StrokeCap.Round)
    }
    drawCircle(Color.White.copy(alpha = .95f), r * .035f, tycho)
    val cop = Offset(cx + r * -.22f, cy + r * -.28f)
    for (i in 0 until 8) { val a = i * (PI.toFloat() / 4f); drawLine(Color.White.copy(alpha = .22f), cop, Offset(cop.x + cos(a) * r * .1f, cop.y + sin(a) * r * .1f), r * .01f, StrokeCap.Round) }
    drawCircle(Color.White.copy(alpha = .9f), r * .028f, cop)
    drawCircle(Color.White.copy(alpha = .85f), r * .02f, Offset(cx + r * -.56f, cy + r * -.32f))
    // the edge is a little darker, so the disc reads as a ball
    drawCircle(Brush.radialGradient(0f to Color.Transparent, .70f to Color.Transparent, 1f to Color(0x4A000000), center = c, radius = r), r, c)
}

/** A moon with a face (see [moonSurface]); [crescent] cuts a sky-coloured disc over its left side. */
internal fun DrawScope.moonDisc(cx: Float, cy: Float, r: Float, crescent: Boolean = false) {
    val lit = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx - r, cy - r), Size(2 * r, 2 * r))) }
    clipPath(lit) {
        moonSurface(cx, cy, r)
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
    // the clouds drift on their own slow loop; each layer goes round a whole number of times per loop, so the end meets the start and nothing jumps
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), label = "d")
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
                val sp = 1f + (depth * 2f).roundToInt()        // 1, 2 or 3 crossings per loop (far clouds slowest)
                val x = (((drift * sp) + i * .29f) % 1f) * (w + h * 1.4f) - h * .7f
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
            val y = ((t + seed * 2.3f) % 1f) * h
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
