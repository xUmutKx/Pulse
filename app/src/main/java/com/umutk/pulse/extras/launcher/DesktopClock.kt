package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clock gadget of the desktop, in the look of each Windows: the round Aero gadget of Windows 7, a Luna panel for XP, a bevelled one for 95 and
 * large light digits with the date for 10 and 11.
 */
@Composable
fun DesktopClock(lookId: String, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val d = Date(now)
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d)
    when (lookId) {
        "win7" -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val s = cal.get(Calendar.SECOND); val m = cal.get(Calendar.MINUTE) + s / 60f; val h = (cal.get(Calendar.HOUR) % 12) + m / 60f
            Canvas(modifier.size(104.dp)) {
                val c = center; val r = size.minDimension / 2f - 4f
                drawCircle(Brush.radialGradient(listOf(Color(0xFFEAF3FC), Color(0xFF7FA6D0)), center = c, radius = r), r, c)
                drawCircle(Color(0xFF2B4A6B), r, c, style = Stroke(5f))
                drawCircle(Color.White.copy(alpha = .55f), r - 6f, c, style = Stroke(1.5f))
                for (i in 0 until 12) { val a = i * PI.toFloat() / 6f; drawLine(Color(0xFF1B3550), c + Offset(sin(a), -cos(a)) * (r - 14f), c + Offset(sin(a), -cos(a)) * (r - 6f), if (i % 3 == 0) 4f else 2f) }
                fun hand(frac: Float, len: Float, w: Float, col: Color) { val a = frac * 2f * PI.toFloat(); drawLine(col, c, c + Offset(sin(a), -cos(a)) * len, w, StrokeCap.Round) }
                hand(h / 12f, r * .5f, 7f, Color(0xFF14283C)); hand(m / 60f, r * .75f, 5f, Color(0xFF14283C)); hand(s / 60f, r * .82f, 2f, Color(0xFFC0392B))
                drawCircle(Color(0xFF14283C), 5f, c)
            }
        }
        "xp" -> Column(modifier.clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3A93FF), Color(0xFF0058EE)))).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(date, color = Color(0xFFD6E6FF), fontSize = 11.sp)
        }
        "win95" -> Column(modifier.background(Color(0xFFC0C0C0)).padding(3.dp)) {
            Column(Modifier.background(Color.Black).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = Color(0xFF00FF00), fontSize = 24.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
        else -> Column(modifier.padding(4.dp), horizontalAlignment = Alignment.End) {
            val sh = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 8f))
            Text(time, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, style = sh)
            Text(date, color = Color.White, fontSize = 14.sp, style = sh)
        }
    }
}
