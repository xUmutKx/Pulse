package com.umutk.pulse.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.FitText
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** Clock faces of the timer: "digits" (plain, the old look), "ring", "bold" and "tomato". Each uses the look's own colours;
 *  only the tomato keeps its fruit colours. Nothing is painted behind the face, so AMOLED stays pure black. */
@Composable
fun TimerFace(skin: String, text: String, frac: Float, graphic: Dp = 200.dp, sp: Int = 48) {
    val f = frac.coerceIn(0f, 1f)
    when (skin) {
        "ring" -> Box(Modifier.size(graphic), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension * .07f
                val arc = Size(size.minDimension - w, size.minDimension - w)
                val tl = Offset(w / 2, w / 2)
                drawArc(T.line, 0f, 360f, false, tl, arc, style = Stroke(w))
                if (f > 0f) drawArc(Look.accent, -90f, 360f * f, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            FitText(text, T.text, (sp * .8f).sp, Modifier.fillMaxWidth(.6f).wrapContentWidth(Alignment.CenterHorizontally), FontWeight.SemiBold)
        }
        "bold" -> FitText(text, T.text, sp.sp, Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally), FontWeight.Black)
        "tomato" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(graphic)) { drawTomato(f) }
            FitText(text, T.text, (sp * .6f).sp, Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally), FontWeight.SemiBold)
        }
        else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            FitText(text, T.text, sp.sp, Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally), FontWeight.SemiBold)
            BarMeter(f, Look.accent, Modifier.fillMaxWidth())
        }
    }
}

private val TomatoRed = Color(0xFFE53935)
private val LeafGreen = Color(0xFF43A047)

/** A tomato with a leaf; the red fill drains from the top down as the time passes, the eaten part stays a faint outline. */
private fun DrawScope.drawTomato(f: Float) {
    val r = size.minDimension * .36f
    val cx = size.width / 2
    val cy = size.height * .6f
    val body = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    drawPath(body, T.text.copy(alpha = .12f))
    if (f > 0f) clipPath(body) { drawRect(TomatoRed, Offset(0f, cy + r - 2 * r * f), Size(size.width, 2 * r * f)) }
    val ty = cy - r * .85f
    val leaf = Path().apply {
        moveTo(cx - r * .6f, ty)
        quadraticBezierTo(cx, ty - r * .55f, cx + r * .6f, ty)
        quadraticBezierTo(cx, ty + r * .1f, cx - r * .6f, ty)
        close()
    }
    drawPath(leaf, LeafGreen)
    drawLine(LeafGreen, Offset(cx, ty), Offset(cx, ty - r * .45f), strokeWidth = r * .12f, cap = StrokeCap.Round)
}

fun skinName(k: String) = when (k) {
    "ring" -> tr("Ring", "Halka"); "bold" -> tr("Bold", "Kalın"); "tomato" -> tr("Tomato", "Domates"); else -> tr("Digits", "Rakam")
}

/** The four faces as previews in one row; the picked one has the accent border (the theme picker's cell style). */
@Composable
fun SkinRow(sel: String, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("digits", "ring", "bold", "tomato").forEach { k ->
            Column(Modifier.weight(1f).clickable { onPick(k) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                    .border(if (k == sel) BorderStroke(2.dp, Look.accent) else BorderStroke(1.dp, T.sub.copy(alpha = .3f)), shape), contentAlignment = Alignment.Center) {
                    TimerFace(k, "12:30", .6f, graphic = 56.dp, sp = 20)
                }
                Text(skinName(k), color = if (k == sel) T.text else T.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** Pomodoro screen of the timer: the period name, cycle count, the face, the length pickers and Start / Skip / Reset. */
@Composable
fun PomodoroPanel(skin: String, askNotes: () -> Unit) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(PomodoroClock.running(ctx)) }
    var left by remember { mutableLongStateOf(PomodoroClock.remaining(ctx)) }
    var phase by remember { mutableIntStateOf(PomodoroClock.phase(ctx)) }
    var count by remember { mutableIntStateOf(PomodoroClock.count(ctx)) }
    var len by remember { mutableLongStateOf(PomodoroClock.length(ctx)) }
    fun sync() {
        running = PomodoroClock.running(ctx); left = PomodoroClock.remaining(ctx); phase = PomodoroClock.phase(ctx)
        count = PomodoroClock.count(ctx); len = PomodoroClock.length(ctx)
    }
    LaunchedEffect(Unit) {
        PomodoroClock.resume(ctx)
        while (true) { PomodoroClock.check(ctx); sync(); delay(200) }
    }
    val names = listOf(tr("Work", "Çalışma"), tr("Short break", "Kısa mola"), tr("Long break", "Uzun mola"))
    @Composable fun Lengths(kind: Int, label: String, mins: List<Int>) = Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = T.sub, fontSize = 12.sp)
        mins.forEach { m -> PChip(PomodoroClock.length(ctx, kind) == m * 60_000L, tr("$m min", "$m dk")) { PomodoroClock.setLength(ctx, kind, m * 60_000L); sync() } }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(names[phase.coerceIn(0, 2)], color = Look.accent, fontSize = 14.sp)
        Text(tr("$count of 4", "$count / 4"), color = T.sub, fontSize = 13.sp)
        TimerFace(skin, timerText(left), if (len > 0) left.toFloat() / len else 0f, graphic = 200.dp, sp = 48)
        Lengths(0, tr("Work", "Çalışma"), listOf(15, 20, 25, 30, 45, 50))
        Lengths(1, tr("Short", "Kısa"), listOf(3, 5, 10))
        Lengths(2, tr("Long", "Uzun"), listOf(10, 15, 20, 30))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(if (running) tr("Pause", "Duraklat") else if (left in 1 until len) tr("Resume", "Sürdür") else tr("Start", "Başlat"), Modifier.weight(1f)) {
                if (running) PomodoroClock.pause(ctx) else { askNotes(); PomodoroClock.start(ctx) }
                sync()
            }
            PButton(tr("Skip", "Atla"), Modifier.weight(1f)) { PomodoroClock.skip(ctx); sync() }
            PButton(tr("Reset", "Sıfırla"), Modifier.weight(1f)) { PomodoroClock.reset(ctx); sync() }
        }
    }
}
