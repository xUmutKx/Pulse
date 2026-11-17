package com.umutk.pulse.watch.apps

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.umutk.pulse.watch.ui.Chip
import com.umutk.pulse.watch.ui.Header
import com.umutk.pulse.watch.ui.Page
import com.umutk.pulse.watch.ui.RoundButton
import com.umutk.pulse.watch.ui.T
import com.umutk.pulse.watch.ui.W
import com.umutk.pulse.watch.ui.tick
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A sensor's values while the page is open (the listener goes when it closes); null while nothing came yet, or there is no such sensor. */
@Composable
private fun sensorValues(type: Int, enabled: Boolean = true): FloatArray? {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(type, enabled) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val s = sm?.getDefaultSensor(type)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { v = e.values.clone() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (enabled && s != null) sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    return v
}

/** Asks once for [permission] when the page opens; true once it is given. */
@Composable
private fun permitted(permission: String): Boolean {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    LaunchedEffect(Unit) { if (!ok) ask.launch(permission) }
    return ok
}

private fun hasSensor(c: Context, type: Int) = c.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

// ---- heart rate ------------------------------------------------------------------------------------------------------------------------------

@Composable
fun HeartScreen() {
    val ctx = LocalContext.current
    val ok = permitted(Manifest.permission.BODY_SENSORS)
    val v = sensorValues(Sensor.TYPE_HEART_RATE, ok)
    val bpm = v?.firstOrNull()?.roundToInt()?.takeIf { it > 0 }
    // the heart beats on the screen at the measured rate
    val beat = remember { Animatable(1f) }
    LaunchedEffect(bpm) { while (bpm != null) { beat.animateTo(1.18f, tween(120)); beat.animateTo(1f, tween(220)); delay((60_000L / bpm - 340L).coerceAtLeast(60L)) } }
    Page {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("❤", size = (34 * beat.value).sp, color = W.red)
            T(bpm?.toString() ?: "--", size = 48.sp, weight = FontWeight.Light)
            T(when { !hasSensor(ctx, Sensor.TYPE_HEART_RATE) -> "This watch has no heart rate sensor"; !ok -> "Allow the body sensors"; bpm == null -> "Measuring… keep still"; else -> "beats a minute" }, color = W.sub, size = 12.sp, align = TextAlign.Center)
        }
    }
}

// ---- steps -------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun StepsScreen() {
    val ctx = LocalContext.current
    val ok = permitted(Manifest.permission.ACTIVITY_RECOGNITION)
    val v = sensorValues(Sensor.TYPE_STEP_COUNTER, ok)
    val total = v?.firstOrNull()?.toInt()
    // today's count: the sensor counts from the start of the watch; the first value of the day is kept as the zero of the day
    val today = remember(total) {
        if (total == null) null else {
            val day = (System.currentTimeMillis() / 86_400_000L).toInt()
            if (Prefs.int("stepsDay", -1) != day || Prefs.int("stepsBase", 0) > total) { Prefs.putInt("stepsDay", day); Prefs.putInt("stepsBase", total) }
            total - Prefs.int("stepsBase", total)
        }
    }
    val goal = 8000
    Page {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = 6.dp.toPx()
            drawArc(W.card, -90f, 360f, false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
            if (today != null) drawArc(W.green, -90f, 360f * (today / goal.toFloat()).coerceIn(0f, 1f), false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("👣", size = 26.sp)
            T(today?.toString() ?: "--", size = 44.sp, weight = FontWeight.Light)
            T(when { !hasSensor(ctx, Sensor.TYPE_STEP_COUNTER) -> "No step sensor"; !ok -> "Allow physical activity"; else -> "today · goal $goal" }, color = W.sub, size = 12.sp, align = TextAlign.Center)
        }
    }
}

// ---- compass -----------------------------------------------------------------------------------------------------------------------------------

@Composable
fun CompassScreen() {
    val v = sensorValues(Sensor.TYPE_ROTATION_VECTOR)
    val azimuth = remember(v) {
        v?.let { r ->
            val m = FloatArray(9); val o = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(m, r)
            SensorManager.getOrientation(m, o)
            ((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0).toFloat()
        }
    }
    Page {
        // the rose turns so that N points north
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val a = azimuth ?: 0f
            rotate(-a) {
                val c = center; val r = size.minDimension / 2f
                for (i in 0 until 72) {
                    val long = i % 18 == 0
                    rotate(i * 5f, c) { drawLine(if (i == 0) W.red else W.sub, Offset(c.x, c.y - r), Offset(c.x, c.y - r + (if (long) 14.dp.toPx() else 6.dp.toPx())), strokeWidth = if (long) 3f else 1.5f) }
                }
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            val a = azimuth?.roundToInt()
            T(a?.let { "$it°" } ?: "--", size = 38.sp, weight = FontWeight.Light)
            T(a?.let { listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((it + 22) % 360) / 45] } ?: "Turn the watch slowly", color = W.accent, size = 15.sp, weight = FontWeight.SemiBold)
        }
    }
}

// ---- battery and device ------------------------------------------------------------------------------------------------------------------------

@Composable
fun BatteryScreen() {
    val ctx = LocalContext.current
    val info by produceState(listOf<Pair<String, String>>()) {
        while (true) {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
            val temp = b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { "%.1f °C".format(it / 10f) } ?: "—"
            val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val am = ctx.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            val up = SystemClock.elapsedRealtime() / 60_000L
            value = listOf(
                "Battery" to (if (level >= 0) "$level %" else "—") + if (plugged) " · charging" else "",
                "Temperature" to temp,
                "Memory free" to "${mem.availMem / 1_048_576} / ${mem.totalMem / 1_048_576} MB",
                "Storage free" to (st?.let { "%.1f / %.1f GB".format(it.availableBytes / 1e9, it.totalBytes / 1e9) } ?: "—"),
                "Up for" to "${up / 60} h ${up % 60} min",
                "Model" to android.os.Build.MODEL,
                "Wear OS" to "Android ${android.os.Build.VERSION.RELEASE}",
            )
            delay(5_000L)
        }
    }
    Page {
        ScalingLazyColumn(Modifier.fillMaxSize()) {
            item { Header("Battery & device") }
            info.forEach { (k, v) -> item { Chip(v, secondary = k) {} } }
        }
    }
}

// ---- breathe: the circle grows while breathing in, holds, shrinks while breathing out; a tick marks each change -------------------------------

@Composable
fun BreatheScreen() {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Tap to start") }
    var cycles by remember { mutableIntStateOf(0) }
    val grow = remember { Animatable(.35f) }
    LaunchedEffect(running) {
        while (running) {
            phase = "Breathe in"; tick(ctx); grow.animateTo(1f, tween(4000, easing = LinearEasing))
            phase = "Hold"; tick(ctx); delay(4000)
            phase = "Breathe out"; tick(ctx); grow.animateTo(.35f, tween(6000, easing = LinearEasing))
            cycles++
        }
    }
    KeepOn(running)
    Page {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(W.accent.copy(alpha = .25f), radius = grow.value * size.minDimension / 2.4f)
            drawCircle(W.accent, radius = grow.value * size.minDimension / 2.4f, style = Stroke(3.dp.toPx()))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(phase, size = 18.sp, weight = FontWeight.Medium)
            if (cycles > 0) T("$cycles", color = W.sub, size = 13.sp)
            RoundButton(if (running) "❚❚" else "▶", size = 44.dp) { running = !running; if (!running) phase = "Tap to start" }
        }
    }
}
