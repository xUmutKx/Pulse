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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
 * The animation that plays over everything for a few seconds when the charger is plugged in. Five looks (the setting of the cover picks one, 0 is none):
 * 1 a ring that fills with ripples going out, 2 liquid rising in a circle, 3 a lightning bolt with sparks, 4 energy drawn in from the edges, 5 the cells of a
 * battery lighting one after the other. It is drawn in the accessibility layer when that is on (above the bars and the keyboard), else in the overlay window.
 */
object ChargeAnim {
    private var view: ComposeView? = null
    private var owner: StandByOwner? = null
    private var host: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val closer = Runnable { hide() }

    fun show(ctx: Context, style: Int = StandByPrefs.chargeAnim(ctx)): Boolean {
        if (style !in 1..5) return false
        if (view != null) hide()
        val acc = CoverAccessibilityService.instance
        val app = ctx.applicationContext
        if (acc == null && !com.umutk.pulse.Compat.canOverlay(app)) return false
        val base: Context = acc ?: app
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            // touches go through it: a tap on the screen under it works while it plays
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
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
    val col = when (style) { 1 -> Color(0xFF30D158); 2 -> Color(0xFF4FC3F7); 3 -> Color(0xFFFFD60A); 4 -> Color(0xFFB388FF); else -> Color(0xFF69F0AE) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                1 -> ringStyle(t, fill.value, col)
                2 -> liquidStyle(t, fill.value, col)
                3 -> boltStyle(t, col)
                4 -> particleStyle(t, col)
                else -> cellStyle(t, fill.value, col)
            }
        }
        Column(Modifier.padding(top = if (style == 5) 140.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fill.value * 100).toInt()}%", color = Color.White, fontSize = if (style == 5) 40.sp else 56.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(tr("Charging", "Şarj oluyor"), color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
        }
    }
}

private const val TAU = (2.0 * PI).toFloat()

/** 1: a ring that fills to the charge, with ripples going out from it. */
private fun DrawScope.ringStyle(t: Float, fill: Float, col: Color) {
    val m = size.minDimension
    val r = m * .26f; val c = center; val w = m * .035f
    for (k in 0..2) {
        val p = (t + k / 3f) % 1f
        drawCircle(col.copy(alpha = (1f - p) * .35f), r + p * m * .3f, c, style = Stroke(w * .5f))
    }
    drawCircle(Color.White.copy(alpha = .12f), r, c, style = Stroke(w))
    drawArc(col, -90f, 360f * fill, false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
}

/** 2: liquid rising in a circle to the charge, two waves moving against each other. */
private fun DrawScope.liquidStyle(t: Float, fill: Float, col: Color) {
    val m = size.minDimension
    val r = m * .27f; val c = center
    val circle = Path().apply { addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    drawCircle(Color.White.copy(alpha = .16f), r, c, style = Stroke(m * .012f))
    clipPath(circle) {
        val level = c.y + r - 2f * r * fill
        for (layer in 0..1) {
            val phase = t * TAU * (if (layer == 0) 1f else -1f) + layer * 1.7f
            val p = Path().apply {
                moveTo(c.x - r, c.y + r); lineTo(c.x - r, level)
                var x = c.x - r
                while (x <= c.x + r) { lineTo(x, level + sin(((x - c.x) / r) * 3f + phase) * r * .06f); x += 6f }
                lineTo(c.x + r, c.y + r); close()
            }
            drawPath(p, col.copy(alpha = if (layer == 0) .85f else .45f))
        }
    }
}

/** 3: a lightning bolt that glows, with sparks going round it. */
private fun DrawScope.boltStyle(t: Float, col: Color) {
    val w = size.minDimension; val c = center
    val pulse = .5f + .5f * sin(t * TAU)
    val bolt = Path().apply {
        moveTo(c.x + w * .03f, c.y - w * .3f); lineTo(c.x - w * .14f, c.y + w * .03f); lineTo(c.x - w * .01f, c.y + w * .03f)
        lineTo(c.x - w * .06f, c.y + w * .3f); lineTo(c.x + w * .14f, c.y - w * .05f); lineTo(c.x + w * .01f, c.y - w * .05f); close()
    }
    for (k in 3 downTo 1) drawPath(bolt, col.copy(alpha = .07f * pulse * (4 - k)), style = Stroke(w * .02f * k))
    drawPath(bolt, col)
    for (i in 0 until 10) {
        val q = (t + i * .13f) % 1f
        val a = (i / 10f) * TAU + t * 2f
        val rr = w * (.22f + .1f * q)
        drawCircle(col.copy(alpha = 1f - q), w * .007f, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr))
    }
}

/** 4: points of energy drawn in from the edges to the middle, which glows. */
private fun DrawScope.particleStyle(t: Float, col: Color) {
    val c = center; val m = size.minDimension
    val reach = maxOf(size.width, size.height) * .6f
    for (i in 0 until 48) {
        val a = i * 2.399963f
        val p = (t + (i % 8) / 8f) % 1f
        val dist = reach * (1f - p) + m * .08f
        drawCircle(col.copy(alpha = p * .9f), m * (.004f + .006f * p), Offset(c.x + cos(a) * dist, c.y + sin(a) * dist))
    }
    drawCircle(col.copy(alpha = .15f + .1f * sin(t * TAU)), m * .09f, c)
}

/** 5: a battery of ten cells that light one after the other up to the charge, the next one blinking. */
private fun DrawScope.cellStyle(t: Float, fill: Float, col: Color) {
    val w = size.minDimension
    val bw = w * .62f; val bh = w * .22f
    val left = center.x - bw / 2f; val top = center.y - bh / 2f - w * .06f
    drawRoundRect(Color.White.copy(alpha = .7f), Offset(left, top), Size(bw, bh), CornerRadius(bh * .18f, bh * .18f), style = Stroke(w * .008f))
    drawRoundRect(Color.White.copy(alpha = .7f), Offset(left + bw + w * .008f, center.y - w * .06f - bh * .18f), Size(w * .02f, bh * .36f), CornerRadius(w * .005f, w * .005f))
    val n = 10
    val gap = bw * .02f
    val cw = (bw - gap * (n + 1)) / n
    val lit = fill * n
    for (i in 0 until n) {
        val on = i + 1 <= lit
        val partial = i < lit && !on
        val blink = if (i == lit.toInt()) .4f + .6f * (.5f + .5f * sin(t * TAU * 2f)) else 1f
        val a = if (on) 1f else if (partial) (lit - i) else .08f
        drawRoundRect(col.copy(alpha = (a * blink).coerceIn(0f, 1f)), Offset(left + gap + i * (cw + gap), top + gap), Size(cw, bh - gap * 2), CornerRadius(cw * .2f, cw * .2f))
    }
}
