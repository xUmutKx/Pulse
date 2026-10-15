package com.umutk.pulse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Loading / progress bar in the look's own style. [progress] null = indeterminate. Previous (non-authentic) 95 / XP / 7 and the other looks keep the plain bar. */
@Composable
fun PProgress(progress: Float?, modifier: Modifier = Modifier, color: Color = Look.accent.copy(alpha = 1f), h: Dp? = null) {
    val t = T
    if (t.material || !styled(t, Opt.authentic)) {
        if (progress == null) LinearProgressIndicator(modifier.fillMaxWidth(), color = color, trackColor = t.line)
        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth(), color = color, trackColor = t.line)
        return
    }
    ThemedBar(progress, color, modifier, h ?: when (t.id) { "win95", "xp" -> 16.dp; "win7" -> 14.dp; else -> 4.dp }, false)
}

/** Usage bar (CPU, memory, disk ...) in the look's own style; [color] is the caller's level colour. */
@Composable
fun PUsageBar(frac: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 10.dp) {
    val t = T
    if (t.material || !styled(t, Opt.authentic)) { LegacyUsageBar(frac, color, modifier, h); return }
    ThemedBar(frac.coerceIn(0f, 1f), color, modifier, h, true)
}

@Composable
fun BarMeter(frac: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 10.dp) = PUsageBar(frac, color, modifier, h)

private fun styled(t: PTheme, authentic: Boolean) = when (t.id) { "win11", "win10" -> true; "win95", "xp", "win7" -> authentic; else -> false }

@Composable
private fun ThemedBar(target: Float?, color: Color, modifier: Modifier, h: Dp, usage: Boolean) {
    val id = T.id
    val line = T.line
    val ph = if (target == null) rememberInfiniteTransition(label = "pbar").animateFloat(0f, 1f, infiniteRepeatable(tween(if (id == "win11") 1500 else 1800, easing = LinearEasing)), label = "ph") else null
    val sm = if (target != null && (id == "win11" || id == "win10")) animateFloatAsState(target, tween(350), label = "pf") else null
    val high = usage && (target ?: 0f) > .9f
    val m = if (id == "win95") modifier.fillMaxWidth().height(h).background(Color(0xFFC0C0C0)).bevel(false) else modifier.fillMaxWidth().height(h)
    Canvas(m) {
        val f = (sm?.value ?: target ?: 0f).coerceIn(0f, 1f)
        val p = ph?.value ?: 0f
        when (id) {
            "win95" -> blocks(2.dp.toPx(), f, p, target == null, (size.height * .6f).coerceAtLeast(3.dp.toPx()), 1.dp.toPx(), 0f, SolidBlock(if (high) Color(0xFFC00000) else Color(0xFF000080)))
            "xp" -> {
                val r = CornerRadius(3.dp.toPx())
                drawRoundRect(Color.White, Offset.Zero, size, r)
                drawRoundRect(Color(0xFF686868), Offset.Zero, size, r, Stroke(1.dp.toPx()))
                val g = if (high) listOf(Color(0xFFFF9A9A), Color(0xFFD62020), Color(0xFFA01010)) else listOf(Color(0xFF7DE87D), Color(0xFF2BC82B), Color(0xFF22A022))
                blocks(3.dp.toPx(), f, p, target == null, 7.dp.toPx(), 2.dp.toPx(), 1.5.dp.toPx(), GlossBlock(g))
            }
            "win7" -> {
                val r = CornerRadius(3.dp.toPx())
                drawRoundRect(Color(0xFFE6E6E6), Offset.Zero, size, r)
                val pad = 1.5.dp.toPx(); val ih = size.height - 2 * pad; val iw = size.width - 2 * pad
                val (x, w) = if (target == null) { val gw = iw * .35f; (p * (iw + gw) - gw).coerceAtLeast(0f) to (minOf(p * (iw + gw), iw) - (p * (iw + gw) - gw).coerceAtLeast(0f)).coerceAtLeast(0f) } else 0f to iw * f
                if (w > 0f) {
                    val cols = if (high) listOf(Color(0xFFFF8A8A), Color(0xFFD02020)) else listOf(Color(0xFF7BE67B), Color(0xFF1FB51F))
                    clipRect(pad + x, pad, pad + x + w, pad + ih) {
                        drawRoundRect(Brush.verticalGradient(cols, pad, pad + ih), Offset(pad, pad), Size(iw, ih), r)
                        drawRect(Color.White.copy(alpha = .3f), Offset(pad, pad), Size(iw, ih / 2))
                        // light sweep running along the filled part
                        val sw = 36.dp.toPx(); val sx = pad + x + ((p * 1.6f) % 1f) * (w + sw) - sw
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .65f), Color.Transparent), sx, sx + sw), Offset(sx, pad), Size(sw, ih))
                    }
                }
                drawRoundRect(Color(0xFFB2BCC8), Offset.Zero, size, r, Stroke(1.dp.toPx()))
            }
            "win10" -> {
                drawRect(line, Offset.Zero, size)
                if (target == null) {
                    val d = size.height; val gap = d * 1.2f
                    for (i in 0 until 5) { // dots chase each other across, the lead one fastest
                        val q = ((p * 1.3f - i * .06f) % 1.3f).coerceAtLeast(0f).coerceAtMost(1f)
                        drawRect(color, Offset(q * (size.width + gap) - gap, 0f), Size(d, d))
                    }
                } else drawRect(color, Offset.Zero, Size(size.width * f, size.height))
            }
            else -> { // win11: rounded pill
                val r = CornerRadius(size.height / 2)
                drawRoundRect(line, Offset.Zero, size, r)
                if (target == null) {
                    val gw = size.width * .4f; val e = p * p * (3 - 2 * p); val x = e * (size.width + gw) - gw
                    clipRect(0f, 0f, size.width, size.height) { drawRoundRect(color, Offset(x, 0f), Size(gw, size.height), r) }
                } else if (f > 0f) drawRoundRect(color, Offset.Zero, Size((size.width * f).coerceAtLeast(size.height), size.height), r)
            }
        }
    }
}

private class SolidBlock(val c: Color)
private class GlossBlock(val g: List<Color>)

/** Block bar: filled blocks left to right, or a group of three travelling across when [moving]. */
private fun DrawScope.blocks(pad: Float, f: Float, p: Float, moving: Boolean, bw: Float, gap: Float, rad: Float, style: Any) {
    val bh = size.height - 2 * pad; val w = size.width - 2 * pad; val step = bw + gap
    fun one(x: Float) {
        val o = Offset(x, pad)
        if (style is SolidBlock) drawRect(style.c, o, Size(bw, bh))
        else if (style is GlossBlock) {
            drawRoundRect(Brush.verticalGradient(style.g, pad, pad + bh), o, Size(bw, bh), CornerRadius(rad))
            drawRect(Color.White.copy(alpha = .35f), o, Size(bw, bh * .4f))
        }
    }
    if (moving) {
        val gl = step * 3; val x0 = p * (w + gl) - gl
        for (i in 0 until 3) { val x = pad + x0 + i * step; if (x >= pad && x + bw <= pad + w) one(x) }
    } else {
        val end = pad + w * f; var x = pad
        while (x + bw <= end) { one(x); x += step }
    }
}
