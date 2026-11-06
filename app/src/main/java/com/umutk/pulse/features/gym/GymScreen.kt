package com.umutk.pulse.features.gym

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.music.stats.ReceiptItem
import com.umutk.pulse.extras.music.stats.ReceiptSection
import com.umutk.pulse.extras.music.stats.ReceiptSpec
import com.umutk.pulse.extras.music.stats.renderReceipt
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private fun fmtW(w: Float) = if (w == w.toInt().toFloat()) w.toInt().toString() else "%.1f".format(w)
private fun day(t: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
private fun hm(sec: Int) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

/**
 * The gym log of Pulse's Health, after the GymMane project: pick exercises (466, with moving pictures), log the sets, rest timer, history, progress, body numbers,
 * foods and a recap as a receipt. Everything stays on the phone.
 */
@Composable
fun GymScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<GymEx?>(null) }
    BackHandler { if (detail != null) detail = null else onBack() }
    val tabs = listOf(tr("Train", "Antrenman"), tr("Library", "Egzersizler"), tr("History", "Geçmiş"), tr("Progress", "Gelişim"), tr("Body", "Vücut"), tr("Food", "Beslenme"), tr("Recap", "Özet"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { if (detail != null) detail = null else onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Gym", "Spor"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        val d = detail
        if (d != null) { ExerciseDetail(d) { detail = null }; return@Column }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { tabs.forEachIndexed { i, n -> PChip(tab == i, n) { tab = i } } }
        when (tab) {
            0 -> TrainTab { detail = it }
            1 -> LibraryTab { detail = it }
            2 -> HistoryTab()
            3 -> ProgressTab()
            4 -> BodyTab()
            5 -> FoodTab()
            else -> RecapTab()
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---- small parts -----------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun GNum(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, modifier, singleLine = true, label = { Text(label, fontSize = 11.sp) },
        textStyle = TextStyle(color = T.text, fontSize = 14.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun Head(text: String) { Text(text, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }

/** An exercise's picture: its two frames fade into each other, drawn in the text colour. Nothing is drawn for an exercise without art. */
@Composable
private fun ArtView(art: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val frames by produceState<List<androidx.compose.ui.graphics.Path>>(emptyList(), art) { value = withContext(Dispatchers.IO) { GymData.art(ctx, art) } }
    val a by rememberInfiniteTransition(label = "art").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val ink = T.text
    Canvas(Modifier.size(size)) {
        if (frames.isEmpty()) return@Canvas
        scale(this.size.width / 512f, pivot = Offset.Zero) {
            drawPath(frames[0], ink, alpha = if (frames.size > 1) 1f - a else 1f)
            if (frames.size > 1) drawPath(frames[1], ink, alpha = a)
        }
    }
}

private fun addToDraft(ctx: Context, ex: GymEx) {
    val (t, items) = GymStore.draft(ctx)
    GymStore.saveDraft(ctx, if (t > 0) t else System.currentTimeMillis(), items + GItem(ex.id))
}

// ---- exercise detail -------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun ExerciseDetail(ex: GymEx, close: () -> Unit) {
    val ctx = LocalContext.current
    var added by remember { mutableStateOf(false) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(T.text.copy(alpha = .06f)), contentAlignment = Alignment.Center) { ArtView(ex.art, 140.dp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(ex.name, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(GymData.muscleName(ex.primary) + (if (ex.secondary.isNotEmpty()) " · " + ex.secondary.joinToString(", ") { GymData.muscleName(it) } else ""), color = T.sub, fontSize = 13.sp)
                Text(ex.equipment + " · " + ex.difficulty, color = T.sub, fontSize = 12.sp)
                val rec = remember(ex.id) { GymStore.record(ctx, ex.id) }
                if (rec > 0f) Text(tr("Record", "Rekor") + ": ${fmtW(rec)} kg", color = Look.accent, fontSize = 13.sp)
            }
        }
    }
    if (ex.steps.isNotEmpty()) PCard {
        Head(tr("How", "Nasıl yapılır"))
        ex.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
    PButton(if (added) tr("Added to the session", "Antrenmana eklendi") else tr("Add to the session", "Antrenmana ekle")) { if (!added) { addToDraft(ctx, ex); added = true } }
}

// ---- library ---------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun ExerciseList(onPick: (GymEx) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(30) }
    val all = remember { GymData.exercises(ctx) }
    val list = remember(q, muscle) { all.filter { (muscle.isEmpty() || it.primary == muscle || muscle in it.secondary) && (q.isBlank() || it.name.contains(q.trim(), true) || it.equipment.contains(q.trim(), true)) } }
    PField(q, { q = it; limit = 30 }, tr("Search ${all.size} exercises", "${all.size} egzersizde ara"), Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PChip(muscle.isEmpty(), tr("All", "Hepsi")) { muscle = ""; limit = 30 }
        GymData.muscles.forEach { (id, n) -> PChip(muscle == id, n) { muscle = id; limit = 30 } }
    }
    list.take(limit).forEach { ex ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(ex) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(T.text.copy(alpha = .06f)), contentAlignment = Alignment.Center) { if (ex.art.isNotBlank()) ArtView(ex.art, 46.dp) else PIcon("heart", T.sub, 20.dp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(ex.name, color = T.text, fontSize = 15.sp, maxLines = 1)
                Text(GymData.muscleName(ex.primary) + " · " + ex.equipment, color = T.sub, fontSize = 12.sp, maxLines = 1)
            }
            PIcon("chevron", T.sub, 18.dp)
        }
    }
    if (list.size > limit) PButton(tr("More", "Daha fazla") + " (${list.size - limit})", compact = true) { limit += 40 }
    if (list.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 13.sp)
}

@Composable
private fun LibraryTab(open: (GymEx) -> Unit) = ExerciseList(open)

// ---- train -----------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun TrainTab(open: (GymEx) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<GItem>().also { l -> l.addAll(GymStore.draft(ctx).second) } }
    var start by remember { mutableLongStateOf(GymStore.draft(ctx).first) }
    var rev by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var restEnd by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var rest by remember { mutableIntStateOf(GymStore.restSec(ctx)) }
    var naming by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf("") }
    val exercises = remember { GymData.exercises(ctx) }
    fun save() { GymStore.saveDraft(ctx, start, items.toList()); rev++ }
    fun ensureStart() { if (start == 0L) start = System.currentTimeMillis() }
    // the rest timer: counts down, and shakes and beeps when it ends
    LaunchedEffect(restEnd) {
        while (restEnd > 0L) {
            now = System.currentTimeMillis()
            if (now >= restEnd) { restEnd = 0L; ringRest(ctx); break }
            delay(250)
        }
    }
    @Suppress("UNUSED_VARIABLE") val tick = rev
    if (picking) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Pick an exercise", "Egzersiz seç"), color = T.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            PButton(tr("Close", "Kapat"), compact = true) { picking = false }
        }
        ExerciseList { ex -> ensureStart(); items.add(GItem(ex.id)); save(); picking = false }
        return
    }
    if (restEnd > 0L) PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Rest", "Dinlenme") + "  " + hm(((restEnd - now) / 1000L).toInt().coerceAtLeast(0)), color = Look.accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PButton(tr("Skip", "Geç"), compact = true) { restEnd = 0L }
        }
    }
    if (items.isEmpty()) {
        PCard {
            Head(tr("Start a session", "Antrenmana başla"))
            Text(tr("Pick the exercises, or start from a program.", "Egzersizleri seç ya da bir programla başla."), color = T.sub, fontSize = 13.sp)
            PButton(tr("Add an exercise", "Egzersiz ekle")) { picking = true }
        }
        val routines = remember(rev) { GymStore.routines(ctx) }
        if (routines.isNotEmpty()) PCard {
            Head(tr("My routines", "Rutinlerim"))
            routines.forEachIndexed { i, (name, list) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(name + " · ${list.size}", color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PButton(tr("Start", "Başla"), compact = true) { ensureStart(); list.forEach { (id, n) -> items.add(GItem(id, MutableList(n) { GSet() })) }; save() }
                    Text("✕", color = T.sub, fontSize = 18.sp, modifier = Modifier.clickable { GymStore.deleteRoutine(ctx, i); rev++ }.padding(10.dp))
                }
            }
        }
        val days = remember { GymData.programs(ctx) }
        val groups = remember { days.groupBy { it.program } }
        var openProg by remember { mutableStateOf("") }
        PCard {
            Head(tr("Programs", "Programlar"))
            groups.forEach { (id, list) ->
                Row(Modifier.fillMaxWidth().clickable { openProg = if (openProg == id) "" else id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(list.first().programName + " · ${list.size}", color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 18.dp)
                }
                if (openProg == id) list.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.day + (if (d.weekday in 1..7) " · " + listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[d.weekday - 1] else "") + " · ${d.items.size}", color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        PButton(tr("Start", "Başla"), compact = true) {
                            ensureStart()
                            d.items.forEach { (name, n) -> GymData.byName(ctx, name)?.let { ex -> items.add(GItem(ex.id, MutableList(n) { GSet() })) } }
                            save()
                        }
                    }
                }
            }
        }
        return
    }
    val elapsed = if (start > 0) ((now.coerceAtLeast(System.currentTimeMillis()) - start) / 1000L).toInt() else 0
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Session", "Antrenman") + (if (start > 0) "  " + hm(elapsed) else ""), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(tr("Rest", "Dinlenme") + " ", color = T.sub, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(45, 60, 90, 120, 180, 240).forEach { s -> PChip(rest == s, if (s < 60) "${s}s" else "${s / 60}:%02d".format(s % 60)) { rest = s; GymStore.setRestSec(ctx, s) } }
        }
    }
    items.toList().forEachIndexed { ii, it0 ->
        val ex = exercises.firstOrNull { e -> e.id == it0.ex }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(T.text.copy(alpha = .06f)).clickable { ex?.let(open) }, contentAlignment = Alignment.Center) { if (ex != null && ex.art.isNotBlank()) ArtView(ex.art, 40.dp) }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(ex?.name ?: it0.ex, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    val rec = remember(it0.ex) { GymStore.record(ctx, it0.ex) }
                    if (rec > 0f) Text(tr("Record", "Rekor") + ": ${fmtW(rec)} kg", color = T.sub, fontSize = 11.sp)
                }
                Text("✕", color = T.sub, fontSize = 18.sp, modifier = Modifier.clickable { items.removeAt(ii); if (items.isEmpty()) { GymStore.clearDraft(ctx); start = 0L } else save() }.padding(10.dp))
            }
            it0.sets.forEachIndexed { si, s ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${si + 1}", color = T.sub, fontSize = 14.sp, modifier = Modifier.width(20.dp))
                    var wt by remember(s) { mutableStateOf(if (s.w > 0f) fmtW(s.w) else "") }
                    var rp by remember(s) { mutableStateOf(if (s.r > 0) s.r.toString() else "") }
                    GNum(wt, { wt = it; s.w = it.replace(',', '.').toFloatOrNull() ?: 0f; save() }, "kg", Modifier.weight(1f))
                    GNum(rp, { rp = it.filter { c -> c.isDigit() }; s.r = rp.toIntOrNull() ?: 0; save() }, tr("reps", "tekrar"), Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (s.done) Look.accent else T.text.copy(alpha = .1f)).clickable {
                        ensureStart(); s.done = !s.done
                        if (s.done) { restEnd = System.currentTimeMillis() + rest * 1000L; now = System.currentTimeMillis() }
                        save()
                    }, contentAlignment = Alignment.Center) { if (s.done) PIcon("check", com.umutk.pulse.onAccent(Look.accent), 20.dp) }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("+ set", "+ set"), compact = true) { val last = it0.sets.lastOrNull(); it0.sets.add(GSet(last?.w ?: 0f, last?.r ?: 0)); save() }
                if (it0.sets.size > 1) PButton(tr("− set", "− set"), compact = true) { it0.sets.removeAt(it0.sets.lastIndex); save() }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PButton(tr("Add exercise", "Egzersiz ekle"), Modifier.weight(1f)) { picking = true }
        PButton(tr("Finish", "Bitir"), Modifier.weight(1f)) {
            val done = items.flatMap { g -> g.sets.filter { it.done && it.r > 0 }.map { Triple(g.ex, it.w, it.r) } }
            if (done.isEmpty()) { msg = tr("Tick at least one set first.", "Önce en az bir seti işaretle.") }
            else {
                val prs = done.groupBy { it.first }.count { (id, l) -> val old = GymStore.record(ctx, id); old > 0f && l.maxOf { it.second } > old }
                val secs = if (start > 0) ((System.currentTimeMillis() - start) / 1000L).toInt() else 0
                GymStore.addSession(ctx, GSession(start.takeIf { it > 0 } ?: System.currentTimeMillis(), secs, day(System.currentTimeMillis()), done))
                GymStore.clearDraft(ctx); items.clear(); start = 0L; restEnd = 0L
                msg = tr("Saved. ${done.size} sets" + (if (prs > 0) ", $prs new record" + (if (prs > 1) "s" else "") else ""), "Kaydedildi. ${done.size} set" + (if (prs > 0) ", $prs yeni rekor" else ""))
            }
        }
    }
    if (naming) WinAlert(onDismissRequest = { naming = false }, title = { Text(tr("Save as a routine", "Rutin olarak kaydet"), color = T.text) },
        text = { PField(routineName, { routineName = it }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
        confirmButton = { PButton(tr("Save", "Kaydet")) { if (routineName.isNotBlank()) { GymStore.addRoutine(ctx, routineName.trim(), items.map { it.ex to it.sets.size }); routineName = ""; naming = false; msg = tr("Routine saved.", "Rutin kaydedildi.") } } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç")) { naming = false } })
    PButton(tr("Save as a routine", "Rutin olarak kaydet"), compact = true) { naming = true }
    if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 13.sp)
}

private fun ringRest(ctx: Context) {
    runCatching {
        val v = ctx.getSystemService(android.os.Vibrator::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) v?.vibrate(android.os.VibrationEffect.createOneShot(450, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v?.vibrate(450)
    }
    runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 350) }
}

// ---- history ---------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun HistoryTab() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val list = remember(rev) { GymStore.sessions(ctx) }
    var open by remember { mutableLongStateOf(0L) }
    if (list.isEmpty()) { Text(tr("No sessions yet.", "Henüz antrenman yok."), color = T.sub, fontSize = 14.sp); return }
    list.forEach { s ->
        PCard {
            Row(Modifier.fillMaxWidth().clickable { open = if (open == s.t) 0L else s.t }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day(s.t), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${s.sets.size} " + tr("sets", "set") + " · ${s.volume.roundToInt()} kg" + (if (s.durSec > 0) " · " + hm(s.durSec) else ""), color = T.sub, fontSize = 12.sp)
                }
                PIcon("chevron", T.sub, 18.dp)
            }
            if (open == s.t) {
                s.sets.groupBy { it.first }.forEach { (id, l) ->
                    Text(GymData.byId(ctx, id)?.name ?: id, color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(l.joinToString("  ") { (if (it.second > 0f) fmtW(it.second) + "×" else "") + it.third }, color = T.sub, fontSize = 13.sp)
                }
                PButton(tr("Delete", "Sil"), danger = true, compact = true, modifier = Modifier.padding(top = 8.dp)) { GymStore.deleteSession(ctx, s.t); open = 0L; rev++ }
            }
        }
    }
}

// ---- progress --------------------------------------------------------------------------------------------------------------------------------------

/** A line chart of [values] (oldest first) in the accent colour, with the last point marked. */
@Composable
private fun Spark(values: List<Float>, modifier: Modifier = Modifier) {
    val line = Look.accent; val grid = T.text.copy(alpha = .12f)
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).coerceAtLeast(0.001f)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        val pts = values.mapIndexed { i, v -> Offset(i * size.width / (values.size - 1), size.height - 10f - (v - lo) / span * (size.height - 24f)) }
        for (i in 1 until pts.size) drawLine(line, pts[i - 1], pts[i], 5f, StrokeCap.Round)
        pts.forEach { drawCircle(line, 6f, it) }
    }
}

@Composable
private fun ProgressTab() {
    val ctx = LocalContext.current
    val sessions = remember { GymStore.sessions(ctx) }
    if (sessions.isEmpty()) { Text(tr("Log a session and your progress shows here.", "Bir antrenman kaydet, gelişimin burada görünür."), color = T.sub, fontSize = 14.sp); return }
    val week = System.currentTimeMillis() - 7 * 86_400_000L
    PCard {
        Head(tr("In numbers", "Sayılarla"))
        val vol = sessions.sumOf { it.volume.toDouble() }
        Text("${sessions.size} " + tr("sessions", "antrenman") + " · ${sessions.count { it.t >= week }} " + tr("this week", "bu hafta"), color = T.text, fontSize = 15.sp)
        Text("%,d kg ".format(vol.roundToInt()) + tr("lifted in total", "toplam kaldırıldı"), color = T.sub, fontSize = 13.sp)
    }
    // volume of the last eight weeks
    PCard {
        Head(tr("Weekly volume", "Haftalık hacim"))
        val ms = 7 * 86_400_000L
        val weeks = (7 downTo 0).map { w -> val from = System.currentTimeMillis() - (w + 1) * ms; val to = from + ms; sessions.filter { it.t in from until to }.sumOf { it.volume.toDouble() }.toFloat() }
        val top = (weeks.maxOrNull() ?: 1f).coerceAtLeast(1f)
        Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEach { v -> Box(Modifier.weight(1f).height((6 + 80 * v / top).dp).clip(RoundedCornerShape(4.dp)).background(Look.accent.copy(alpha = if (v == top && v > 0) 1f else .55f))) }
        }
    }
    val logged = remember { GymStore.logged(ctx).take(14) }
    var pick by remember { mutableStateOf(logged.firstOrNull().orEmpty()) }
    PCard {
        Head(tr("By exercise", "Egzersize göre"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            logged.forEach { id -> PChip(pick == id, GymData.byId(ctx, id)?.name ?: id) { pick = id } }
        }
        val h = remember(pick) { GymStore.history(ctx, pick) }
        if (h.isEmpty()) Text("—", color = T.sub, fontSize = 14.sp) else {
            val best = h.maxByOrNull { it[3] }!!
            Text(tr("Best estimated 1 rep max", "En iyi tahmini 1 tekrar maksimumu") + ": ${best[3].roundToInt()} kg", color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Text(tr("Heaviest", "En ağır") + ": ${fmtW(h.maxOf { it[1] })} kg", color = T.sub, fontSize = 13.sp)
            Spark(h.map { if (it[3] > 0f) it[3] else it[2] }, Modifier.padding(top = 8.dp))
        }
    }
}

// ---- body ------------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun BodyTab() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val kinds = listOf("weight" to tr("Weight (kg)", "Kilo (kg)"), "fat" to tr("Body fat (%)", "Yağ oranı (%)"), "waist" to tr("Waist (cm)", "Bel (cm)"), "chest" to tr("Chest (cm)", "Göğüs (cm)"),
        "hips" to tr("Hips (cm)", "Kalça (cm)"), "arm" to tr("Arm (cm)", "Kol (cm)"), "thigh" to tr("Thigh (cm)", "Bacak (cm)"))
    var kind by remember { mutableStateOf("weight") }
    var txt by remember { mutableStateOf("") }
    val all = remember(rev) { GymStore.measures(ctx) }
    val mine = all.filter { it.kind == kind }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { kinds.forEach { (k, n) -> PChip(kind == k, n) { kind = k } } }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GNum(txt, { txt = it }, kinds.first { it.first == kind }.second, Modifier.weight(1f))
            PButton(tr("Add", "Ekle"), compact = true) { txt.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }?.let { GymStore.addMeasure(ctx, kind, it); txt = ""; rev++ } }
        }
        if (mine.size >= 2) Spark(mine.map { it.v }, Modifier.padding(top = 10.dp))
        mine.takeLast(1).forEach { Text(tr("Last", "Son") + ": ${fmtW(it.v)} · " + day(it.t), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (mine.size >= 2) { val d = mine.last().v - mine.first().v; Text((if (d >= 0) "+" else "") + "%.1f".format(d) + " " + tr("since the first entry", "ilk kayıttan beri"), color = Look.accent, fontSize = 13.sp) }
    }
    mine.reversed().take(12).forEach { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(day(m.t), color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(fmtW(m.v), color = T.text, fontSize = 15.sp)
            Text("✕", color = T.sub, fontSize = 16.sp, modifier = Modifier.clickable { GymStore.deleteMeasure(ctx, m.t); rev++ }.padding(10.dp))
        }
    }
}

// ---- food ------------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun FoodTab() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<GymFood?>(null) }
    var grams by remember { mutableStateOf("100") }
    val foods = remember { GymData.foods(ctx) }
    val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
    val today = remember(rev) { GymStore.foods(ctx).filter { it.t >= cal.timeInMillis } }
    val goal = remember(rev) { GymStore.kcalGoal(ctx) }
    val kcal = today.sumOf { it.kcal }
    PCard {
        Head(tr("Today", "Bugün"))
        Text("$kcal / $goal kcal", color = T.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(T.text.copy(alpha = .1f))) { Box(Modifier.fillMaxWidth((kcal / goal.toFloat()).coerceIn(0f, 1f)).height(8.dp).background(Look.accent)) }
        Text(tr("Protein", "Protein") + " ${today.sumOf { it.p.toDouble() }.roundToInt()} g · " + tr("Carbs", "Karbonhidrat") + " ${today.sumOf { it.c.toDouble() }.roundToInt()} g · " + tr("Fat", "Yağ") + " ${today.sumOf { it.f.toDouble() }.roundToInt()} g", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1600, 1800, 2000, 2200, 2500, 2800, 3200).forEach { g -> PChip(goal == g, "$g") { GymStore.setKcalGoal(ctx, g); rev++ } }
        }
    }
    today.reversed().forEach { f ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${f.name} · ${f.grams} g", color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${f.kcal}", color = T.sub, fontSize = 14.sp)
            Text("✕", color = T.sub, fontSize = 16.sp, modifier = Modifier.clickable { GymStore.deleteFood(ctx, f.t); rev++ }.padding(10.dp))
        }
    }
    PField(q, { q = it }, tr("Search ${foods.size} foods", "${foods.size} yiyecekte ara"), Modifier.fillMaxWidth())
    val hits = remember(q) { if (q.isBlank()) emptyList() else foods.filter { it.en.contains(q.trim(), true) || it.trName.contains(q.trim(), true) }.take(25) }
    hits.forEach { f ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pick = f; grams = (f.unitG.takeIf { it > 0 } ?: 100).toString() }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(f.name, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${f.kcal.roundToInt()} kcal/100 g", color = T.sub, fontSize = 12.sp)
        }
    }
    pick?.let { f ->
        WinAlert(onDismissRequest = { pick = null }, title = { Text(f.name, color = T.text) },
            text = { Column { GNum(grams, { grams = it.filter { c -> c.isDigit() } }, tr("grams", "gram"), Modifier.fillMaxWidth()); if (f.unitG > 0) Text(tr("One piece is about ${f.unitG} g", "Bir adet yaklaşık ${f.unitG} g"), color = T.sub, fontSize = 12.sp) } },
            confirmButton = { PButton(tr("Add", "Ekle")) { grams.toIntOrNull()?.takeIf { it in 1..5000 }?.let { GymStore.addFood(ctx, f, it); pick = null; rev++ } } },
            dismissButton = { PButton(tr("Cancel", "Vazgeç")) { pick = null } })
    }
}

// ---- recap as a receipt ----------------------------------------------------------------------------------------------------------------------------

@Composable
private fun RecapTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    var saved by remember { mutableStateOf("") }
    val label = when (days) { 7 -> tr("Last 7 days", "Son 7 gün"); 30 -> tr("Last 30 days", "Son 30 gün"); 365 -> tr("Last year", "Son bir yıl"); else -> tr("All time", "Tüm zamanlar") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(7, 30, 365, 100000).forEach { d -> PChip(days == d, when (d) { 7 -> "7d"; 30 -> "30d"; 365 -> "1y"; else -> tr("All", "Hepsi") }) { days = d } } }
    val bmp by produceState<Bitmap?>(null, days) { value = withContext(Dispatchers.Default) { renderReceipt(gymReceipt(ctx, days, label)) } }
    bmp?.let { b ->
        Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        PButton(tr("Save receipt", "Fişi kaydet")) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) { ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
                val n = "pulse-gym-receipt-" + System.currentTimeMillis() / 1000 + ".png"
                saved = runCatching { withContext(Dispatchers.IO) { com.umutk.pulse.docScanSave(ctx, n, "image/png") { it.write(bytes) } }; tr("Saved $n in Download/Pulse", "$n kaydedildi, Download/Pulse içinde") }.getOrElse { tr("Could not save", "Kaydedilemedi") }
            }
        }
        if (saved.isNotEmpty()) Text(saved, color = T.sub, fontSize = 12.sp)
    }
}

private fun gymReceipt(ctx: Context, days: Int, label: String): ReceiptSpec {
    val from = System.currentTimeMillis() - days * 86_400_000L
    val ss = GymStore.sessions(ctx).filter { it.t >= from }
    val sets = ss.flatMap { it.sets }
    val byEx = sets.groupBy { it.first }.entries.sortedByDescending { it.value.size }.take(10)
    val items = byEx.map { (id, l) ->
        val best = l.maxByOrNull { it.second * it.third }
        ReceiptItem(l.size.toString(), GymData.byId(ctx, id)?.name ?: id, best?.let { (if (it.second > 0f) fmtW(it.second) + " kg x " else "") + it.third } ?: "")
    }
    val byMuscle = sets.groupBy { GymData.byId(ctx, it.first)?.primary ?: "" }.filterKeys { it.isNotBlank() }.entries.sortedByDescending { it.value.size }.take(5).map { (m, l) -> GymData.muscleName(m) to "${l.size} " + tr("SETS", "SET") }
    val vol = ss.sumOf { it.volume.toDouble() }.roundToInt()
    val secs = ss.sumOf { it.durSec }
    return ReceiptSpec(
        label = label, seed = vol.toLong() * 13 + sets.size * 7L, qtyHead = tr("SETS  EXERCISE", "SET  EGZERSİZ"), items = items,
        sections = if (byMuscle.isEmpty()) emptyList() else listOf(ReceiptSection(tr("MUSCLES", "KASLAR"), byMuscle)),
        stats = listOf(tr("WORKOUTS", "ANTRENMAN") to "${ss.size}", tr("EXERCISES", "EGZERSİZ") to "${byEx.size}", tr("SETS", "SET") to "${sets.size}", tr("REPS", "TEKRAR") to "${sets.sumOf { it.third }}"),
        totalLabel = tr("TOTAL", "TOPLAM"), totalValue = "%,d KG".format(vol),
        subTotal = if (secs > 0) listOf(tr("TIME", "SÜRE") to hm(secs)) else emptyList(),
        payLabel = tr("PAID WITH", "ÖDEME"), payValue = tr("SWEAT", "TER"), barcode = "GYM-${ss.size}-$vol",
        title = "PULSE", kind = tr("GYM RECEIPT", "SPOR FİŞİ"), empty = tr("NO WORKOUTS YET", "HENÜZ ANTRENMAN YOK"),
    )
}
