package com.umutk.pulse.features

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.PChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.T
import com.umutk.pulse.tr
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One finished session: sport key, start (ms), length (ms), distance (m, 0 without a GPS track). */
class ActRecord(val key: String, val start: Long, val dur: Long, val dist: Float)

private const val LOG = "act_log"

fun loadActLog(p: SharedPreferences): List<ActRecord> = runCatching {
    val a = JSONArray(p.getString(LOG, "[]"))
    List(a.length()) { a.getJSONObject(it).let { o -> ActRecord(o.getString("k"), o.getLong("s"), o.getLong("d"), o.optDouble("m", 0.0).toFloat()) } }
}.getOrDefault(emptyList())

/** shortcut: the log keeps the newest 200 sessions, older ones drop off. */
fun addActRecord(p: SharedPreferences, r: ActRecord) {
    val old = loadActLog(p)
    val a = JSONArray()
    (old + r).takeLast(200).forEach { a.put(JSONObject().put("k", it.key).put("s", it.start).put("d", it.dur).put("m", it.dist.toDouble())) }
    p.edit().putString(LOG, a.toString()).apply()
}

fun fmtDur(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60) }
fun fmtDist(m: Float): String = if (m <= 0f) "-" else if (m < 1000f) "%d m".format(m.toInt()) else "%.2f km".format(m / 1000f)

private fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = T.sub, fontSize = 12.sp)
    }
}

/** The Health page: goal rings, step history, streak and averages, activity totals, BMI and heart-rate history. [rev] changes when goals or body numbers did. */
@Composable
fun HealthOverview(p: SharedPreferences, log: List<ActRecord>, steps: Long, bpm: Int, rev: Int, onGoals: () -> Unit) {
    val today = LocalDate.now()
    val d = log.filter { day(it.start) == today }
    val w = log.filter { !day(it.start).isBefore(today.minusDays(6)) }
    val gs = HealthStore.goalSteps(p); val gm = HealthStore.goalMinutes(p); val gk = HealthStore.goalKcal(p)
    val minutes = (d.sumOf { it.dur } / 60_000L).toInt()
    val kcal = HealthStore.kcal(p, steps)
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Today", "Bugün"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Edit, tr("Goals", "Hedefler"), Modifier.size(22.dp).clickable { onGoals() }, tint = T.sub)
        }
        GoalRings(listOf(
            Ring(steps.toFloat() / gs, Look.accent, tr("Steps", "Adım"), "%,d / %,d".format(steps, gs)),
            Ring(minutes.toFloat() / gm, Color(0xFF4CAF50), tr("Active", "Aktif"), "$minutes / $gm ${tr("min", "dk")}"),
            Ring(kcal.toFloat() / gk, Color(0xFFFF7043), tr("Calories", "Kalori"), "$kcal / $gk kcal"),
        ))
        Text("%.2f km".format(HealthStore.km(p, steps)) + " · " + tr("calories are an estimate", "kalori tahmindir"), color = T.sub, fontSize = 12.sp)
    }
    // history: recomputed when today's steps or the goal change
    var span by remember { mutableStateOf(7) }
    val hist = remember(steps, rev, span) { HealthStore.days(p, span) }
    val h30 = remember(steps, rev) { HealthStore.days(p, 30) }
    val best = remember(steps, rev) { HealthStore.best(p) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Steps", "Adımlar"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PChip(span == 7, tr("7 days", "7 gün")) { span = 7 }
            PChip(span == 30, tr("30 days", "30 gün")) { span = 30 }
        }
        StepsChart(hist, gs)
        Row(Modifier.fillMaxWidth()) {
            Stat(tr("Streak", "Seri"), tr("${HealthStore.streak(p)} days", "${HealthStore.streak(p)} gün"), Modifier.weight(1f))
            Stat(tr("Best day", "En iyi gün"), best?.let { "%,d".format(it.second) } ?: "-", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(tr("Average, 7 days", "Ortalama, 7 gün"), "%,d".format(h30.takeLast(7).map { it.second }.average().toLong()), Modifier.weight(1f))
            Stat(tr("Average, 30 days", "Ortalama, 30 gün"), "%,d".format(h30.map { it.second }.average().toLong()), Modifier.weight(1f))
        }
    }
    PCard {
        Text(tr("Activity log", "Aktivite günlüğü"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth()) {
            Stat(tr("Today", "Bugün"), "${fmtDur(d.sumOf { it.dur })} · ${fmtDist(d.sumOf { it.dist.toDouble() }.toFloat())}", Modifier.weight(1f))
            Stat(tr("7 days", "7 gün"), "${fmtDur(w.sumOf { it.dur })} · ${fmtDist(w.sumOf { it.dist.toDouble() }.toFloat())}", Modifier.weight(1f))
        }
        if (log.isEmpty()) Text(tr("No activities yet.", "Henüz aktivite yok."), color = T.sub, fontSize = 13.sp)
        // per type, all recorded sessions
        log.groupBy { it.key }.entries.sortedByDescending { e -> e.value.sumOf { it.dur } }.forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityIcon(k, Look.accent, 22.dp)
                Text("${ActivityType.fromName(k).label} · ${v.size}", color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${fmtDur(v.sumOf { it.dur })} · ${fmtDist(v.sumOf { it.dist.toDouble() }.toFloat())}", color = T.sub, fontSize = 13.sp)
            }
        }
    }
    val bmi = remember(rev) { HealthStore.bmi(p) }
    if (bmi > 0f) PCard {
        Text("BMI", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("%.1f".format(bmi), color = T.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text(bmiLabel(bmi), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        BmiBar(bmi)
        val gw = HealthStore.goalWeight(p); val wt = HealthStore.weight(p)
        if (gw > 0f) Text(tr("Weight %.1f kg, goal %.1f kg (%+.1f kg to go)", "Kilo %.1f kg, hedef %.1f kg (%+.1f kg kaldı)").format(wt, gw, gw - wt), color = T.sub, fontSize = 12.sp)
    }
    val hr = remember(bpm) { HealthStore.hrLog(p).reversed() }
    if (hr.isNotEmpty()) PCard {
        Text(tr("Heart rate", "Nabız"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val f = remember { DateTimeFormatter.ofPattern("d MMM HH:mm") }
        hr.take(8).forEach { (t, b) ->
            Row(Modifier.fillMaxWidth()) {
                Text(Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(f), color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("$b bpm", color = T.text, fontSize = 13.sp)
            }
        }
    }
    PCard {
        Text(tr("Latest activities", "Son aktiviteler"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (log.isEmpty()) Text(tr("No activities yet.", "Henüz aktivite yok."), color = T.sub, fontSize = 13.sp)
        val f = remember { DateTimeFormatter.ofPattern("d MMM HH:mm") }
        log.takeLast(10).reversed().forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityIcon(r.key, Look.accent, 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(ActivityType.fromName(r.key).label, color = T.text, fontSize = 14.sp)
                    Text(Instant.ofEpochMilli(r.start).atZone(ZoneId.systemDefault()).format(f), color = T.sub, fontSize = 11.sp)
                }
                Text("${fmtDur(r.dur)} · ${fmtDist(r.dist)}", color = T.text, fontSize = 13.sp)
            }
        }
    }
}

/** Summary of the session that just ended, with about two seconds of confetti behind it. */
@Composable
fun FinishCard(r: ActRecord) {
    val t = remember(r) { Animatable(0f) }
    LaunchedEffect(r) { t.animateTo(1f, tween(2000, easing = LinearEasing)) }
    val colors = listOf(Look.accent, Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFFE91E63), Color(0xFF03A9F4))
    // each piece: x fraction, fall speed, drift, size, colour
    val rnd = remember(r) { java.util.Random(r.start) }
    val bits = remember(r) { List(60) { floatArrayOf(rnd.nextFloat(), 0.6f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 2f - 1f, 4f + rnd.nextFloat() * 5f, rnd.nextInt(5).toFloat()) } }
    PCard {
        Box {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ActivityIcon(r.key, Look.accent, 36.dp)
                Text(tr("Well done!", "Tebrikler!"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${ActivityType.fromName(r.key).label} · ${fmtDur(r.dur)} · ${fmtDist(r.dist)}", color = T.sub, fontSize = 13.sp)
            }
            if (t.value < 1f) Canvas(Modifier.matchParentSize()) {
                bits.forEach { b ->
                    val p = t.value
                    val x = (b[0] + b[2] * 0.15f * p) * size.width
                    val y = (-0.1f + p * b[1] * 1.3f) * size.height
                    drawRect(colors[b[4].toInt()].copy(alpha = 1f - p * p), Offset(x, y), Size(b[3] * density, b[3] * density * 0.6f))
                }
            }
        }
    }
}

private fun Modifier.matchParentSize(): Modifier = this.fillMaxWidth().height(110.dp)
