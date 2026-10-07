package com.umutk.pulse

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings > About: an animated heartbeat line, who made it, version and device, the GitHub link and the privacy promise. */
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val t = rememberInfiniteTransition(label = "about")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "beat")
    val pts = remember { listOf(0f to .5f, .18f to .5f, .28f to .5f, .36f to .18f, .45f to .88f, .54f to .08f, .62f to .62f, .70f to .5f, 1f to .5f) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp)) {
            val w = size.width; val h = size.height
            val xy = pts.map { Offset(it.first * w, it.second * h) }
            val lens = xy.zipWithNext { a, b -> (b - a).getDistance() }
            val total = lens.sum()
            // the whole line, faint
            for (i in 0 until xy.size - 1) drawLine(Look.accent.copy(alpha = .25f), xy[i], xy[i + 1], 5f, StrokeCap.Round)
            // the bright part trailing the moving dot
            val head = p * total; val tail = (head - total * .3f).coerceAtLeast(0f)
            var acc = 0f
            for (i in 0 until xy.size - 1) {
                val a0 = acc; val a1 = acc + lens[i]; acc = a1
                if (a1 < tail || a0 > head) continue
                fun at(d: Float) = xy[i] + (xy[i + 1] - xy[i]) * ((d - a0) / lens[i].coerceAtLeast(.001f))
                drawLine(Look.accent, at(maxOf(a0, tail)), at(minOf(a1, head)), 6f, StrokeCap.Round)
            }
            acc = 0f
            for (i in 0 until xy.size - 1) {
                if (head in acc..(acc + lens[i])) { val pos = xy[i] + (xy[i + 1] - xy[i]) * ((head - acc) / lens[i].coerceAtLeast(.001f)); drawCircle(Look.accent.copy(alpha = .3f), 16f, pos); drawCircle(Look.accent, 8f, pos) }
                acc += lens[i]
            }
        }
        Text("Pulse", color = T.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("by UmutK", color = Look.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        PCard {
            InfoRows(listOf("Version" to ver, "Package" to ctx.packageName, "Android" to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", "Device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"))
        }
        PButton("GitHub · xUmutKx/Pulse", Modifier.fillMaxWidth()) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xUmutKx/Pulse")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Text("A task manager for Android with the look of Windows Task Manager, plus a full hardware and system browser.", color = T.sub, fontSize = 13.sp)
        PCard {
            Text("Privacy", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("No data is shared. Pulse reads your phone's numbers on the phone and keeps them there. The only network use is the latency test, and only when you press Start.", color = T.text, fontSize = 13.sp)
        }
        Text("Icons in the Windows themes: Microsoft Fluent System Icons (MIT).", color = T.sub, fontSize = 11.sp)
    }
}
