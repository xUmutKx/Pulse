package com.umutk.pulse.extras.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.umutk.pulse.ExtraLookRow
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

private enum class Rec { IDLE, RUN, PAUSED }

/** Recording accents the user can pick for the record button and the waveform. */
private val ACCENTS = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00), Color(0xFF8E24AA))
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val AUTO_STOPS = listOf(0, 5, 10, 30, 60)
private val SLEEPS = listOf(0, 15, 30, 60)
private val SORTS = listOf("newest", "oldest", "longest", "name")

/** "m:ss" under an hour, "h:mm:ss" above. */
fun clockText(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s / 60) % 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun sizeText(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%d KB".format(b / 1024)
    else -> "$b B"
}

private fun dayText(sec: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(sec * 1000))

private fun sortedTakes(l: List<Recording>, k: String): List<Recording> = when (k) {
    "oldest" -> l.sortedBy { it.added }
    "longest" -> l.sortedByDescending { it.millis }
    "name" -> l.sortedBy { it.name.lowercase() }
    else -> l.sortedByDescending { it.added }
}

private fun sortLabel(k: String): String = when (k) {
    "oldest" -> tr("Oldest first", "En eski")
    "longest" -> tr("Longest first", "En uzun")
    "name" -> tr("Name", "Ad")
    else -> tr("Newest first", "En yeni")
}

/** Voice recorder: record, pause, stop; playback with seek, speed and skip; the list of takes with search, sort and actions. */
@Composable
fun VoiceHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val engine = remember { VoiceEngine(ctx) }
    val player = remember { VoicePlayer(ctx) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var rec by remember { mutableStateOf(Rec.IDLE) }
    var banked by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    var count by remember { mutableIntStateOf(0) }
    val levels = remember { mutableStateListOf<Int>() }
    var list by remember { mutableStateOf(emptyList<Recording>()) }
    var playing by remember { mutableStateOf<Uri?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var sleepAt by remember { mutableLongStateOf(0L) }
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    var message by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(VoicePrefs.sort(ctx)) }
    var quality by remember { mutableIntStateOf(VoicePrefs.quality(ctx)) }
    var autoStop by remember { mutableIntStateOf(VoicePrefs.autoStopMin(ctx)) }
    var countdown by remember { mutableStateOf(VoicePrefs.countdown(ctx)) }
    var accent by remember { mutableIntStateOf(VoicePrefs.accent(ctx)) }
    var favs by remember { mutableStateOf(VoicePrefs.favs(ctx)) }
    var dlg by remember { mutableStateOf("") }
    var autoDone by rememberSaveable { mutableStateOf(false) }
    val acc = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)]

    fun elapsed(): Long = banked + if (rec == Rec.RUN) SystemClock.elapsedRealtime() - startedAt else 0L
    fun reload() { list = VoiceStore.list(ctx) }
    fun visible(): List<Recording> = sortedTakes(list, sort).filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    fun stopPlayer() {
        // remembers where the take was left, unless it ran to the end
        playing?.let { u ->
            val end = player.duration()
            val at = player.position()
            VoicePrefs.savePos(ctx, u.toString(), if (end > 0 && at >= end - 1000) 0L else at)
        }
        player.stop(); playing = null; isPlaying = false
    }
    fun playTake(r: Recording) {
        if (rec != Rec.IDLE) return
        if (playing == r.uri) { player.toggle(); isPlaying = player.isPlaying(); return }
        stopPlayer()
        if (player.play(r.uri, speed, loop)) {
            playing = r.uri; isPlaying = true; dur = player.duration()
            val at = VoicePrefs.pos(ctx, r.uri.toString())
            if (at > 0) player.seek(at)
            pos = at
        }
        else message = tr("This recording cannot be played.", "Bu kayıt oynatılamıyor.")
    }
    fun playNeighbour(step: Int) {
        val l = visible()
        val i = l.indexOfFirst { it.uri == playing }
        val n = l.getOrNull(i + step)
        if (n == null) stopPlayer() else playTake(n)
    }

    fun begin() {
        stopPlayer()
        if (engine.start()) { banked = 0L; startedAt = SystemClock.elapsedRealtime(); rec = Rec.RUN; message = ""; levels.clear() }
        else message = tr("The microphone is busy or unavailable.", "Mikrofon meşgul ya da kullanılamıyor.")
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) begin() else message = tr("Recording needs the microphone.", "Kayıt için mikrofon gerekir.")
    }
    fun startRecord() {
        if (rec != Rec.IDLE) return
        if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO)
        else if (countdown) count = 3
        else begin()
    }
    fun finishRecording() {
        banked = elapsed(); rec = Rec.IDLE
        if (engine.stop() == null) message = tr("Recording was too short.", "Kayıt çok kısaydı.")
        reload()
    }
    fun onMain() {
        if (count > 0) { count = 0; return }
        if (rec == Rec.IDLE) startRecord() else finishRecording()
    }
    fun onPause() {
        if (rec == Rec.RUN) { banked = elapsed(); startedAt = 0L; engine.pause(); rec = Rec.PAUSED }
        else if (rec == Rec.PAUSED) { startedAt = SystemClock.elapsedRealtime(); engine.resume(); rec = Rec.RUN }
    }

    fun share(r: Recording) { runCatching { ctx.startActivity(VoiceStore.shareIntent(r).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun toggleFav(r: Recording) { VoicePrefs.toggleFav(ctx, r.uri.toString()); favs = VoicePrefs.favs(ctx) }
    fun duplicate(r: Recording) {
        message = if (VoiceStore.duplicate(ctx, r)) tr("Copy saved.", "Kopya kaydedildi.") else tr("Could not copy.", "Kopyalanamadı.")
        reload()
    }
    fun export(r: Recording) {
        message = if (VoiceStore.exportToDownloads(ctx, r)) tr("Saved to Download/Pulse.", "Download/Pulse klasörüne kaydedildi.") else tr("Could not save the copy.", "Kopya kaydedilemedi.")
    }

    fun leave() { if (rec != Rec.IDLE) finishRecording(); stopPlayer(); onBack() }
    BackHandler { leave() }

    LaunchedEffect(Unit) { reload() }
    // widget tap: start at once, once per opened page (saved so a restored page does not record again)
    LaunchedEffect(Unit) {
        if (!autoDone && activity?.intent?.getBooleanExtra("autostart", false) == true) { autoDone = true; startRecord() }
    }
    // the countdown before a take; tapping the button again cancels it
    LaunchedEffect(count) { if (count > 0) { delay(1000); count -= 1; if (count == 0) begin() } }
    // redraws the clock and the waveform while a take runs, and stops it at the auto-stop limit
    LaunchedEffect(rec) {
        while (rec == Rec.RUN) {
            delay(120)
            tick = SystemClock.elapsedRealtime()
            levels.add(engine.level())
            while (levels.size > 48) levels.removeAt(0)
            if (autoStop > 0 && elapsed() >= autoStop * 60_000L) {
                finishRecording()
                message = tr("Stopped by the timer.", "Zamanlayıcı kaydı durdurdu.")
            }
        }
    }
    // the playing take's position and state, refreshed while something plays
    LaunchedEffect(playing) {
        while (playing != null) {
            pos = player.position(); isPlaying = player.isPlaying()
            // the sleep timer stops playback, and the position is kept
            if (sleepAt > 0 && SystemClock.elapsedRealtime() >= sleepAt) { sleepAt = 0L; sleepMin = 0; stopPlayer() }
            delay(200)
        }
    }
    // a take in progress is saved, never thrown away, and never recorded while Pulse is out of sight
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && rec != Rec.IDLE) finishRecording() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // the screen stays on while a take runs
    DisposableEffect(rec != Rec.IDLE) {
        val w = activity?.window
        val on = rec != Rec.IDLE
        if (on) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (on) w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(Unit) {
        player.onEnd = { if (next) playNeighbour(1) else { playing = null; isPlaying = false } }
        onDispose { stopPlayer(); if (rec != Rec.IDLE) finishRecording() }
    }

    // the clock reads the tick so it redraws while recording
    @Suppress("UNUSED_EXPRESSION") tick
    val fg = T.text
    val total = list.sumOf { it.millis }
    val vis = visible()
    val rows = mutableListOf<Any>()
    var lastDay = ""
    vis.forEach { r ->
        if (sort == "newest" || sort == "oldest") { val d = dayText(r.added); if (d != lastDay) { rows += d; lastDay = d } }
        rows += r
    }

    LazyColumn(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PIcon("back", fg, 22.dp, Modifier.clickable { leave() })
                Text(tr("Voice recorder", "Ses kaydedici"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PIcon("settings", fg, 22.dp, Modifier.clickable { dlg = "settings" })
                com.umutk.pulse.ExtraLookIcon(fg)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (count > 0) "$count" else clockText(elapsed()), color = fg, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Text(
                    when {
                        count > 0 -> tr("Get ready", "Hazır olun")
                        rec == Rec.IDLE -> tr("Ready", "Hazır")
                        rec == Rec.RUN -> tr("Recording", "Kaydediliyor")
                        else -> tr("Paused", "Duraklatıldı")
                    },
                    color = T.sub, fontSize = 13.sp,
                )
                if (rec != Rec.IDLE) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        levels.forEach { v ->
                            val f = sqrt((v / 32767f).coerceIn(0f, 1f))
                            Box(Modifier.width(3.dp).height((4f + 36f * f).dp).clip(RoundedCornerShape(2.dp)).background(acc))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (rec != Rec.IDLE) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(T.panel).clickable { onPause() }, contentAlignment = Alignment.Center) {
                            PIcon(if (rec == Rec.RUN) "pause" else "play", fg, 24.dp)
                        }
                    }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(T.panel).clickable { onMain() }, contentAlignment = Alignment.Center) {
                        if (rec == Rec.IDLE) Box(Modifier.size(28.dp).clip(CircleShape).background(acc))
                        else PIcon("stop", acc, 28.dp)
                    }
                }
            }
        }
        if (!granted) item {
            PCard(Modifier.fillMaxWidth()) {
                Text(tr("Pulse records from the microphone only while a take runs. Files go to Music/Recordings.",
                    "Pulse mikrofondan yalnızca kayıt sürerken ses alır. Dosyalar Music/Recordings klasörüne gider."), color = T.sub, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                PButton(tr("Allow microphone", "Mikrofona izin ver"), compact = true) { ask.launch(Manifest.permission.RECORD_AUDIO) }
            }
        }
        if (message.isNotEmpty()) item { Text(message, color = T.sub, fontSize = 13.sp) }

        // the take that is playing: seek, skip, speed, loop, continuous play, and its actions
        val now = playing?.let { u -> list.firstOrNull { it.uri == u } }
        if (now != null) item {
            PCard(Modifier.fillMaxWidth()) {
                Text(now.name, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PSlider(
                    value = pos.toFloat(),
                    onChange = { pos = it.toLong() },
                    range = 0f..maxOf(1f, dur.toFloat()),
                    modifier = Modifier.fillMaxWidth(),
                    onDone = { player.seek(pos) },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(clockText(pos), color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(clockText(dur), color = T.sub, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clickable { val p = (pos - 10_000).coerceAtLeast(0L); pos = p; player.seek(p) }, contentAlignment = Alignment.Center) {
                        PIcon("back", fg, 20.dp)
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(T.panel).clickable { player.toggle(); isPlaying = player.isPlaying() }, contentAlignment = Alignment.Center) {
                        PIcon(if (isPlaying) "pause" else "play", acc, 22.dp)
                    }
                    Box(Modifier.size(40.dp).clickable { val p = (pos + 10_000).coerceAtMost(dur); pos = p; player.seek(p) }, contentAlignment = Alignment.Center) {
                        PIcon("forward", fg, 20.dp)
                    }
                    Box(Modifier.size(40.dp).clickable { val i = (SPEEDS.indexOf(speed) + 1) % SPEEDS.size; speed = SPEEDS[i]; player.setSpeed(speed) }, contentAlignment = Alignment.Center) {
                        Text("${speed}x".replace(".0x", "x"), color = fg, fontSize = 13.sp)
                    }
                    Box(Modifier.size(40.dp).clickable { loop = !loop; player.setLoop(loop) }, contentAlignment = Alignment.Center) {
                        PIcon("refresh", if (loop) acc else fg, 20.dp)
                    }
                    Box(Modifier.size(40.dp).clickable { next = !next }, contentAlignment = Alignment.Center) {
                        PIcon("sort", if (next) acc else fg, 20.dp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val fav = favs.contains(now.uri.toString())
                    Box(Modifier.size(40.dp).clickable { toggleFav(now) }, contentAlignment = Alignment.Center) { PIcon("bookmark", if (fav) acc else fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable { renaming = now }, contentAlignment = Alignment.Center) { PIcon("rename", fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable { share(now) }, contentAlignment = Alignment.Center) { PIcon("share", fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable { duplicate(now) }, contentAlignment = Alignment.Center) { PIcon("copy", fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable { export(now) }, contentAlignment = Alignment.Center) { PIcon("download", fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable { deleting = now }, contentAlignment = Alignment.Center) { PIcon("delete", fg, 20.dp) }
                    Box(Modifier.size(40.dp).clickable {
                        val i = (SLEEPS.indexOf(sleepMin) + 1) % SLEEPS.size
                        sleepMin = SLEEPS[i]
                        sleepAt = if (sleepMin > 0) SystemClock.elapsedRealtime() + sleepMin * 60_000L else 0L
                    }, contentAlignment = Alignment.Center) { PIcon("timer", if (sleepMin > 0) acc else fg, 20.dp) }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PIcon("search", T.sub, 18.dp)
                Spacer(Modifier.width(8.dp))
                PField(query, { query = it }, tr("Search", "Ara"), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).clickable { dlg = "sort" }, contentAlignment = Alignment.Center) { PIcon("sort", fg, 20.dp) }
            }
        }
        item {
            Text(
                tr("${list.size} recordings · ${clockText(total)}", "${list.size} kayıt · ${clockText(total)}"),
                color = T.sub, fontSize = 12.sp,
            )
        }
        items(rows, key = { if (it is Recording) it.uri.toString() else "h_$it" }) { row ->
            when (row) {
                is String -> Text(row, color = T.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                is Recording -> {
                    val on = playing == row.uri
                    val fav = favs.contains(row.uri.toString())
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { playTake(row) }) {
                                Text(row.name, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${clockText(row.millis)}  ·  ${sizeText(row.bytes)}  ·  ${SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(row.added * 1000))}",
                                    color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(Modifier.size(40.dp).clickable { toggleFav(row) }, contentAlignment = Alignment.Center) { PIcon("bookmark", if (fav) acc else fg, 20.dp) }
                            Box(Modifier.size(40.dp).clickable { playTake(row) }, contentAlignment = Alignment.Center) { PIcon(if (on && isPlaying) "pause" else "play", acc, 22.dp) }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
                    }
                }
            }
        }
    }

    renaming?.let { r ->
        RenameDialog(r, onDone = { ok -> renaming = null; if (ok) reload() }) { base -> VoiceStore.rename(ctx, r, base) }
    }
    deleting?.let { r ->
        PDialog(tr("Delete recording?", "Kayıt silinsin mi?"), r.name, tr("Delete", "Sil"), {
            if (playing == r.uri) stopPlayer()
            VoiceStore.delete(ctx, r.uri); deleting = null; reload()
        }, { deleting = null })
    }

    when (dlg) {
        "settings" -> SettingsDialog(
            quality = quality, autoStop = autoStop, countdown = countdown, accent = accent,
            onQuality = { quality = it; VoicePrefs.setQuality(ctx, it) },
            onAutoStop = { autoStop = it; VoicePrefs.setAutoStopMin(ctx, it) },
            onCountdown = { countdown = it; VoicePrefs.setCountdown(ctx, it) },
            onAccent = { accent = it; VoicePrefs.setAccent(ctx, it) },
            onDone = { dlg = "" },
        )
        "sort" -> PickDialog(tr("Sort by", "Sırala"), SORTS.map { it to sortLabel(it) }, sort,
            { sort = it; VoicePrefs.setSort(ctx, it); dlg = "" }, { dlg = "" })
    }
}

/** Settings of the recorder: quality, auto-stop, countdown and the record colour. */
@Composable
private fun SettingsDialog(
    quality: Int, autoStop: Int, countdown: Boolean, accent: Int,
    onQuality: (Int) -> Unit, onAutoStop: (Int) -> Unit, onCountdown: (Boolean) -> Unit, onAccent: (Int) -> Unit, onDone: () -> Unit,
) {
    val w = LocalWindowLook.current
    var sub by remember { mutableStateOf("") }
    when (sub) {
        "quality" -> PickDialog(tr("Quality", "Kalite"),
            listOf(0 to tr("Low (smaller files)", "Düşük (küçük dosya)"), 1 to tr("Standard", "Standart"), 2 to tr("High", "Yüksek")),
            quality, { onQuality(it); sub = "" }, { sub = "" })
        "autostop" -> PickDialog(tr("Stop after", "Şu süre sonra durdur"),
            AUTO_STOPS.map { it to (if (it == 0) tr("Never", "Hiçbir zaman") else tr("$it min", "$it dk")) },
            autoStop, { onAutoStop(it); sub = "" }, { sub = "" })
        "accent" -> PickDialog(tr("Record colour", "Kayıt rengi"),
            listOf(0 to tr("Red", "Kırmızı"), 1 to tr("Blue", "Mavi"), 2 to tr("Green", "Yeşil"), 3 to tr("Orange", "Turuncu"), 4 to tr("Purple", "Mor")),
            accent, { onAccent(it); sub = "" }, { sub = "" })
        else -> WinAlert(
            onDismissRequest = onDone,
            title = { Text(tr("Settings", "Ayarlar"), color = w.text) },
            text = {
                Column {
                    ExtraLookRow(w.text)
                    SettingRow(tr("Quality", "Kalite"),
                        listOf(tr("Low", "Düşük"), tr("Standard", "Standart"), tr("High", "Yüksek"))[quality.coerceIn(0, 2)]) { sub = "quality" }
                    SettingRow(tr("Stop after", "Şu süre sonra durdur"), if (autoStop == 0) tr("Never", "Hiçbir zaman") else tr("$autoStop min", "$autoStop dk")) { sub = "autostop" }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Countdown", "Geri sayım"), color = w.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        PSwitch(countdown, onCountdown)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENTS.forEachIndexed { i, c ->
                            Box(Modifier.size(26.dp).clip(CircleShape).background(c).clickable { onAccent(i) }, contentAlignment = Alignment.Center) {
                                if (i == accent) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            },
            confirmButton = { PButton(tr("Close", "Kapat")) { onDone() } },
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    val w = LocalWindowLook.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = w.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = w.text.copy(alpha = .6f), fontSize = 14.sp)
    }
}

/** A radio list in the look's own alert; one tap picks and closes. */
@Composable
private fun <V> PickDialog(title: String, options: List<Pair<V, String>>, cur: V, onPick: (V) -> Unit, onDismiss: () -> Unit) {
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = w.text) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(v == cur) { onPick(v) }
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = w.text, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat")) { onDismiss() } },
    )
}

/** Name field for one take; the extension stays as it is. */
@Composable
private fun RenameDialog(r: Recording, onDone: (Boolean) -> Unit, apply: (String) -> Boolean) {
    var text by remember(r.uri) { mutableStateOf(r.name.substringBeforeLast('.')) }
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = { onDone(false) },
        title = { Text(tr("Rename", "Yeniden adlandır"), color = w.text) },
        text = { PField(text, { text = it }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
        confirmButton = { PButton(tr("OK", "Tamam")) { onDone(apply(text)) } },
        dismissButton = { PButton(tr("Cancel", "İptal")) { onDone(false) } },
    )
}
