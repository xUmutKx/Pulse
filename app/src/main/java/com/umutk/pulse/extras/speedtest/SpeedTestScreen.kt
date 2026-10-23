package com.umutk.pulse.extras.speedtest

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The dial's top: the smallest of these that holds the speed with some room. */
private fun scaleFor(v: Float): Float = listOf(10f, 25f, 50f, 100f, 250f, 500f, 1000f, 2500f, 5000f).firstOrNull { v * 1.15f <= it } ?: 10000f

/** The speed test: a dial with a needle that swings while the speed is measured, then ping, jitter, download, upload and the provider. */
@Composable
fun SpeedTestScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    val s by SpeedTest.state.collectAsState()
    val last = remember { SpeedResult.last(ctx) }
    val busy = s.phase == "ping" || s.phase == "down" || s.phase == "up"
    // the dial keeps the larger scale it reached during a run, so it does not jump back
    var top by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(25f) }
    if (s.phase == "ping") top = 25f
    if (s.phase == "down" || s.phase == "up") top = maxOf(top, scaleFor(s.value))
    val shown = if (s.phase == "ping") 0f else s.value
    // the numbers on the dial slide to the new scale instead of jumping
    val topAnim by animateFloatAsState(top, tween(450), label = "scale")
    val frac by animateFloatAsState((shown / top).coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "needle")
    val done = s.phase == "done"
    val res = if (done) s else null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Speed test", "Hız testi"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        // the dial
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f; val cy = size.height * .66f
                val r = minOf(size.width * .36f, cy * .8f)
                val stroke = r * .13f
                val start = 150f; val sweep = 240f
                val arcTopLeft = Offset(cx - r, cy - r); val arcSize = Size(r * 2, r * 2)
                drawArc(T.text.copy(alpha = .12f), start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (frac > 0f) drawArc(Brush.sweepGradient(0f to Color(0xFF29B6F6), .35f to Color(0xFF26C6DA), .65f to Color(0xFF66BB6A), 1f to Color(0xFFFFCA28), center = Offset(cx, cy)), start, sweep * frac, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // the ticks
                for (i in 0..10) {
                    val a = Math.toRadians((start + sweep * i / 10f).toDouble())
                    val r0 = r - stroke * 1.15f; val r1 = r - stroke * (if (i % 5 == 0) 1.9f else 1.5f)
                    drawLine(T.text.copy(alpha = .45f), Offset(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0), Offset(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1), 2.5f, StrokeCap.Round)
                }
                // the values on the dial, on the scale it has now
                val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = T.text.copy(alpha = .75f).toArgb(); textSize = r * .1f; textAlign = android.graphics.Paint.Align.CENTER
                }
                for (i in 0..10 step 2) {
                    val a = Math.toRadians((start + sweep * i / 10f).toDouble())
                    val rt = r + stroke * 1.05f
                    val v = topAnim * i / 10f
                    drawContext.canvas.nativeCanvas.drawText(if (v >= 100f || v == v.toInt().toFloat()) "%.0f".format(v) else "%.1f".format(v), cx + cos(a).toFloat() * rt, cy + sin(a).toFloat() * rt + tp.textSize / 3f, tp)
                }
                // the needle
                val na = Math.toRadians((start + sweep * frac).toDouble())
                drawLine(Color(0xFFFF7043), Offset(cx, cy), Offset(cx + cos(na).toFloat() * (r - stroke * 2.3f), cy + sin(na).toFloat() * (r - stroke * 2.3f)), r * .035f, StrokeCap.Round)
                drawCircle(Color(0xFFFF7043), r * .07f, Offset(cx, cy)); drawCircle(T.bg, r * .03f, Offset(cx, cy))
            }
            // the live figure, small, under the hub; no unit written
            val big = when (s.phase) { "ping" -> if (s.value > 0f) "%.0f".format(s.value) else "…"; "idle", "error" -> "—"; else -> fmtSpeed(s.value) }
            Text(big, color = T.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
            Text(
                when (s.phase) { "ping" -> tr("Ping", "Ping"); "down" -> tr("Download", "İndirme"); "up" -> tr("Upload", "Yükleme"); "done" -> tr("Done", "Bitti"); "error" -> tr("No connection", "Bağlantı yok"); else -> "" },
                color = T.sub, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
        // go / stop
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (busy) Color(0xFFC42B1C) else T.accent)
                    .clickable { if (busy) SpeedTest.cancel() else SpeedTest.start(ctx) }.padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PIcon(if (busy) "close" else "play", Color.White, 24.dp)
                Text(if (busy) tr("Stop", "Durdur") else tr("Start", "Başlat"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        // the numbers
        val p = res ?: last?.let { SpeedLive("done", it.down, it.pingMs, it.jitterMs, it.down, it.up, it.isp, it.place, it.ip) }
        val ping = if (busy) s.ping else p?.ping ?: 0f
        val jit = if (busy) s.jitter else p?.jitter ?: 0f
        val dn = if (s.phase == "up") s.down else if (s.phase == "down") s.value else p?.down ?: 0f
        val upv = if (s.phase == "up") s.value else p?.up ?: 0f
        PCard {
            Row(Modifier.fillMaxWidth()) {
                Stat(tr("Ping", "Ping"), if (ping > 0f) "%.0f".format(ping) else "—", "ms", Modifier.weight(1f))
                Stat(tr("Jitter", "Titreşim"), if (jit > 0f) "%.0f".format(jit) else "—", "ms", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(tr("Download", "İndirme"), if (dn > 0f) fmtSpeed(dn) else "—", "", Modifier.weight(1f), Color(0xFF29B6F6))
                Stat(tr("Upload", "Yükleme"), if (upv > 0f) fmtSpeed(upv) else "—", "", Modifier.weight(1f), Color(0xFF66BB6A))
            }
        }
        val isp = s.isp.ifBlank { p?.isp.orEmpty() }; val place = s.place.ifBlank { p?.place.orEmpty() }; val ip = s.ip.ifBlank { p?.ip.orEmpty() }
        if (isp.isNotBlank()) PCard {
            Text(tr("Provider", "Sağlayıcı"), color = T.sub, fontSize = 12.sp)
            Text(isp, color = T.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(listOf(place, ip).filter { it.isNotBlank() }.joinToString(" · "), color = T.sub, fontSize = 13.sp, textAlign = TextAlign.Start)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmtSpeed(v: Float) = if (v >= 100f) "%.0f".format(v) else if (v >= 10f) "%.1f".format(v) else "%.2f".format(v)

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier, tint: Color = Color.Unspecified) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = T.sub, fontSize = 12.sp)
        Text(value, color = if (tint == Color.Unspecified) T.text else tint, fontSize = 26.sp, fontWeight = FontWeight.Light)
        if (unit.isNotEmpty()) Text(unit, color = T.sub, fontSize = 11.sp)
    }
}
