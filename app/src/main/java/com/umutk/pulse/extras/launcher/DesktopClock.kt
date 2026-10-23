package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val time = SimpleDateFormat(if (LauncherPrefs.clock24) (if (LauncherPrefs.clockSeconds) "HH:mm:ss" else "HH:mm") else (if (LauncherPrefs.clockSeconds) "h:mm:ss a" else "h:mm a"), Locale.getDefault()).format(d)
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
        // Windows 10 / 11: the clock widget, a dark rounded card with the time and the date (not bare text on the picture)
        else -> Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE6202020)).padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.Start) {
            Text(time, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Text(date, color = Color(0xFFB8B8B8), fontSize = 13.sp)
        }
    }
}


/** The desktop clock as a widget of its own: a long press opens its edit mode (grip to move, cross to take it off, scale, check to finish); the launcher settings bring it back. */
@Composable
fun MovableDesktopClock(lookId: String) {
    if (!LauncherPrefs.desktopClock) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val d = androidx.compose.ui.platform.LocalDensity.current.density
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // first place: the top right corner, as the old clock sat
    val first = (maxWidth.value - 190f).coerceAtLeast(0f)
    // x, y and a scale (1 = the first size)
    fun pos(): FloatArray = LauncherPrefs.widgetPos[-1]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size >= 2 }?.let { if (it.size == 2) floatArrayOf(it[0], it[1], 1f) else it.toFloatArray() } ?: floatArrayOf(first, 10f, 1f)
    fun put(x: Float, y: Float, k: Float) { LauncherPrefs.widgetPos = LauncherPrefs.widgetPos + (-1 to "$x,$y,$k") }
    val p = pos()
    val editing = WidgetEdit.id == -1 && !LauncherPrefs.lockLayout
    androidx.compose.foundation.layout.Box(Modifier.offset(p[0].dp, p[1].dp)) {
        androidx.compose.foundation.layout.Box(Modifier.graphicsLayer { scaleX = p[2]; scaleY = p[2]; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }.longPressEdit(-1, !LauncherPrefs.lockLayout) { WidgetEdit.id = -1 }) {
            DesktopClock(lookId)
        }
        if (editing) {
            EditDot("gridView", Modifier.align(Alignment.TopStart).pointerInput(Unit) {
                detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = pos(); put((q[0] + drag.x / d).coerceAtLeast(0f), (q[1] + drag.y / d).coerceAtLeast(0f), q[2]) }
            })
            EditDot("close", Modifier.align(Alignment.TopEnd)) { WidgetEdit.id = null; LauncherPrefs.desktopClock = false; LauncherPrefs.save(ctx) }
            EditDot("check", Modifier.align(Alignment.TopCenter)) { WidgetEdit.id = null }
            // the corner grows or shrinks the clock
            EditDot("scale", Modifier.align(Alignment.BottomEnd).pointerInput(Unit) {
                detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = pos(); put(q[0], q[1], (q[2] + (drag.x + drag.y) / d / 240f).coerceIn(.6f, 2.5f)) }
            })
        }
    }
    }
}
