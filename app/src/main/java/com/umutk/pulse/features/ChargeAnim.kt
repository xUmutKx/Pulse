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

    fun show(ctx: Context, picked: Int = StandByPrefs.chargeAnim(ctx)): Boolean {
        // 6 is "random": one of the five each time
        val style = if (picked == 6) (1..5).random() else picked
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
    val col = when (style) { 1 -> Color(0xFF30D158); 2 -> Color(0xFF4FC3F7); 3 -> Color(0xFFFFD60A); 4 -> Color(0xFFB388FF); else -> Color(0xFF69F0AE) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = .94f)).pointerInput(Unit) { detectTapGestures(onDoubleTap = { ChargeAnim.hide() }) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            when (style) {
                1 -> ringStyle(t, fill.value, col)
                2 -> liquidStyle(t, fill.value, col)
                3 -> boltStyle(t, col)
                4 -> rippleStyle(t, col)
                else -> barStyle(t, fill.value, col)
            }
        }
        Column(Modifier.padding(top = if (style == 5) 140.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fill.value * 100).toInt()}%", color = Color.White, fontSize = if (style == 5) 40.sp else 56.sp, fontWeight = FontWeight.Light, maxLines = 1)
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
