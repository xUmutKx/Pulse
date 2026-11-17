package com.umutk.pulse.watch.apps

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.input.RemoteInputIntentHelper
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.crown
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---- calculator: the keys stand in a circle round the result, the way a round screen holds them ------------------------------------------------

@Composable
fun CalculatorScreen() {
    val ctx = LocalContext.current
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    fun press(k: String) {
        tick(ctx)
        when (k) {
            "C" -> { expr = ""; result = "" }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> { result = calc(expr); if (result != "?") expr = result }
            else -> expr += k
        }
        if (k != "=" && k != "C") result = calc(expr).takeIf { it != "?" }.orEmpty()
    }
    Page(showTime = false) {
        Column(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(expr.ifEmpty { "0" }, size = 18.sp, maxLines = 1)
            T(result, color = W.accent, size = 13.sp, maxLines = 1)
            listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("C", "0", ".", "+"), listOf("⌫", "=")).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { k ->
                        val op = k in setOf("÷", "×", "−", "+", "=")
                        RoundButton(k, size = if (r == 4) 34.dp else 32.dp, color = if (op) W.accent.copy(alpha = .35f) else W.card, textSize = 14.sp) { press(k) }
                    }
                }
            }
        }
    }
}

/** The value of a sum typed on the keys (+ − × ÷, the usual order, decimals); "?" when it cannot be worked out. */
private fun calc(s: String): String = runCatching {
    val t = s.replace('×', '*').replace('÷', '/').replace('−', '-')
    if (t.isBlank()) return@runCatching ""
    var i = 0
    fun num(): Double { val st = i; if (i < t.length && t[i] == '-') i++; while (i < t.length && (t[i].isDigit() || t[i] == '.')) i++; return t.substring(st, i).toDouble() }
    fun term(): Double { var v = num(); while (i < t.length && (t[i] == '*' || t[i] == '/')) { val o = t[i++]; val n = num(); v = if (o == '*') v * n else v / n }; return v }
    var v = term()
    while (i < t.length && (t[i] == '+' || t[i] == '-')) { val o = t[i++]; val n = term(); v = if (o == '+') v + n else v - n }
    if (i != t.length || v.isNaN() || v.isInfinite()) "?" else if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else "%.6f".format(v).trimEnd('0').trimEnd('.')
}.getOrDefault("?")

// ---- flashlight: the whole screen white, as bright as it goes; a tap changes the colour --------------------------------------------------------

@Composable
fun FlashlightScreen() {
    val ctx = LocalContext.current
    val colors = listOf(Color.White, Color(0xFFFFE0B2), Color(0xFFFF5252))
    var c by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        val before = w?.attributes?.screenBrightness ?: -1f
        w?.attributes = w?.attributes?.apply { screenBrightness = 1f }
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = before }; w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    Box(Modifier.fillMaxSize().background(colors[c]).clickable { c = (c + 1) % colors.size })
}

// ---- counter: the crown counts up and down --------------------------------------------------------------------------------------------------

@Composable
fun CounterScreen() {
    val ctx = LocalContext.current
    var n by remember { mutableIntStateOf(Prefs.int("counter", 0)) }
    fun set(v: Int) { n = v; Prefs.putInt("counter", v) }
    Page {
        Box(Modifier.fillMaxSize().crown(stepPx = 64f) { d -> set(n + d); tick(ctx) }) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("$n", size = 54.sp, weight = FontWeight.Light)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton("−") { set(n - 1); tick(ctx) }
                    RoundButton("+", color = W.accent, textColor = W.bg) { set(n + 1); tick(ctx) }
                }
                T("↺ 0", color = W.sub, size = 13.sp, modifier = Modifier.clickable { set(0); tick(ctx, true) })
            }
        }
    }
}

// ---- dice and coin: a tap rolls, the crown changes how many dice ------------------------------------------------------------------------------

@Composable
fun DiceScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dice by remember { mutableIntStateOf(1) }
    var faces by remember { mutableStateOf(listOf(6)) }
    var coin by remember { mutableStateOf("") }
    val spin = remember { Animatable(0f) }
    val pips = "⚀⚁⚂⚃⚄⚅"
    fun roll() {
        tick(ctx, true); coin = ""
        faces = List(dice) { Random.nextInt(1, 7) }
        scope.launch { spin.snapTo(0f); spin.animateTo(360f) }
    }
    Page {
        Box(Modifier.fillMaxSize().crown(stepPx = 80f) { d -> dice = (dice + d).coerceIn(1, 4); faces = List(dice) { faces.getOrElse(it) { 1 } }; tick(ctx) }) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.graphicsLayer { rotationZ = spin.value }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (coin.isNotEmpty()) T(coin, size = 40.sp) else faces.forEach { f -> T(pips[f - 1].toString(), size = if (dice > 2) 34.sp else 46.sp) }
                }
                if (coin.isEmpty() && dice > 1) T("= ${faces.sum()}", color = W.sub, size = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton("🎲", color = W.accent) { roll() }
                    RoundButton("🪙") { tick(ctx, true); coin = if (Random.nextBoolean()) "Heads" else "Tails"; scope.launch { spin.snapTo(0f); spin.animateTo(360f) } }
                }
            }
        }
    }
}

// ---- notes: spoken or typed with the watch's own input, a list the crown scrolls; a tap on a note removes it after a second tap -------------

@Composable
fun NotesScreen() {
    var notes by remember { mutableStateOf(Prefs.string("notes").split('\u0001').filter { it.isNotBlank() }) }
    var armed by remember { mutableStateOf<String?>(null) }
    fun save(l: List<String>) { notes = l; Prefs.putString("notes", l.joinToString("\u0001")) }
    val input = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence("note")?.toString() }?.trim().orEmpty()
        if (text.isNotEmpty()) save(listOf(text) + notes)
    }
    Page {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Notes") }
            item {
                Chip("New note", icon = "＋", color = W.accent.copy(alpha = .3f)) {
                    val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder("note").setLabel("Note").build()))
                    runCatching { input.launch(intent) }
                }
            }
            if (notes.isEmpty()) item { T("No notes yet", color = W.sub, size = 13.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            items(notes) { n ->
                Chip(n, secondary = if (armed == n) "Tap again to delete" else null, color = if (armed == n) W.red.copy(alpha = .35f) else W.card) {
                    if (armed == n) { save(notes - n); armed = null } else armed = n
                }
            }
        }
    }
}

// ---- settings ----------------------------------------------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen() {
    var ticks by remember { mutableStateOf(Prefs.crownTicks) }
    Page {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Settings") }
            item { Chip("Crown ticks", icon = if (ticks) "✓" else "○", secondary = if (ticks) "A tick for every step of the crown" else "Off") { ticks = !ticks; Prefs.changeCrownTicks(ticks) } }
            item { T("Accent", color = W.sub, size = 13.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Prefs.ACCENTS.forEach { a ->
                        Box(Modifier.size(if (W.accent == Color(a)) 30.dp else 24.dp).clip(CircleShape).background(Color(a)).clickable { Prefs.setAccent(a) })
                    }
                }
            }
            item { T("Pulse Watch 1.0", color = W.sub, size = 11.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
        }
    }
}
