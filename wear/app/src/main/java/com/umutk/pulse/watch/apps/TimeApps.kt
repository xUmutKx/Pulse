package com.umutk.pulse.watch.apps

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.buzz
import com.umutk.pulse.watch.ui.clock
import com.umutk.pulse.watch.ui.crown
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A ring round the edge of the screen, filled to [fraction] (a timer's time left, a breath, a beat). */
@Composable
fun EdgeRing(fraction: Float, color: androidx.compose.ui.graphics.Color = W.accent) {
    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        val w = 6.dp.toPx()
        val o = Offset(w / 2, w / 2); val s = Size(size.width - w, size.height - w)
        drawArc(W.card, -90f, 360f, false, o, s, style = Stroke(w))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, o, s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Keeps the screen on while [on] (a running timer, the stopwatch, the flashlight). */
@Composable
fun KeepOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) { view.keepScreenOn = on; onDispose { view.keepScreenOn = false } }
}

// ---- stopwatch -------------------------------------------------------------------------------------------------------------------------------

@Composable
fun StopwatchScreen() {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var before by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }
    val now by produceState(0L, running) { while (running) { value = SystemClock.elapsedRealtime(); delay(31L) } }
    val elapsed = before + if (running) (now - startedAt).coerceAtLeast(0L) else 0L
    KeepOn(running)
    Page {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { T(clock(elapsed, hundredths = true), Modifier.fillMaxWidth(), size = 34.sp, weight = FontWeight.Light, align = TextAlign.Center) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton(if (running) "❚❚" else "▶", color = if (running) W.card else W.accent, textColor = if (running) W.text else W.bg) {
                        tick(ctx)
                        if (running) { before += SystemClock.elapsedRealtime() - startedAt; running = false }
                        else { startedAt = SystemClock.elapsedRealtime(); running = true }
                    }
                    RoundButton(if (running) "Lap" else "↺", textSize = 15.sp) {
                        tick(ctx)
                        if (running) laps.add(0, elapsed) else { before = 0L; laps.clear() }
                    }
                }
            }
            itemsIndexed(laps) { i, t ->
                val split = t - (laps.getOrNull(i + 1) ?: 0L)
                Chip("Lap ${laps.size - i}   ${clock(split, true)}", secondary = clock(t, true)) {}
            }
        }
    }
}

// ---- timer ------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    var setMin by remember { mutableIntStateOf(Prefs.int("timerMin", 5)) }
    var running by remember { mutableStateOf(false) }
    var endAt by remember { mutableLongStateOf(0L) }
    var left by remember { mutableLongStateOf(setMin * 60_000L) }
    val pager = rememberPagerState { 2 }
    LaunchedEffect(running, endAt) {
        while (running) {
            left = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (left == 0L) { running = false; buzz(ctx) }
            delay(200L)
        }
    }
    KeepOn(running)
    fun set(m: Int) { setMin = m.coerceIn(1, 180); Prefs.putInt("timerMin", setMin); if (!running) left = setMin * 60_000L }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) Page {
            // the crown sets the minutes while it is stopped
            Box(Modifier.fillMaxSize().crown { d -> if (!running) { set(setMin + d); if (Prefs.crownTicks) tick(ctx) } }) {
                EdgeRing(if (running || left < setMin * 60_000L) left / (setMin * 60_000f) else 1f)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(clock(left), size = 38.sp, weight = FontWeight.Light)
                    T(if (running) "running" else "$setMin min", color = W.sub, size = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoundButton(if (running) "❚❚" else "▶", color = if (running) W.card else W.accent, textColor = if (running) W.text else W.bg) {
                            tick(ctx)
                            if (running) running = false
                            else { if (left <= 0L) left = setMin * 60_000L; endAt = SystemClock.elapsedRealtime() + left; running = true }
                        }
                        RoundButton("↺") { tick(ctx); running = false; left = setMin * 60_000L }
                    }
                }
            }
        } else Page {
            // the second page (swipe left): ready-made lengths
            ScalingLazyColumn(Modifier.fillMaxSize()) {
                item { Header("Presets") }
                items(listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90)) { m ->
                    Chip(if (m < 60) "$m minutes" else "${m / 60} h ${m % 60} min".replace(" 0 min", ""), icon = "⏲") { set(m); running = false }
                }
            }
        }
    }
}

// ---- world clock ------------------------------------------------------------------------------------------------------------------------------

private val CITIES = listOf(
    "Istanbul" to "Europe/Istanbul", "London" to "Europe/London", "Berlin" to "Europe/Berlin", "Paris" to "Europe/Paris", "Moscow" to "Europe/Moscow",
    "Dubai" to "Asia/Dubai", "Delhi" to "Asia/Kolkata", "Bangkok" to "Asia/Bangkok", "Beijing" to "Asia/Shanghai", "Tokyo" to "Asia/Tokyo",
    "Sydney" to "Australia/Sydney", "New York" to "America/New_York", "Chicago" to "America/Chicago", "Denver" to "America/Denver",
    "Los Angeles" to "America/Los_Angeles", "São Paulo" to "America/Sao_Paulo", "Cairo" to "Africa/Cairo", "Johannesburg" to "Africa/Johannesburg",
)

@Composable
fun WorldClockScreen() {
    var mine by remember { mutableStateOf(Prefs.string("cities", "Istanbul,London,New York,Tokyo").split(',').filter { it.isNotBlank() }) }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(15_000L) } }
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val pager = rememberPagerState { 2 }
    fun save(l: List<String>) { mine = l; Prefs.putString("cities", l.joinToString(",")) }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        Page {
            ScalingLazyColumn(Modifier.fillMaxSize()) {
                if (page == 0) {
                    item { Header("World clock") }
                    items(mine) { city ->
                        val zone = CITIES.firstOrNull { it.first == city }?.second ?: return@items
                        val t = ZonedDateTime.now(ZoneId.of(zone))
                        val diff = (t.offset.totalSeconds - ZonedDateTime.now().offset.totalSeconds) / 3600
                        Chip(city + "   " + t.format(fmt), secondary = (if (diff >= 0) "+" else "") + "$diff h" + if (now < 0) "" else "") {}
                    }
                } else {
                    // the second page (swipe left): every city, a tap adds or removes it
                    item { Header("Cities") }
                    items(CITIES) { (city, _) ->
                        val on = city in mine
                        Chip(city, icon = if (on) "✓" else "+") { save(if (on) mine - city else mine + city) }
                    }
                }
            }
        }
    }
}

// ---- pomodoro ---------------------------------------------------------------------------------------------------------------------------------

@Composable
fun PomodoroScreen() {
    val ctx = LocalContext.current
    var workMin by remember { mutableIntStateOf(Prefs.int("pomoWork", 25)) }
    var resting by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var endAt by remember { mutableLongStateOf(0L) }
    var left by remember { mutableLongStateOf(workMin * 60_000L) }
    var rounds by remember { mutableIntStateOf(0) }
    val total = (if (resting) 5 else workMin) * 60_000L
    LaunchedEffect(running, endAt) {
        while (running) {
            left = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (left == 0L) {
                buzz(ctx)
                // work ends: a rest of 5 minutes starts; a rest ends: the next round of work
                if (!resting) rounds++
                resting = !resting
                val next = (if (resting) 5 else workMin) * 60_000L
                endAt = SystemClock.elapsedRealtime() + next; left = next
            }
            delay(250L)
        }
    }
    KeepOn(running)
    Page {
        Box(Modifier.fillMaxSize().crown { d -> if (!running && !resting) { workMin = (workMin + d).coerceIn(5, 90); Prefs.putInt("pomoWork", workMin); left = workMin * 60_000L; if (Prefs.crownTicks) tick(ctx) } }) {
            EdgeRing(left / total.toFloat(), if (resting) W.green else W.red)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T(if (resting) "Rest" else "Work", color = if (resting) W.green else W.red, size = 14.sp, weight = FontWeight.SemiBold)
                T(clock(left), size = 36.sp, weight = FontWeight.Light)
                T("🍅 × $rounds", color = W.sub, size = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton(if (running) "❚❚" else "▶", color = if (running) W.card else W.accent, textColor = if (running) W.text else W.bg) {
                        tick(ctx)
                        if (running) running = false else { endAt = SystemClock.elapsedRealtime() + left; running = true }
                    }
                    RoundButton("↺") { tick(ctx); running = false; resting = false; rounds = 0; left = workMin * 60_000L }
                }
            }
        }
    }
}

// ---- metronome --------------------------------------------------------------------------------------------------------------------------------

@Composable
fun MetronomeScreen() {
    val ctx = LocalContext.current
    var bpm by remember { mutableIntStateOf(Prefs.int("bpm", 100)) }
    var playing by remember { mutableStateOf(false) }
    var beat by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing, bpm) {
        var n = 0
        while (playing) {
            beat = n % 4
            // the first beat of the bar is stronger
            tick(ctx, strong = beat == 0)
            n++
            delay(60_000L / bpm)
        }
    }
    KeepOn(playing)
    Page {
        Box(Modifier.fillMaxSize().crown { d -> bpm = (bpm + d).coerceIn(30, 240); Prefs.putInt("bpm", bpm); if (Prefs.crownTicks && !playing) tick(ctx) }) {
            EdgeRing(if (playing) (beat + 1) / 4f else 0f)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("$bpm", size = 44.sp, weight = FontWeight.Light)
                T("beats a minute", color = W.sub, size = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton("−") { bpm = (bpm - 5).coerceAtLeast(30); Prefs.putInt("bpm", bpm) }
                    RoundButton(if (playing) "❚❚" else "▶", color = if (playing) W.card else W.accent, textColor = if (playing) W.text else W.bg) { playing = !playing }
                    RoundButton("+") { bpm = (bpm + 5).coerceAtMost(240); Prefs.putInt("bpm", bpm) }
                }
            }
        }
    }
}
