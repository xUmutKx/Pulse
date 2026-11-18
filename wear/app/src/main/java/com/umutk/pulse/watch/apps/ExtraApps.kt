package com.umutk.pulse.watch.apps

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.crown
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun WaterScreen() {
    val ctx = LocalContext.current
    val today = remember { java.time.LocalDate.now().toString() }
    var n by remember { mutableIntStateOf(if (Prefs.string("waterDay") == today) Prefs.int("water", 0) else 0) }
    fun set(v: Int) { n = v.coerceIn(0, 30); Prefs.putString("waterDay", today); Prefs.putInt("water", n) }
    Page {
        Box(Modifier.fillMaxSize().crown(stepPx = 64f) { d -> set(n + d); tick(ctx) }) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("💧", size = 26.sp)
                T("$n / 8", size = 40.sp, weight = FontWeight.Light, color = if (n >= 8) W.accent else W.text)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundButton("−") { set(n - 1); tick(ctx) }
                    RoundButton("+", color = W.accent, textColor = W.bg) { set(n + 1); tick(ctx) }
                }
            }
        }
    }
}

@Composable
fun TipScreen() {
    val ctx = LocalContext.current
    var bill by remember { mutableIntStateOf(Prefs.int("tipBill", 100)) }
    var pct by remember { mutableIntStateOf(15) }
    var people by remember { mutableIntStateOf(1) }
    val total = bill * (100 + pct) / 100.0
    Page {
        Box(Modifier.fillMaxSize().crown(stepPx = 36f) { d -> bill = (bill + d).coerceAtLeast(0); Prefs.putInt("tipBill", bill); tick(ctx) }) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("$bill", size = 44.sp, weight = FontWeight.Light)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    T("$pct %", color = W.accent, size = 15.sp, modifier = Modifier.clickable { pct = if (pct >= 25) 10 else pct + 5; tick(ctx) })
                    T("$people ×", color = W.accent, size = 15.sp, modifier = Modifier.clickable { people = if (people >= 8) 1 else people + 1; tick(ctx) })
                }
                T("= " + "%.2f".format(total), size = 17.sp)
                if (people > 1) T("/ " + "%.2f".format(total / people), color = W.sub, size = 13.sp)
            }
        }
    }
}

@Composable
fun ReactionScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableIntStateOf(0) }
    var ms by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(Prefs.int("reactBest", 0)) }
    var startAt by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun begin() { state = 1; job?.cancel(); job = scope.launch { delay(1000L + Random.nextLong(3000L)); startAt = SystemClock.uptimeMillis(); state = 2 } }
    Page(showTime = false) {
        Box(
            Modifier.fillMaxSize().background(when (state) { 2 -> Color(0xFF2E7D32); 4 -> Color(0xFF8E2424); else -> Color.Transparent })
                .clickable {
                    when (state) {
                        0, 3, 4 -> begin()
                        1 -> { job?.cancel(); state = 4 }
                        2 -> { ms = (SystemClock.uptimeMillis() - startAt).toInt(); if (best == 0 || ms < best) { best = ms; Prefs.putInt("reactBest", ms) }; state = 3; tick(ctx, true) }
                    }
                },
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (state) {
                    0 -> T("Tap to start", size = 16.sp)
                    1 -> T("Wait for green…", size = 16.sp)
                    2 -> T("TAP!", size = 34.sp, weight = FontWeight.Bold)
                    3 -> { T("$ms ms", size = 34.sp, weight = FontWeight.Light); T("Tap to go again", color = W.sub, size = 12.sp) }
                    else -> { T("Too early", size = 20.sp); T("Tap to try again", color = W.sub, size = 12.sp) }
                }
                if (best > 0 && state != 2) T("best $best ms", color = W.accent, size = 12.sp)
            }
        }
    }
}

@Composable
fun ConvertScreen() {
    val ctx = LocalContext.current
    val pairs = remember { listOf(
        Conv("°C", "°F") { it * 9 / 5 + 32 }, Conv("°F", "°C") { (it - 32) * 5 / 9 },
        Conv("km", "mi") { it * 0.621371 }, Conv("mi", "km") { it / 0.621371 },
        Conv("kg", "lb") { it * 2.20462 }, Conv("lb", "kg") { it / 2.20462 },
        Conv("cm", "in") { it / 2.54 }, Conv("in", "cm") { it * 2.54 },
    ) }
    var i by remember { mutableIntStateOf(0) }
    var v by remember { mutableIntStateOf(20) }
    val from = pairs[i].a; val to = pairs[i].b; val f = pairs[i].f
    Page {
        Box(Modifier.fillMaxSize().crown(stepPx = 36f) { d -> v += d; tick(ctx) }) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("$v $from", size = 30.sp, weight = FontWeight.Light)
                T("= " + "%.1f".format(f(v.toDouble())) + " $to", color = W.accent, size = 20.sp)
                T("$from → $to  ⟳", color = W.sub, size = 13.sp, modifier = Modifier.clickable { i = (i + 1) % pairs.size; tick(ctx) })
            }
        }
    }
}

private class Conv(val a: String, val b: String, val f: (Double) -> Double)
