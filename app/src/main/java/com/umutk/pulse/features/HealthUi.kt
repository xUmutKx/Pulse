package com.umutk.pulse.features

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/** One goal ring: progress 0..1 (may pass 1), its colour, name and "value / goal" text. */
class Ring(val frac: Float, val color: Color, val label: String, val value: String)

/** Concentric goal rings (outermost first) with a legend beside them. */
@Composable
fun GoalRings(rings: List<Ring>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val track = T.line.copy(alpha = .5f)
        Canvas(Modifier.size(112.dp)) {
            val w = 11.dp.toPx(); val gap = 4.dp.toPx()
            rings.forEachIndexed { i, r ->
                val inset = w / 2 + i * (w + gap)
                val sz = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                if (r.frac > 0f) drawArc(r.color, -90f, 360f * r.frac.coerceAtMost(1f), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rings.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(r.color, CircleShape))
                    Column {
                        Text(r.value, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${r.label} · ${(r.frac * 100).toInt()}%", color = T.sub, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Daily step bars with a dashed goal line; days that reached the goal are solid. */
@Composable
fun StepsChart(days: List<Pair<LocalDate, Long>>, goal: Int) {
    val top = maxOf(goal.toLong(), days.maxOfOrNull { it.second } ?: 0L).toFloat()
    val on = Look.accent; val off = Look.accent.copy(alpha = .4f); val line = T.sub
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / days.size
        val bw = slot * (if (days.size > 10) .62f else .5f)
        days.forEachIndexed { i, (_, v) ->
            val h = (v / top * size.height).coerceAtLeast(if (v > 0) 2.dp.toPx() else 0f)
            drawRoundRect(if (v >= goal) on else off, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 4))
        }
        val y = size.height - goal / top * size.height
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
    if (days.size <= 7) Row(Modifier.fillMaxWidth()) {
        days.forEach { Text(it.first.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).take(3), Modifier.weight(1f), color = T.sub, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    } else Row(Modifier.fillMaxWidth()) {
        Text("${days.first().first.dayOfMonth}/${days.first().first.monthValue}", Modifier.weight(1f), color = T.sub, fontSize = 11.sp)
        Text("${days.last().first.dayOfMonth}/${days.last().first.monthValue}", color = T.sub, fontSize = 11.sp)
    }
}

/** BMI with the four standard bands on a bar and a marker; the label is the WHO adult category. */
@Composable
fun BmiBar(bmi: Float) {
    val bands = listOf(18.5f to Color(0xFF42A5F5), 25f to Color(0xFF66BB6A), 30f to Color(0xFFFFA726), 40f to Color(0xFFEF5350))
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val lo = 15f; val hi = 40f
        var from = lo
        bands.forEach { (to, c) ->
            val a = (from - lo) / (hi - lo) * size.width; val b = (to.coerceAtMost(hi) - lo) / (hi - lo) * size.width
            drawRect(c, Offset(a, 4.dp.toPx()), Size(b - a, 6.dp.toPx())); from = to
        }
        val x = ((bmi.coerceIn(lo, hi) - lo) / (hi - lo)) * size.width
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, size.height / 2)); drawCircle(Color.DarkGray, 7.dp.toPx(), Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
    }
}

fun bmiLabel(b: Float) = when {
    b < 18.5f -> tr("Underweight", "Zayıf"); b < 25f -> tr("Normal", "Normal"); b < 30f -> tr("Overweight", "Fazla kilolu"); else -> tr("Obese", "Obez")
}

@Composable
private fun GoalField(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = onValue, modifier = modifier, singleLine = true, label = { Text(label, fontSize = 12.sp) },
    textStyle = TextStyle(color = T.text, fontSize = 14.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
)

/** The goals editor: daily steps, active minutes, calories and a target weight (empty = none). Saved on Save, [onSaved] redraws the screen. */
@Composable
fun GoalsDialog(p: SharedPreferences, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var steps by remember { mutableStateOf(HealthStore.goalSteps(p).toString()) }
    var min by remember { mutableStateOf(HealthStore.goalMinutes(p).toString()) }
    var kcal by remember { mutableStateOf(HealthStore.goalKcal(p).toString()) }
    var wt by remember { mutableStateOf(HealthStore.goalWeight(p).takeIf { it > 0f }?.toString() ?: "") }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Goals", "Hedefler"), color = T.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalField(tr("Steps a day", "Günlük adım"), steps, Modifier.fillMaxWidth()) { steps = it }
                GoalField(tr("Active minutes a day", "Günlük aktif dakika"), min, Modifier.fillMaxWidth()) { min = it }
                GoalField(tr("Calories a day (kcal)", "Günlük kalori (kcal)"), kcal, Modifier.fillMaxWidth()) { kcal = it }
                GoalField(tr("Target weight (kg, optional)", "Hedef kilo (kg, isteğe bağlı)"), wt, Modifier.fillMaxWidth()) { wt = it }
            }
        },
        confirmButton = {
            PButton(tr("Save", "Kaydet")) {
                fun n(s: String, d: Int) = s.replace(',', '.').toFloatOrNull()?.toInt()?.takeIf { it > 0 } ?: d
                HealthStore.saveGoals(p, n(steps, HealthStore.goalSteps(p)), n(min, HealthStore.goalMinutes(p)), n(kcal, HealthStore.goalKcal(p)),
                    wt.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..300f } ?: 0f)
                onSaved(); onDismiss()
            }
        },
        dismissButton = { PButton(tr("Cancel", "İptal"), onClick = onDismiss) },
    )
}
