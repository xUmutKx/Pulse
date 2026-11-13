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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign

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
    val col = chargeColor(style)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .94f)).pointerInput(Unit) { detectTapGestures(onDoubleTap = { ChargeAnim.hide() }) }, contentAlignment = Alignment.Center) {
        ChargeBody(style, t, fill.value, col, 1f)
    }
}

/** The colour a look is drawn in (the party looks use their own). */
internal fun chargeColor(style: Int): Color = when (style) { 1 -> Color(0xFF30D158); 2 -> Color(0xFF4FC3F7); 3 -> Color(0xFFFFD60A); 4 -> Color(0xFFB388FF); 7 -> Color(0xFFFF5C7A); 8 -> Color(0xFF7CFC00); 9 -> Color(0xFF40C4FF); 10 -> Color(0xFF30D158)
    11 -> Color(0xFF64FFDA); 12 -> Color(0xFFFFAB40); 13 -> Color(0xFF00E676); 14 -> Color(0xFFFF80AB); 15 -> Color(0xFF82B1FF); 16 -> Color(0xFF18FFFF); 22 -> Color(0xFF69F0AE); 24 -> Color(0xFF00E5FF); 25 -> Color(0xFFFFFFFF); 26 -> Color(0xFFCFD8DC); 27 -> Color(0xFFFFE082); 28 -> Color(0xFF9FA8FF); else -> Color(0xFF69F0AE) }

/** The picture of one look at [t] (0..1, repeating) and [fill] (0..1): drawn the same for the full screen and for the small previews in the settings; [scale] shrinks the texts. */
@Composable
private fun ChargeBody(style: Int, t: Float, fill: Float, col: Color, scale: Float) {
    if (style == 22) { ConsoleCharge(t, fill, col, scale); return }
    if (style == 23) { WordsCharge(t, fill, scale); return }
    if (style == 24) { NeofetchCharge(t, fill, col, scale); return }
    if (style == 25) { ClockCharge(fill, col, scale); return }
    if (style == 27) { PoemCharge(t, fill, scale); return }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                1 -> ringStyle(t, fill, col)
                2 -> liquidStyle(t, fill, col)
                3 -> boltStyle(t, col)
                4 -> rippleStyle(t, col)
                7 -> beatStyle(t, col)
                8 -> risingStyle(t, col)
                9 -> orbitStyle(t, col)
                10 -> batteryStyle(t, fill, col)
                11 -> auroraStyle(t, fill, col)
                12 -> hexStyle(t, fill, col)
                13 -> radarStyle(t, col)
                14 -> burstStyle(t, col)
                15 -> swirlStyle(t, col)
                16 -> edgeStyle(t, fill, col)
                17 -> confettiStyle(t)
                18 -> bubblesStyle(t)
                19 -> rainbowStyle(t, fill)
                20 -> smileStyle(t, fill)
                21 -> heartsStyle(t)
                26 -> birdsStyle(t, fill, col)
                28 -> warpStyle(t, col)
                else -> barStyle(t, fill, col)
            }
        }
        Column(Modifier.padding(top = if (style == 5 || style == 10) (140 * scale).dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fill * 100).toInt()}%", color = Color.White, fontSize = ((if (style == 5 || style == 10) 40 else 56) * scale).sp, fontWeight = FontWeight.Light, maxLines = 1)
            if (scale >= 1f) Text(tr("Charging", "Şarj oluyor"), color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
        }
    }
}

/** The small live picture of a look for the settings page: it plays by itself, the charge filling again and again. */
@Composable
internal fun ChargeMini(style: Int, modifier: Modifier = Modifier) {
    val tr0 = rememberInfiniteTransition(label = "chp")
    val t by tr0.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t")
    val fill by tr0.animateFloat(.1f, .9f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "f")
    Box(modifier.background(Color.Black)) { ChargeBody(style, t, fill, chargeColor(style), .32f) }
}

/** 22: a console: the lines are typed one after the other, the charge is a bar of # signs, the cursor blinks. */
@Composable
private fun ConsoleCharge(t: Float, fill: Float, col: Color, scale: Float) {
    val pct = (fill * 100).toInt(); val bar = 20; val on = (fill * bar).toInt().coerceIn(0, bar)
    val lines = listOf("$ pulse --charger", "> power connected", "> charging $pct%", "[" + "#".repeat(on) + ".".repeat(bar - on) + "]")
    val shown = ((t * 5f).toInt() + 1).coerceAtMost(lines.size)
    Column(Modifier.fillMaxSize().padding((18 * scale).dp), verticalArrangement = Arrangement.Center) {
        lines.take(shown).forEachIndexed { i, l ->
            Text(l + if (i == shown - 1 && (t * 10f).toInt() % 2 == 0) "_" else "", color = col, fontFamily = FontFamily.Monospace, fontSize = (15 * scale + 1).sp, maxLines = 1)
        }
    }
}

/** 23: the charge as a big number and the word "Charging" in party colours, the letters bouncing in turn. */
@Composable
private fun WordsCharge(t: Float, fill: Float, scale: Float) {
    val word = tr("Charging", "Şarj oluyor")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("${(fill * 100).toInt()}%", color = Color.White, fontSize = (64 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Row {
            word.forEachIndexed { i, ch ->
                val bounce = kotlin.math.abs(sin(t * TAU - i * .45f))
                Text(ch.toString(), color = PARTY[i % PARTY.size], fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = (-14 * scale * bounce).dp))
            }
        }
    }
}

/** 24: a system card, like neofetch: a little logo on the left, the phone's lines are typed in on the right, the charge bar ends the list. */
@Composable
private fun NeofetchCharge(t: Float, fill: Float, col: Color, scale: Float) {
    val pct = (fill * 100).toInt(); val bar = 18; val on = (fill * bar).toInt().coerceIn(0, bar)
    val logo = listOf("   .--.   ", "  |o_o |  ", "  |:_/ |  ", " //   \\ \\ ", "(|     | )", "/'\\_   _/`\\", "\\___)=(___/")
    val info = listOf("os" to "Android", "device" to "phone", "status" to tr("charging", "şarj oluyor"), "battery" to "$pct%", "port" to "USB-C")
    val shown = (t * (info.size + 2)).toInt().coerceIn(0, info.size + 1)
    Row(Modifier.fillMaxSize().padding((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxWidth(.42f)) { logo.forEach { Text(it, color = col, fontFamily = FontFamily.Monospace, fontSize = (11 * scale + 1).sp, maxLines = 1) } }
        Column(Modifier.fillMaxWidth()) {
            Text("pulse@phone", color = col, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (12 * scale + 1).sp, maxLines = 1)
            info.take(shown.coerceAtMost(info.size)).forEach { (k, v) -> Text("$k: $v", color = Color.White.copy(alpha = .9f), fontFamily = FontFamily.Monospace, fontSize = (11 * scale + 1).sp, maxLines = 1) }
            if (shown > info.size) Text("[" + "#".repeat(on) + ".".repeat(bar - on) + "]", color = col, fontFamily = FontFamily.Monospace, fontSize = (11 * scale + 1).sp, maxLines = 1)
        }
    }
}

/** 25: the clock of the phone, big and thin, the colon blinking, a thin line under it with the charge. */
@Composable
private fun ClockCharge(fill: Float, col: Color, scale: Float) {
    val cal = java.util.Calendar.getInstance()
    val hh = cal.get(java.util.Calendar.HOUR_OF_DAY); val mm = cal.get(java.util.Calendar.MINUTE)
    val colon = if ((System.currentTimeMillis() / 500L) % 2L == 0L) ":" else " "
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("%02d%s%02d".format(hh, colon, mm), color = Color.White, fontSize = (76 * scale).sp, fontWeight = FontWeight.Thin, maxLines = 1)
        Box(Modifier.padding(top = (12 * scale).dp).fillMaxWidth(.6f).height((3 * scale + 1).dp).background(Color.White.copy(alpha = .15f))) {
            Box(Modifier.fillMaxWidth(fill.coerceIn(.01f, 1f)).fillMaxHeight().background(col))
        }
        if (scale >= 1f) Text("${(fill * 100).toInt()}%  ·  " + tr("charging", "şarj oluyor"), color = Color.White.copy(alpha = .6f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** 27: a short line typed out letter by letter, the cursor blinks, the charge under it. */
@Composable
private fun PoemCharge(t: Float, fill: Float, scale: Float) {
    val full = tr("Slowly, everything fills up.", "Yavaş yavaş, her şey dolar.")
    val n = (t * 1.5f * full.length).toInt().coerceIn(0, full.length)
    Column(Modifier.fillMaxSize().padding((22 * scale).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(full.take(n) + if ((t * 8f).toInt() % 2 == 0) "|" else " ", color = Color.White.copy(alpha = .92f), fontFamily = FontFamily.Serif, fontSize = (22 * scale).sp, textAlign = TextAlign.Center)
        if (scale >= 1f) Text("${(fill * 100).toInt()}%", color = Color.White.copy(alpha = .55f), fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

private val PARTY = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF9100))

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

/** 17: confetti falling and turning, in party colours. */
private fun DrawScope.confettiStyle(t: Float) {
    for (i in 0 until 46) {
        val x = hash(i) * size.width
        val y = ((t * (.5f + hash(i + 100) * .9f) + hash(i + 200)) % 1f) * (size.height + 40f) - 20f
        val w = 6.dp.toPx() + hash(i + 300) * 6.dp.toPx(); val h = w * .5f
        rotate(t * 360f * (1f + hash(i + 400)) + hash(i + 500) * 360f, Offset(x, y)) { drawRect(PARTY[i % PARTY.size].copy(alpha = .9f), Offset(x - w / 2, y - h / 2), Size(w, h)) }
    }
}

/** 18: colourful bubbles rising, each with a thin rim. */
private fun DrawScope.bubblesStyle(t: Float) {
    for (i in 0 until 22) {
        val r = size.minDimension * (.025f + hash(i) * .05f)
        val x = hash(i + 50) * size.width + sin(t * TAU + i) * r
        val y = size.height + r - ((t * (.5f + hash(i + 90)) + hash(i + 130)) % 1f) * (size.height + 2 * r)
        val c = PARTY[i % PARTY.size]
        drawCircle(c.copy(alpha = .25f), r, Offset(x, y)); drawCircle(c, r, Offset(x, y), style = Stroke(2.dp.toPx()))
    }
}

/** 19: a rainbow whose arcs fill up to the charge, a little sun above it. */
private fun DrawScope.rainbowStyle(t: Float, fill: Float) {
    val m = size.minDimension; val c = Offset(size.width / 2f, size.height * .62f); val w = m * .055f
    PARTY.forEachIndexed { i, col ->
        val r = m * (.16f + i * .06f)
        drawArc(col.copy(alpha = .18f), 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w))
        drawArc(col, 180f, 180f * fill.coerceIn(0f, 1f), false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(w))
    }
    drawCircle(Color(0xFFFFEB3B), m * .045f, Offset(c.x, c.y - m * (.18f + .02f * sin(t * TAU))))
}

/** 20: a smiling face that bounces, blinks and smiles wider as the charge grows. */
private fun DrawScope.smileStyle(t: Float, fill: Float) {
    val m = size.minDimension
    val c = Offset(center.x, center.y - kotlin.math.abs(sin(t * TAU)) * m * .04f); val r = m * .24f
    val ink = Color(0xFF4E342E)
    drawCircle(Color(0xFFFFD740), r, c); drawCircle(Color(0xFFFFB300), r, c, style = Stroke(m * .012f))
    val blink = if ((t * 6f).toInt() % 6 == 0) .15f else 1f
    drawOval(ink, Offset(c.x - r * .38f - r * .07f, c.y - r * .25f - r * .12f * blink), Size(r * .14f, r * .24f * blink))
    drawOval(ink, Offset(c.x + r * .38f - r * .07f, c.y - r * .25f - r * .12f * blink), Size(r * .14f, r * .24f * blink))
    val mouth = Path().apply { moveTo(c.x - r * .5f, c.y + r * .2f); quadraticBezierTo(c.x, c.y + r * (.2f + .55f * fill.coerceIn(.2f, 1f)), c.x + r * .5f, c.y + r * .2f) }
    drawPath(mouth, ink, style = Stroke(r * .09f, cap = StrokeCap.Round))
    drawCircle(Color(0x55FF5252), r * .12f, Offset(c.x - r * .62f, c.y + r * .15f)); drawCircle(Color(0x55FF5252), r * .12f, Offset(c.x + r * .62f, c.y + r * .15f))
}

/** 21: hearts floating up. */
private fun DrawScope.heartsStyle(t: Float) {
    val cols = listOf(Color(0xFFFF5C7A), Color(0xFFFF80AB), Color(0xFFFF8A80))
    for (i in 0 until 14) {
        val s = size.minDimension * (.03f + hash(i) * .035f)
        val x = hash(i + 20) * size.width + sin(t * TAU + i) * s
        val y = size.height + s - ((t * (.4f + hash(i + 60) * .6f) + hash(i + 80)) % 1f) * (size.height + 2 * s)
        val p = Path().apply {
            moveTo(x, y + s * .9f)
            cubicTo(x - s * 1.6f, y - s * .2f, x - s * .9f, y - s * 1.3f, x, y - s * .4f)
            cubicTo(x + s * .9f, y - s * 1.3f, x + s * 1.6f, y - s * .2f, x, y + s * .9f)
            close()
        }
        drawPath(p, cols[i % 3].copy(alpha = .9f))
    }
}

/** 26: a flock of small birds crossing the screen, their wings beating; the charge is a thin line at the bottom. */
private fun DrawScope.birdsStyle(t: Float, fill: Float, col: Color) {
    val w = size.width; val h = size.height; val m = size.minDimension
    for (i in 0 until 9) {
        val p = (t + hash(i)) % 1f
        val x = -.1f * w + p * 1.2f * w
        val y = h * (.18f + .45f * hash(i + 40)) + sin(t * TAU + i) * m * .02f
        val sz = m * (.025f + .02f * hash(i + 80))
        val flap = sin(t * TAU * 3f + i) * sz * .35f
        val path = Path().apply {
            moveTo(x - sz, y - sz * .3f + flap)
            quadraticBezierTo(x - sz * .4f, y - sz * .1f, x, y)
            quadraticBezierTo(x + sz * .4f, y - sz * .1f, x + sz, y - sz * .3f + flap)
        }
        drawPath(path, col.copy(alpha = .85f), style = Stroke(m * .006f))
    }
    drawRect(Color.White.copy(alpha = .12f), Offset(w * .2f, h * .86f), Size(w * .6f, m * .01f))
    drawRect(col, Offset(w * .2f, h * .86f), Size(w * .6f * fill.coerceIn(0f, 1f), m * .01f))
}

/** 28: stars rushing out from the middle, like a warp jump; they stretch as they come nearer. */
private fun DrawScope.warpStyle(t: Float, col: Color) {
    val c = center; val m = size.minDimension
    for (i in 0 until 60) {
        val p = (t + hash(i)) % 1f
        val ang = hash(i + 200) * TAU
        val r1 = p * p * m * .55f
        val r0 = r1 * .86f
        val a = Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0)
        val b = Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1)
        drawLine(if (i % 3 == 0) col.copy(alpha = p) else Color.White.copy(alpha = p), a, b, strokeWidth = m * .004f + m * .006f * p)
    }
}

