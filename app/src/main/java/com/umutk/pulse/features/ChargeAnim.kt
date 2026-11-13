package com.umutk.pulse.features

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animation that plays over everything for a few seconds when the charger is plugged in. Sixteen looks (the setting of the cover picks one; it is off until its switch is on):
 * 1 a ring that fills with ripples going out, 2 liquid rising in a circle, 3 a lightning bolt with sparks, 4 energy drawn in from the edges, 5 the cells of a
 * battery lighting one after the other. It is drawn in the accessibility layer when that is on (above the bars and the keyboard), else in the overlay window.
 */
object ChargeAnim {
    private var view: ComposeView? = null
    private var owner: StandByOwner? = null
    private var host: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val closer = Runnable { hide() }

    /** [picked] -1: the user's setting (needs the switch to be on); a number: a preview of that look. */
    fun show(ctx: Context, picked: Int = -1): Boolean {
        if (picked == -1 && !StandByPrefs.chargeAnimOn(ctx)) return false
        val pk = if (picked == -1) StandByPrefs.chargeAnim(ctx) else picked
        // 6 is "random": one of all the looks each time
        val style = if (pk == 6) ((1..5) + (7..16)).random() else pk
        if (style !in 1..16) return false
        if (view != null) hide()
        val acc = CoverAccessibilityService.instance
        val app = ctx.applicationContext
        if (acc == null && !com.umutk.pulse.Compat.canOverlay(app)) return false
        val base: Context = acc ?: app
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            // it takes touches, so a double tap can close it
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        val o = StandByOwner().also { it.start() }
        val v = ComposeView(base).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent { ChargeUi(app, style) }
        }
        view = v; owner = o; host = base
        if (runCatching { wm.addView(v, lp) }.isFailure) { view = null; o.stop(); owner = null; host = null; return false }
        main.removeCallbacks(closer); main.postDelayed(closer, 4200L)
        return true
    }

    fun hide() {
        main.removeCallbacks(closer)
        val v = view ?: return
        runCatching { (host?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(v) }
        owner?.stop(); owner = null; view = null; host = null
    }
}

@Composable
private fun ChargeUi(ctx: Context, style: Int) {
    val (level, _) = batteryInfo(ctx)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(350)); delay(3100); appear.animateTo(0f, tween(500)) }
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(level / 100f, tween(1400, easing = FastOutSlowInEasing)) }
    val t by rememberInfiniteTransition(label = "charge").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val col = when (style) { 1 -> Color(0xFF30D158); 2 -> Color(0xFF4FC3F7); 3 -> Color(0xFFFFD60A); 4 -> Color(0xFFB388FF); 7 -> Color(0xFFFF5C7A); 8 -> Color(0xFF7CFC00); 9 -> Color(0xFF40C4FF); 10 -> Color(0xFF30D158)
        11 -> Color(0xFF64FFDA); 12 -> Color(0xFFFFAB40); 13 -> Color(0xFF00E676); 14 -> Color(0xFFFF80AB); 15 -> Color(0xFF82B1FF); 16 -> Color(0xFF18FFFF); else -> Color(0xFF69F0AE) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .94f)).pointerInput(Unit) { detectTapGestures(onDoubleTap = { ChargeAnim.hide() }) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                1 -> ringStyle(t, fill.value, col)
                2 -> liquidStyle(t, fill.value, col)
                3 -> boltStyle(t, col)
                4 -> rippleStyle(t, col)
                7 -> beatStyle(t, col)
                8 -> risingStyle(t, col)
                9 -> orbitStyle(t, col)
                10 -> batteryStyle(t, fill.value, col)
                11 -> auroraStyle(t, fill.value, col)
                12 -> hexStyle(t, fill.value, col)
                13 -> radarStyle(t, col)
                14 -> burstStyle(t, col)
                15 -> swirlStyle(t, col)
                16 -> edgeStyle(t, fill.value, col)
                else -> barStyle(t, fill.value, col)
            }
        }
        Column(Modifier.padding(top = if (style == 5 || style == 10) 140.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fill.value * 100).toInt()}%", color = Color.White, fontSize = if (style == 5 || style == 10) 40.sp else 56.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(tr("Charging", "Şarj oluyor"), color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
        }
    }
}

private const val TAU = (2.0 * PI).toFloat()

/** 1: a thin ring that fills to the charge in a soft gradient, with a glowing point at its end and a calm halo. */
private fun DrawScope.ringStyle(t: Float, fill: Float, col: Color) {
    val m = size.minDimension; val c = center
    val r = m * .27f; val w = m * .028f
    drawCircle(col.copy(alpha = .05f + .04f * sin(t * TAU)), r + m * .06f, c)
    drawCircle(Color.White.copy(alpha = .10f), r, c, style = Stroke(w))
    if (fill > 0f) {
        val sweep = Brush.sweepGradient(listOf(col.copy(alpha = .25f), col, col.copy(alpha = .25f)), c)
        rotate(-90f, c) {
            drawArc(sweep, 0f, 360f * fill, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
        }
        val a = (-90f + 360f * fill) * PI.toFloat() / 180f
        val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        drawCircle(col.copy(alpha = .25f), w * 1.8f, tip)
        drawCircle(Color.White, w * .55f, tip)
    }
}

/** 2: a circle that fills with liquid to the charge, one slow wave over another. */
private fun DrawScope.liquidStyle(t: Float, fill: Float, col: Color) {
    val m = size.minDimension; val c = center
    val r = m * .27f
    val circle = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    clipPath(circle) {
        val level = c.y + r - 2f * r * fill
        for (layer in 0..1) {
            val phase = t * TAU * (if (layer == 0) 1f else -1f) + layer * 2.1f
            val amp = r * (if (layer == 0) .05f else .035f)
            val p = Path().apply {
                moveTo(c.x - r, c.y + r); lineTo(c.x - r, level)
                var x = c.x - r
                while (x <= c.x + r) { lineTo(x, level + sin(((x - c.x) / r) * 2.4f + phase) * amp); x += 4f }
                lineTo(c.x + r, c.y + r); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(col.copy(alpha = if (layer == 0) .9f else .4f), col.copy(alpha = if (layer == 0) .55f else .25f)), startY = level, endY = c.y + r))
        }
    }
    drawCircle(Color.White.copy(alpha = .22f), r, c, style = Stroke(m * .008f))
}

/** 3: a lightning bolt in a gradient that breathes in a soft glow. */
private fun DrawScope.boltStyle(t: Float, col: Color) {
    val w = size.minDimension; val c = center
    val pulse = .5f + .5f * sin(t * TAU)
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .30f + .18f * pulse), Color.Transparent), c, w * (.36f + .04f * pulse)), w * (.36f + .04f * pulse), c)
    val bolt = Path().apply {
        moveTo(c.x + w * .03f, c.y - w * .3f); lineTo(c.x - w * .14f, c.y + w * .03f); lineTo(c.x - w * .01f, c.y + w * .03f)
        lineTo(c.x - w * .06f, c.y + w * .3f); lineTo(c.x + w * .14f, c.y - w * .05f); lineTo(c.x + w * .01f, c.y - w * .05f); close()
    }
    drawPath(bolt, Brush.verticalGradient(listOf(Color.White, col), c.y - w * .3f, c.y + w * .3f))
}

/** 4: rings go out from a glowing centre, one after the other, and fade. */
private fun DrawScope.rippleStyle(t: Float, col: Color) {
    val m = size.minDimension; val c = center
    for (k in 0..3) {
        val p = (t + k / 4f) % 1f
        drawCircle(col.copy(alpha = (1f - p) * (1f - p) * .55f), m * (.07f + .36f * p), c, style = Stroke(m * (.012f - .006f * p)))
    }
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .6f), Color.Transparent), c, m * .09f), m * .09f, c)
    drawCircle(Color.White, m * .018f, c)
}

/** 5: a rounded bar that fills to the charge, with a light that slides over the filled part. */
private fun DrawScope.barStyle(t: Float, fill: Float, col: Color) {
    val w = size.minDimension
    val bw = w * .62f; val bh = w * .1f
    val left = center.x - bw / 2f; val top = center.y - bh / 2f - w * .04f
    val rr = androidx.compose.ui.geometry.CornerRadius(bh / 2f, bh / 2f)
    drawRoundRect(Color.White.copy(alpha = .10f), Offset(left, top), androidx.compose.ui.geometry.Size(bw, bh), rr)
    val fw = (bw * fill).coerceAtLeast(bh)
    drawRoundRect(Brush.horizontalGradient(listOf(col.copy(alpha = .7f), col), left, left + fw), Offset(left, top), androidx.compose.ui.geometry.Size(fw, bh), rr)
    val sx = left + (fw + bh * 3f) * t - bh * 2f
    clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + fw, top + bh, rr)) }) {
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent), sx - bh * 1.5f, sx + bh * 1.5f), Offset(sx - bh * 1.5f, top), androidx.compose.ui.geometry.Size(bh * 3f, bh))
    }
}

private fun hash(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x shr 13)) * 1274126177; return ((x xor (x shr 16)) and 0xFFFF) / 65535f }

/** 7: a heartbeat line that runs across the screen. */
private fun DrawScope.beatStyle(t: Float, col: Color) {
    val w = size.width; val h = size.minDimension; val y0 = center.y
    val p = Path(); var x = 0f
    while (x <= w) {
        val u = ((x / w) - t + 1f) % 1f
        val k = (u * 3f) % 1f
        val dy = when { k < .35f -> 0f; k < .42f -> -(k - .35f) / .07f * .05f; k < .48f -> (-.05f + (k - .42f) / .06f * .34f); k < .54f -> (.29f - (k - .48f) / .06f * .4f); k < .6f -> -.11f + (k - .54f) / .06f * .11f; else -> 0f }
        val fade = u
        val yy = y0 + dy * h * (.4f + .6f * fade)
        if (x == 0f) p.moveTo(x, yy) else p.lineTo(x, yy)
        x += 6f
    }
    drawPath(p, col.copy(alpha = .25f), style = Stroke(h * .03f, cap = StrokeCap.Round))
    drawPath(p, col, style = Stroke(h * .01f, cap = StrokeCap.Round))
}

/** 8: points of light rise from the bottom and fade. */
private fun DrawScope.risingStyle(t: Float, col: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 46) {
        val sp = .6f + hash(i) * .9f
        val u = (t * sp + hash(i + 99)) % 1f
        val x = w * hash(i + 7) + sin((u + hash(i)) * TAU * 2f) * w * .02f
        val y = h * (1.05f - u * 1.1f)
        val r = h * (.004f + .008f * hash(i + 3))
        drawCircle(col.copy(alpha = (1f - u) * .35f), r * 3f, Offset(x, y))
        drawCircle(col.copy(alpha = (1f - u).coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

/** 9: electrons on tilted orbits around a glowing core. */
private fun DrawScope.orbitStyle(t: Float, col: Color) {
    val m = size.minDimension; val c = center
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .7f), Color.Transparent), c, m * .1f), m * .1f, c)
    drawCircle(Color.White, m * .02f, c)
    for (k in 0..2) rotate(60f * k, c) {
        drawOval(Color.White.copy(alpha = .12f), Offset(c.x - m * .3f, c.y - m * .11f), Size(m * .6f, m * .22f), style = Stroke(m * .004f))
        val a = (t * TAU * (1f + k * .15f)) + k * 2f
        drawCircle(col, m * .013f, Offset(c.x + cos(a) * m * .3f, c.y + sin(a) * m * .11f))
        drawCircle(col.copy(alpha = .25f), m * .03f, Offset(c.x + cos(a) * m * .3f, c.y + sin(a) * m * .11f))
    }
}

/** 10: a battery outline that fills with a charge colour and small bubbles rising in it. */
private fun DrawScope.batteryStyle(t: Float, fill: Float, col: Color) {
    val m = size.minDimension; val bw = m * .34f; val bh = m * .56f
    val left = center.x - bw / 2f; val top = center.y - bh / 2f - m * .04f
    val rr = CornerRadius(m * .04f, m * .04f)
    drawRoundRect(Color.White.copy(alpha = .75f), Offset(center.x - bw * .18f, top - m * .035f), Size(bw * .36f, m * .035f), CornerRadius(m * .01f, m * .01f))
    drawRoundRect(Color.White.copy(alpha = .75f), Offset(left, top), Size(bw, bh), rr, style = Stroke(m * .01f))
    val fh = (bh - m * .03f) * fill
    val fy = top + bh - m * .015f - fh
    clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left + m * .015f, top + m * .015f, left + bw - m * .015f, top + bh - m * .015f, CornerRadius(m * .03f, m * .03f))) }) {
        drawRect(Brush.verticalGradient(listOf(col, col.copy(alpha = .55f)), fy, top + bh), Offset(left, fy), Size(bw, fh + m * .02f))
        for (i in 0 until 7) {
            val u = (t * (.7f + hash(i) * .6f) + hash(i + 40)) % 1f
            val by = top + bh - u * fh
            drawCircle(Color.White.copy(alpha = (1f - u) * .5f), m * .006f * (1f + hash(i + 5)), Offset(left + bw * (.15f + .7f * hash(i + 9)), by))
        }
    }
}

/** 11: slow bands of light, like a northern sky, that rise to the charge. */
private fun DrawScope.auroraStyle(t: Float, fill: Float, col: Color) {
    val w = size.width; val h = size.height
    val base = h * (1f - .72f * fill)
    for (layer in 0..2) {
        val p = Path(); p.moveTo(0f, h)
        var x = 0f
        while (x <= w + 8f) { p.lineTo(x, base + layer * h * .04f + sin(x / w * 5f + t * TAU * (1 + layer) * (if (layer % 2 == 0) 1f else -1f)) * h * (.03f + .012f * layer)); x += 8f }
        p.lineTo(w, h); p.close()
        drawPath(p, Brush.verticalGradient(listOf(col.copy(alpha = .5f - layer * .12f), Color.Transparent), base - h * .05f, h))
    }
}

/** 12: a honeycomb whose cells light one after the other up to the charge. */
private fun DrawScope.hexStyle(t: Float, fill: Float, col: Color) {
    val r = size.minDimension * .075f; val cols = 6; val rows = 7
    val gw = r * 1.75f * cols; val gh = r * 1.5f * rows
    val ox = center.x - gw / 2f + r * .9f; val oy = center.y - gh / 2f + r
    val total = cols * rows; val lit = (total * fill).toInt()
    for (row in 0 until rows) for (cx in 0 until cols) {
        val idx = (rows - 1 - row) * cols + cx
        val x = ox + cx * r * 1.75f + if (row % 2 == 1) r * .875f else 0f; val y = oy + row * r * 1.5f
        val on = idx < lit
        val a = if (on) .55f + .35f * sin((t + hash(idx)) * TAU) else .07f
        val hp = Path(); for (k in 0..5) { val an = (k * 60f - 30f) * PI.toFloat() / 180f; val px = x + cos(an) * r * .88f; val py = y + sin(an) * r * .88f; if (k == 0) hp.moveTo(px, py) else hp.lineTo(px, py) }; hp.close()
        drawPath(hp, (if (on) col else Color.White).copy(alpha = a.coerceIn(0f, 1f)))
    }
}

/** 13: a radar sweep with a trail and a few blips. */
private fun DrawScope.radarStyle(t: Float, col: Color) {
    val m = size.minDimension; val c = center; val r = m * .34f
    for (k in 1..3) drawCircle(col.copy(alpha = .18f), r * k / 3f, c, style = Stroke(m * .003f))
    drawLine(col.copy(alpha = .18f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), m * .003f); drawLine(col.copy(alpha = .18f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), m * .003f)
    val ang = t * 360f
    rotate(ang, c) { drawArc(Brush.sweepGradient(listOf(Color.Transparent, col.copy(alpha = .55f)), c), -70f, 70f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2)) }
    for (i in 0 until 5) { val a = hash(i) * 360f; val d = (ang - a + 360f) % 360f; if (d < 200f) { val rr = r * (.3f + .6f * hash(i + 9)); val an = a * PI.toFloat() / 180f; drawCircle(col.copy(alpha = (1f - d / 200f)), m * .012f, Offset(c.x + cos(an) * rr, c.y + sin(an) * rr)) } }
}

/** 14: sparks fly out from the centre in bursts. */
private fun DrawScope.burstStyle(t: Float, col: Color) {
    val m = size.minDimension; val c = center
    for (b in 0..1) {
        val u = (t + b * .5f) % 1f
        for (i in 0 until 24) {
            val a = (i / 24f) * TAU + b
            val d = m * (.04f + .38f * (1f - (1f - u) * (1f - u)))
            val x = c.x + cos(a) * d; val y = c.y + sin(a) * d + u * u * m * .05f
            drawCircle(col.copy(alpha = (1f - u).coerceIn(0f, 1f)), m * .008f * (1.2f - u), Offset(x, y))
            drawLine(col.copy(alpha = (1f - u) * .4f), Offset(c.x + cos(a) * d * .8f, c.y + sin(a) * d * .8f), Offset(x, y), m * .003f)
        }
    }
    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .5f), Color.Transparent), c, m * .07f), m * .07f, c)
}

/** 15: soft rotating arcs in a few colours, like a voice assistant. */
private fun DrawScope.swirlStyle(t: Float, col: Color) {
    val m = size.minDimension; val c = center
    val cs = listOf(col, Color(0xFFFF80AB), Color(0xFF69F0AE))
    for (k in 0..2) rotate(t * 360f * (if (k % 2 == 0) 1f else -1.4f) + k * 50f, c) {
        val r = m * (.2f + k * .045f)
        drawArc(cs[k].copy(alpha = .8f), 0f, 120f + k * 30f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(m * .014f, cap = StrokeCap.Round))
        drawArc(cs[k].copy(alpha = .2f), 0f, 120f + k * 30f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(m * .04f, cap = StrokeCap.Round))
    }
}

/** 16: a light that runs round the edge of the screen and leaves a glow, brighter as it charges. */
private fun DrawScope.edgeStyle(t: Float, fill: Float, col: Color) {
    val w = size.width; val h = size.height; val per = 2f * (w + h)
    val rr = CornerRadius(w * .08f, w * .08f)
    drawRoundRect(col.copy(alpha = .12f + .25f * fill), Offset(w * .012f, w * .012f), Size(w * .976f, h - w * .024f), rr, style = Stroke(w * .012f))
    for (k in 0 until 14) {
        val d = ((t - k * .006f + 1f) % 1f) * per
        val (x, y) = when { d < w -> d to 0f; d < w + h -> w to d - w; d < 2 * w + h -> (w - (d - w - h)) to h; else -> 0f to (h - (d - 2 * w - h)) }
        drawCircle(col.copy(alpha = (1f - k / 14f) * .9f), w * .03f * (1f - k / 20f), Offset(x.coerceIn(w * .012f, w * .988f), y.coerceIn(w * .012f, h - w * .012f)))
    }
}
