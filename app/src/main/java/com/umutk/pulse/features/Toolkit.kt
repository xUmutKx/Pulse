package com.umutk.pulse.features

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.FitText
import com.umutk.pulse.Guard
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.MainActivity
import com.umutk.pulse.Opt
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.R
import com.umutk.pulse.Shell
import com.umutk.pulse.Stopwatch
import com.umutk.pulse.T
import com.umutk.pulse.mb
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One tile of the Toolkit: its English and Turkish label, icon and the screen it opens. */
private class Tool(val label: String, val trLabel: String, val icon: String, val body: @Composable () -> Unit)

/** The tool a Quick Settings tile asked for ("toolkit:<tool>"); ToolkitPage opens it and clears this. */
object ToolkitOpen { var tool by mutableStateOf("") }

private val tools = listOf(
    Tool("Compass", "Pusula", "compass") { Compass() },
    Tool("Level", "Su terazisi", "level") { Level() },
    Tool("Flashlight", "El feneri", "flash") { Flashlight() },
    Tool("Lux meter", "Işık ölçer", "light") { LuxMeter() },
    Tool("Dice", "Zar", "dice") { Dice() },
    Tool("Coin flip", "Yazı tura", "coin") { CoinFlip() },
    Tool("Clipboard", "Pano", "clipboard") { ClipboardTool() },
    Tool("Screenshot", "Ekran görüntüsü", "screenshot") { ScreenshotTool() },
    Tool("Sensors off", "Sensörler kapalı", "sensors") { SensorsOffTool() },
    Tool("Battery", "Pil", "battery") { BatteryTool() },
    Tool("Memory", "Bellek", "memory") { MemoryTool() },
    Tool("Temperature", "Sıcaklık", "thermal") { TemperatureTool() },
    Tool("Volume", "Ses düzeyi", "volume") { VolumeTool() },
    Tool("Sound mode", "Ses modu", "vibrate") { SoundModeTool() },
    Tool("Stopwatch", "Kronometre", "stopwatch") { StopwatchTool() },
    Tool("Timer", "Zamanlayıcı", "timer") { TimerTool() },
    Tool("Network type", "Ağ türü", "globe") { NetworkModeTool() },
    Tool("DNS and TTL", "DNS ve TTL", "globe") { BypassTool() },
    Tool("Screen scale", "Ekran ölçeği", "scale") { ScreenScaleTool() },
    Tool("Finder", "Bulucu", "search") { FinderTool() },
    Tool("Screen rotation", "Ekran yönü", "rotate") { RotationTool() },
    Tool("Show touches", "Dokunma noktaları", "touch") { FlagTool("show_touches", tr("Show a dot under every finger", "Her parmağın altında nokta göster")) },
    Tool("Pointer location", "Dokunma çizgileri", "pointer") { FlagTool("pointer_location", tr("Show touch lines and coordinates", "Dokunma çizgilerini ve koordinatları göster")) },
    Tool("Motion cues", "Hareket noktaları", "motion") { MotionCueTool() },
    Tool("Floating keys", "Yüzen tuşlar", "keyboard") { FloatingKeysTool() },
    Tool("Power menu", "Güç menüsü", "power") { PowerMenuTool() },
    Tool("Dink", "Dink", "dink") { Dink() },
    Tool("DNS", "DNS", "wifi") { DnsSettings() },
    Tool("Screen cover", "Ekran kapağı", "lock") { CoverTool() },
    Tool("Floating timer", "Yüzen sayaç", "pin") { FloatingTimerTool() },
)

/** The Toolkit page: a 3-column grid of tiles; a tile opens its tool, the chevron goes back. */
@Composable
fun ToolkitPage() {
    var open by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(ToolkitOpen.tool) { if (ToolkitOpen.tool.isNotEmpty()) { open = tools.indexOfFirst { it.icon == ToolkitOpen.tool }; ToolkitOpen.tool = "" } }
    com.umutk.pulse.InnerBackStep(open >= 0) { open = -1 }
    if (open >= 0) {
        val t = tools[open]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PHead(tr(t.label, t.trLabel))
            t.body()
            Spacer(Modifier.height(12.dp))
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead(tr("Toolkit", "Araç kutusu"), guide = "toolkit")
        tools.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, t ->
                    PCard(Modifier.weight(1f).clip(RoundedCornerShape(T.radius)).clickable { open = r * 3 + c }) {
                        PIcon(t.icon, Look.accent, 28.dp, Modifier.align(Alignment.CenterHorizontally))
                        Text(tr(t.label, t.trLabel), Modifier.fillMaxWidth(), color = T.text, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToolNote(s: String, color: Color = T.sub) = Text(s, Modifier.fillMaxWidth(), color = color, fontSize = 14.sp, textAlign = TextAlign.Center)

/** The big centred reading most tools lead with. */
@Composable
private fun BigValue(s: String, size: Int = 40) = FitText(s, T.text, size.sp, Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally), FontWeight.SemiBold)

private fun prefs(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
private fun hasSensor(ctx: Context, type: Int) = ctx.getSystemService(SensorManager::class.java).getDefaultSensor(type) != null
private val warn = Color(0xFFE53935)

/** A sensor's values after [fold] (default: the raw reading) and its accuracy (SensorManager.SENSOR_STATUS_*). */
private class Reading(val v: FloatArray, val acc: Int)

/** Low-pass filter for [sensorReading]: each new value moves the kept one by [k], which steadies a bubble or a needle. */
private fun lowPass(k: Float) = { old: FloatArray?, new: FloatArray -> if (old == null || old.size != new.size) new.clone() else FloatArray(new.size) { old[it] + (new[it] - old[it]) * k } }

/** The latest reading of a sensor, null until the first one arrives. [fold] turns the previous and the new raw values into what is kept. */
@Composable
private fun sensorReading(type: Int, fold: (FloatArray?, FloatArray) -> FloatArray = { _, n -> n.clone() }): State<Reading?> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf<Reading?>(null) }
    DisposableEffect(type) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { st.value = Reading(fold(st.value?.v, e.values), e.accuracy) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { st.value?.let { st.value = Reading(it.v, a) } }
        }
        sm.getDefaultSensor(type)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }
    return st
}

/** Re-reads [read] every [ms] while the screen is shown. */
@Composable
private fun <V> polled(ms: Long, read: () -> V): V {
    val st = remember { mutableStateOf(read()) }
    LaunchedEffect(Unit) { while (true) { delay(ms); st.value = read() } }
    return st.value
}

/** The battery broadcast, read again every two seconds. */
@Composable
private fun rememberBattery(): Intent? {
    val ctx = LocalContext.current
    return polled(2000) { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
}

private fun dirName(deg: Float): String {
    val i = ((deg + 22.5f) / 45f).toInt() % 8
    return tr(listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[i], listOf("K", "KD", "D", "GD", "G", "GB", "B", "KB")[i])
}

@Composable
private fun Compass() {
    val ctx = LocalContext.current
    // the fused rotation vector is steadier; the magnetometer-only one is the fallback on phones without a gyroscope
    val type = remember { if (hasSensor(ctx, Sensor.TYPE_ROTATION_VECTOR)) Sensor.TYPE_ROTATION_VECTOR else Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR }
    // kept value: the heading in degrees, smoothed the short way round so 359° to 1° does not swing back through 180°
    val r by sensorReading(type) { old, rv ->
        val m = FloatArray(9); val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(m, rv); SensorManager.getOrientation(m, o)
        val az = (Math.toDegrees(o[0].toDouble()).toFloat() + 360f) % 360f
        if (old == null) floatArrayOf(az) else floatArrayOf((old[0] + (((az - old[0] + 540f) % 360f) - 180f) * .15f + 360f) % 360f)
    }
    // the magnetometer reports its own calibration state; the rotation vector does not always pass it on
    val mag by sensorReading(Sensor.TYPE_MAGNETIC_FIELD)
    if (!hasSensor(ctx, type)) { PCard { ToolNote(tr("No compass sensor", "Pusula sensörü yok")) }; return }
    val rd = r
    if (rd == null) { PCard { ToolNote(tr("Waiting for the sensor…", "Sensör bekleniyor…")) }; return }
    val deg = rd.v[0]
    val acc = mag?.acc ?: rd.acc
    val names = listOf(tr("N", "K"), tr("E", "D"), tr("S", "G"), tr("W", "B"))
    val text = T.text; val sub = T.sub; val accent = Look.accent
    val paint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true } }
    PCard {
        Canvas(Modifier.size(260.dp).align(Alignment.CenterHorizontally)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r0 = size.minDimension / 2 - 8.dp.toPx()
            // fixed heading mark at the top
            drawPath(Path().apply { moveTo(c.x - 7.dp.toPx(), 0f); lineTo(c.x + 7.dp.toPx(), 0f); lineTo(c.x, 12.dp.toPx()); close() }, accent)
            // the card turns against the heading, so each mark stays on its bearing
            rotate(-deg, c) {
                drawCircle(sub.copy(alpha = .5f), r0, c, style = Stroke(1.5.dp.toPx()))
                for (i in 0 until 72) {
                    val long = i % 6 == 0
                    rotate(i * 5f, c) { drawLine(if (long) text else sub, Offset(c.x, c.y - r0), Offset(c.x, c.y - r0 + (if (long) 14 else 7).dp.toPx()), if (long) 2.dp.toPx() else 1.dp.toPx()) }
                }
                val nc = drawContext.canvas.nativeCanvas
                for (i in 0 until 12) rotate(i * 30f, c) {
                    val cardinal = i % 3 == 0
                    paint.textSize = (if (cardinal) 20 else 11).sp.toPx(); paint.isFakeBoldText = cardinal
                    paint.color = (if (i == 0) warn else if (cardinal) text else sub).toArgb()
                    nc.drawText(if (cardinal) names[i / 3] else "${i * 30}", c.x, c.y - r0 + (if (cardinal) 38 else 30).dp.toPx(), paint)
                }
                // needle: red half to north
                val n = r0 * .52f; val w = r0 * .09f
                drawPath(Path().apply { moveTo(c.x, c.y - n); lineTo(c.x - w, c.y); lineTo(c.x + w, c.y); close() }, warn)
                drawPath(Path().apply { moveTo(c.x, c.y + n); lineTo(c.x - w, c.y); lineTo(c.x + w, c.y); close() }, sub)
                drawCircle(text, w * .5f, c)
            }
        }
        BigValue("${deg.roundToInt() % 360}°  ${dirName(deg)}")
        if (acc <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) ToolNote(tr("Compass needs calibration", "Pusula kalibrasyon istiyor"), warn)
    }
}

@Composable
private fun Level() {
    val ctx = LocalContext.current
    val r by sensorReading(Sensor.TYPE_ACCELEROMETER, lowPass(.12f))
    if (!hasSensor(ctx, Sensor.TYPE_ACCELEROMETER)) { PCard { ToolNote(tr("No accelerometer", "İvmeölçer yok")) }; return }
    val a = r?.v
    if (a == null) { PCard { ToolNote(tr("Waiting for the sensor…", "Sensör bekleniyor…")) }; return }
    val g = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).coerceAtLeast(.1f)
    val flat = abs(a[2]) / g > .5f
    // flat: tilt along each screen axis; upright: how far the edge is off the nearest 90° step
    val ax = Math.toDegrees(asin((a[0] / g).toDouble())).toFloat()
    val ay = Math.toDegrees(asin((a[1] / g).toDouble())).toFloat()
    val edge = (Math.toDegrees(atan2(a[0].toDouble(), a[1].toDouble())).toFloat() + 405f) % 90f - 45f
    val level = if (flat) abs(ax) < 1f && abs(ay) < 1f else abs(edge) < 1f
    val line = T.sub; val accent = Look.accent; val ok = Color(0xFF43A047)
    PCard {
        if (flat) Canvas(Modifier.size(240.dp).align(Alignment.CenterHorizontally)) {
            val c = Offset(size.width / 2, size.height / 2)
            val rr = size.minDimension / 2
            drawCircle(line, rr, c, style = Stroke(2.dp.toPx()))
            drawCircle(line, rr * .5f, c, style = Stroke(1.dp.toPx()))
            drawLine(line.copy(alpha = .4f), Offset(c.x - rr, c.y), Offset(c.x + rr, c.y))
            drawLine(line.copy(alpha = .4f), Offset(c.x, c.y - rr), Offset(c.x, c.y + rr))
            val br = 22.dp.toPx()
            drawCircle(if (level) ok else line, br + 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            // the bubble floats to the high side; ~20° tilt reaches the rim
            val k = rr * 2.8f
            var bx = a[0] / g * k; var by = -a[1] / g * k
            val d = sqrt(bx * bx + by * by); val lim = rr - br - 2.dp.toPx()
            if (d > lim) { bx *= lim / d; by *= lim / d }
            drawCircle(if (level) ok else accent, br, Offset(c.x + bx, c.y + by))
        } else Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val h = size.height; val w = size.width
            drawRoundRect(line, Offset(0f, 0f), Size(w, h), CornerRadius(h / 2), Stroke(2.dp.toPx()))
            val br = h * .32f
            drawLine(if (level) ok else line, Offset(w / 2 - br - 4.dp.toPx(), 4.dp.toPx()), Offset(w / 2 - br - 4.dp.toPx(), h - 4.dp.toPx()), 2.dp.toPx())
            drawLine(if (level) ok else line, Offset(w / 2 + br + 4.dp.toPx(), 4.dp.toPx()), Offset(w / 2 + br + 4.dp.toPx(), h - 4.dp.toPx()), 2.dp.toPx())
            val x = (w / 2 + sin(Math.toRadians(edge.toDouble())).toFloat() * w * 1.4f).coerceIn(br + h * .18f, w - br - h * .18f)
            drawCircle(if (level) ok else accent, br, Offset(x, h / 2))
        }
        BigValue(if (flat) "%.1f°  %.1f°".format(abs(ax), abs(ay)) else "%.1f°".format(abs(edge)))
        ToolNote(if (level) tr("Level", "Düz") else tr("Not level", "Eğik"), if (level) ok else T.sub)
    }
}

@Composable
private fun Flashlight() {
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = remember { runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() }
    if (id == null) { PCard { ToolNote(tr("No flash on this phone", "Bu telefonda flaş yok")) }; return }
    // brightness steps exist on Android 13+ when the flash supports them (max level above 1)
    val max = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1 }.getOrDefault(1) else 1 }
    var level by remember { mutableIntStateOf(if (max > 1 && Build.VERSION.SDK_INT >= 33) runCatching { cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: max }.getOrDefault(max) else 1) }
    var on by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) } // the camera is in use: the torch cannot be switched now
    // the callback reports the current state at once, then every change, also from the tile or another app
    DisposableEffect(id) {
        if (android.os.Build.VERSION.SDK_INT < 23) return@DisposableEffect onDispose { }
        val cb = torchCallback(cm, id, max, { on = it; busy = false }, { busy = true }, { level = it })
        cm.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { cm.unregisterTorchCallback(cb) }
    }
    fun turn(want: Boolean) {
        try { if (want && max > 1 && Build.VERSION.SDK_INT >= 33) cm.turnOnTorchWithStrengthLevel(id, level) else cm.setTorchMode(id, want) } catch (e: Exception) { busy = true }
    }
    PCard {
        Box(Modifier.align(Alignment.CenterHorizontally).size(120.dp).clip(CircleShape).background(if (on) Look.accent else T.line.copy(alpha = .5f)).clickable { turn(!on) }, contentAlignment = Alignment.Center) {
            PIcon("flash", if (on) Color.White else T.sub, 56.dp)
        }
        ToolNote(when { busy -> tr("Camera in use", "Kamera kullanımda"); on -> tr("On", "Açık"); else -> tr("Off", "Kapalı") })
        PButton(if (on) tr("Turn off", "Kapat") else tr("Turn on", "Aç"), Modifier.fillMaxWidth()) { turn(!on) }
        if (max > 1) {
            Text(tr("Brightness", "Parlaklık") + "  $level / $max", color = T.text, fontSize = 13.sp)
            PSlider(level.toFloat(), { v ->
                level = v.roundToInt().coerceIn(1, max)
                if (on && Build.VERSION.SDK_INT >= 33) runCatching { cm.turnOnTorchWithStrengthLevel(id, level) }
            }, 1f..max.toFloat()) {}
        }
    }
}

@Composable
private fun LuxMeter() {
    val ctx = LocalContext.current
    val r by sensorReading(Sensor.TYPE_LIGHT)
    var lo by remember { mutableFloatStateOf(Float.NaN) }
    var hi by remember { mutableFloatStateOf(Float.NaN) }
    val lx = r?.v?.get(0)
    LaunchedEffect(lx) { if (lx != null) { if (lo.isNaN() || lx < lo) lo = lx; if (hi.isNaN() || lx > hi) hi = lx } }
    if (!hasSensor(ctx, Sensor.TYPE_LIGHT)) { PCard { ToolNote(tr("No light sensor", "Işık sensörü yok")) }; return }
    fun f(v: Float) = if (v.isNaN()) "—" else "%.0f lx".format(v)
    PCard {
        BigValue(if (lx == null) "— lx" else f(lx))
        // log scale: 1 lx to 100 000 lx (sunlight) spread evenly
        BarMeter(((log10((lx ?: 0f) + 1f)) / 5f).coerceIn(0f, 1f), Look.accent)
        if (lx != null) ToolNote(when {
            lx < 10f -> tr("Dark", "Karanlık"); lx < 200f -> tr("Dim", "Loş"); lx < 1000f -> tr("Indoor", "İç mekân")
            lx < 10000f -> tr("Bright", "Aydınlık"); else -> tr("Sunlight", "Güneş ışığı")
        })
        InfoRows(listOf(tr("Min", "En düşük") to f(lo), tr("Max", "En yüksek") to f(hi)))
        PButton(tr("Reset", "Sıfırla"), Modifier.fillMaxWidth()) { lo = lx ?: Float.NaN; hi = lx ?: Float.NaN }
    }
}

/** Pip spots on a 3 x 3 grid (0 top-left .. 8 bottom-right) for each face. */
private val PIPS = listOf(listOf(4), listOf(0, 8), listOf(0, 4, 8), listOf(0, 2, 6, 8), listOf(0, 2, 4, 6, 8), listOf(0, 2, 3, 5, 6, 8))

@Composable
private fun Dice() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // the last roll is shared with the Quick Settings tile
    var face by remember { mutableIntStateOf(prefs(ctx).getInt("dice_last", 0).takeIf { it in 1..6 } ?: 1) }
    var rolling by remember { mutableStateOf(false) }
    var spins by remember { mutableIntStateOf(0) }
    val rot by animateFloatAsState(spins * 360f, tween(720, easing = FastOutSlowInEasing), label = "dice")
    val pip = T.text
    PCard {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(12.dp).size(140.dp).graphicsLayer { rotationZ = rot }
            .background(T.panel, RoundedCornerShape(22.dp)).border(2.dp, T.sub, RoundedCornerShape(22.dp))) {
            Canvas(Modifier.fillMaxSize().padding(22.dp)) {
                PIPS[face - 1].forEach { s -> drawCircle(pip, size.width * .11f, Offset(size.width * (s % 3) / 2f, size.height * (s / 3) / 2f)) }
            }
        }
        ToolNote(if (rolling) "…" else "$face", T.text)
        PButton(tr("Roll", "At"), Modifier.fillMaxWidth()) {
            if (!rolling) scope.launch {
                rolling = true; spins++
                repeat(12) { face = Random.nextInt(1, 7); delay(60) }
                prefs(ctx).edit().putInt("dice_last", face).apply()
                rolling = false
            }
        }
    }
}

@Composable
private fun CoinFlip() {
    val ctx = LocalContext.current
    // prefs keep "Heads" / "Tails" (the tile translates them); the last result is shared with the tile
    val first = remember { prefs(ctx).getString("coin_last", "Heads") != "Tails" }
    var heads by remember { mutableStateOf(first) }
    var turns by remember { mutableIntStateOf(0) } // half-turns since the screen opened
    val rot by animateFloatAsState(turns * 180f, tween(1100, easing = FastOutSlowInEasing), label = "coin")
    val half = ((rot + 90f) / 180f).toInt() // half-turns already shown
    val showHeads = first xor (half % 2 == 1)
    val settled = rot == turns * 180f
    PCard {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(8.dp).size(160.dp)
            .graphicsLayer { rotationY = rot; cameraDistance = 14f * density }
            .background(Look.accent, CircleShape).border(4.dp, Color.White.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
            // the back of the coin is drawn mirrored; turning the text once more reads it the right way
            Text(if (showHeads) tr("H", "T") else tr("T", "Y"), Modifier.graphicsLayer { rotationY = if (half % 2 == 1) 180f else 0f },
                color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
        ToolNote(if (!settled) "…" else if (heads) tr("Heads", "Tura") else tr("Tails", "Yazı"), T.text)
        PButton(tr("Flip", "At"), Modifier.fillMaxWidth()) {
            if (settled) {
                val result = Random.nextBoolean()
                // at least six half-turns; an odd count lands on the other side
                turns += 2 * Random.nextInt(3, 5) + if (result != heads) 1 else 0
                heads = result
                prefs(ctx).edit().putString("coin_last", if (result) "Heads" else "Tails").apply()
            }
        }
    }
}

@Composable
private fun ClipboardTool() {
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    // Android 10+ shows the clipboard only to the focused window, so read when focus arrives and on every change after
    val focused = LocalWindowInfo.current.isWindowFocused
    var shown by remember { mutableStateOf<String?>(null) }
    DisposableEffect(focused) {
        val l = ClipboardManager.OnPrimaryClipChangedListener { shown = TileActions.clipboardText(ctx) }
        if (focused) { shown = TileActions.clipboardText(ctx); cm.addPrimaryClipChangedListener(l) }
        onDispose { cm.removePrimaryClipChangedListener(l) }
    }
    var text by remember { mutableStateOf("") }
    PCard {
        val s = shown
        if (s.isNullOrEmpty()) ToolNote(tr("Clipboard is empty", "Pano boş"))
        else {
            SelectionContainer { Text(s, color = T.text, fontSize = 14.sp, maxLines = 12, overflow = TextOverflow.Ellipsis) }
            Text(tr("${s.length} characters", "${s.length} karakter"), color = T.sub, fontSize = 12.sp)
        }
    }
    PCard {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(tr("Text to copy", "Kopyalanacak metin"), color = T.sub) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T.text, unfocusedTextColor = T.text, focusedBorderColor = Look.accent, unfocusedBorderColor = T.line, cursorColor = Look.accent))
        PButton(tr("Copy", "Kopyala"), Modifier.fillMaxWidth()) {
            if (text.isNotEmpty()) { cm.setPrimaryClip(ClipData.newPlainText("Pulse", text)); text = "" }
        }
    }
}

@Composable
private fun ScreenshotTool() {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // the last shot is shared with the tile: its time and file
    var last by remember { mutableLongStateOf(prefs(ctx).getLong("shot_last", 0L)) }
    if (accessMissing()) { AccessNeededCard(tr("Screenshot", "Ekran görüntüsü"), tr("Taking a screenshot from here needs root or Shizuku.", "Buradan ekran görüntüsü almak için root veya Shizuku gerekir.")); return }
    PCard {
        PIcon("screenshot", if (busy) Look.accent else T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        PButton(if (busy) tr("Saving…", "Kaydediliyor…") else tr("Take screenshot", "Ekran görüntüsü al"), Modifier.fillMaxWidth()) {
            if (!busy) {
                busy = true; failed = false
                if (!TileActions.screenshot(ctx) { ok -> busy = false; failed = !ok; last = prefs(ctx).getLong("shot_last", 0L) }) { busy = false; failed = true }
            }
        }
        if (failed) ToolNote(tr("Screenshot failed", "Ekran görüntüsü alınamadı"), warn)
        if (last > 0) InfoRows(listOf(tr("Last", "Son") to android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(last)), tr("Folder", "Klasör") to "DCIM/Screenshots"))
        // the uri arrives from the media scan a moment after the shot
        val uri = polled(500) { prefs(ctx).getString("shot_uri", null) }
        if (uri != null && !busy) PButton(tr("Open", "Aç"), Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun SensorsOffTool() {
    val ctx = LocalContext.current
    var off by remember { mutableStateOf(TileActions.sensorsOff(ctx)) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Shell.shizuku, Shell.root) { TileActions.refreshSensors(ctx) { off = it ?: off } }
    if (accessMissing()) { AccessNeededCard(tr("Sensors off", "Sensörler kapalı"), tr("Switching sensors needs root or Shizuku.", "Sensörleri kapatmak için root veya Shizuku gerekir.")); return }
    PCard {
        PIcon("sensors", if (off == true) Look.accent else T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Microphone and camera off", "Mikrofon ve kamera kapalı"), Modifier.weight(1f), color = T.text, fontSize = 14.sp)
            PSwitch(off == true, { if (!busy) { busy = true; if (!TileActions.toggleSensorsOff(ctx) { off = it; busy = false }) busy = false } }, enabled = !busy)
        }
        when {
            busy -> ToolNote("…")
            off == null -> ToolNote(tr("State unknown", "Durum bilinmiyor"))
        }
    }
}

@Composable
private fun BatteryTool() {
    val ctx = LocalContext.current
    val i = rememberBattery()
    val bm = ctx.getSystemService(BatteryManager::class.java)
    val now = polled(2000) { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
    if (i == null) { PCard { ToolNote(tr("Reading…", "Okunuyor…")) }; return }
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val pct = level * 100 / scale
    val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> tr("Charging", "Şarj oluyor")
        BatteryManager.BATTERY_STATUS_FULL -> tr("Full", "Dolu")
        BatteryManager.BATTERY_STATUS_DISCHARGING -> tr("Discharging", "Boşalıyor")
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> tr("Not charging", "Şarj olmuyor")
        else -> tr("Unknown", "Bilinmiyor")
    }
    val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
        BatteryManager.BATTERY_PLUGGED_AC -> tr("Charger", "Şarj aleti"); BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> tr("Wireless", "Kablosuz"); 0 -> tr("Unplugged", "Takılı değil"); else -> tr("Dock", "Yuva")
    }
    val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
        BatteryManager.BATTERY_HEALTH_GOOD -> tr("Good", "İyi"); BatteryManager.BATTERY_HEALTH_OVERHEAT -> tr("Overheat", "Aşırı ısınma")
        BatteryManager.BATTERY_HEALTH_DEAD -> tr("Dead", "Ölü"); BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> tr("Over voltage", "Aşırı gerilim")
        BatteryManager.BATTERY_HEALTH_COLD -> tr("Cold", "Soğuk"); else -> tr("Unknown", "Bilinmiyor")
    }
    // CURRENT_NOW is µA on most phones, mA on a few: a value that large can only be µA
    val ma = now.takeIf { it != Int.MIN_VALUE && it != 0 }?.let { if (abs(it) > 20000) it / 1000 else it }
    PCard {
        BigValue("$pct%")
        BarMeter(pct / 100f, if (pct <= 15) warn else Look.accent)
        InfoRows(listOfNotNull(
            tr("Status", "Durum") to status,
            tr("Power source", "Güç kaynağı") to plug,
            tr("Health", "Sağlık") to health,
            tr("Temperature", "Sıcaklık") to Opt.temp(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f),
            tr("Voltage", "Gerilim") to "%.2f V".format(i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f),
            ma?.let { tr("Current", "Akım") to "$it mA" },
        ))
    }
}

@Composable
private fun MemoryTool() {
    val ctx = LocalContext.current
    val am = ctx.getSystemService(ActivityManager::class.java)
    val m = polled(2000) { ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) } }
    val used = m.totalMem - m.availMem
    val frac = used.toFloat() / m.totalMem.coerceAtLeast(1)
    PCard {
        BigValue(tr("${(frac * 100).roundToInt()}% used", "%${(frac * 100).roundToInt()} dolu"), 32)
        BarMeter(frac, if (m.lowMemory) warn else Look.accent)
        InfoRows(listOf(
            tr("Total", "Toplam") to mb((m.totalMem shr 20).toInt()),
            tr("Used", "Kullanılan") to mb((used shr 20).toInt()),
            tr("Available", "Kullanılabilir") to mb((m.availMem shr 20).toInt()),
        ))
        if (m.lowMemory) ToolNote(tr("Memory is low", "Bellek az"), warn)
    }
}

@Composable
private fun TemperatureTool() {
    val ctx = LocalContext.current
    val i = rememberBattery()
    var cpu by remember { mutableFloatStateOf(0f) }
    // thermal zones can need a file read per zone, so off the main thread
    LaunchedEffect(Unit) { while (true) { cpu = withContext(Dispatchers.IO) { runCatching { Guard.cpuTemp() }.getOrDefault(0f) }; delay(2000) } }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val status = polled(2000) { pm.currentThermalStatus }
    if (i == null) { PCard { ToolNote(tr("Reading…", "Okunuyor…")) }; return }
    val bat = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
    PCard {
        BigValue(Opt.temp(bat))
        ToolNote(tr("Battery", "Pil"))
        InfoRows(listOfNotNull(
            cpu.takeIf { it > 0f }?.let { tr("Processor", "İşlemci") to Opt.temp(it) },
            tr("Thermal state", "Isı durumu") to when (status) {
                PowerManager.THERMAL_STATUS_NONE -> tr("Normal", "Normal"); PowerManager.THERMAL_STATUS_LIGHT -> tr("Warm", "Ilık")
                PowerManager.THERMAL_STATUS_MODERATE -> tr("Hot", "Sıcak"); PowerManager.THERMAL_STATUS_SEVERE -> tr("Throttling", "Yavaşlatılıyor")
                else -> tr("Critical", "Kritik")
            },
        ))
    }
}

@Composable
private fun VolumeTool() {
    val ctx = LocalContext.current
    val am = ctx.getSystemService(AudioManager::class.java)
    val streams = listOf(
        tr("Media", "Medya") to AudioManager.STREAM_MUSIC, tr("Ring", "Zil") to AudioManager.STREAM_RING, tr("Alarm", "Alarm") to AudioManager.STREAM_ALARM,
        tr("Notification", "Bildirim") to AudioManager.STREAM_NOTIFICATION, tr("Call", "Arama") to AudioManager.STREAM_VOICE_CALL, tr("System", "Sistem") to AudioManager.STREAM_SYSTEM,
    )
    val vols = remember { streams.map { mutableIntStateOf(am.getStreamVolume(it.second)) } }
    // follows the volume keys and other apps
    LaunchedEffect(Unit) { while (true) { delay(400); streams.forEachIndexed { i, (_, s) -> vols[i].intValue = am.getStreamVolume(s) } } }
    PCard {
        streams.forEachIndexed { idx, (name, s) ->
            val lo = am.getStreamMinVolume(s); val hi = am.getStreamMaxVolume(s).coerceAtLeast(lo + 1)
            val v = vols[idx].intValue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), color = T.text, fontSize = 13.sp)
                Text("$v / $hi", color = T.sub, fontSize = 13.sp)
            }
            PSlider(v.toFloat(), { nv ->
                // in Do Not Disturb, ring and notification changes need DND access; reading back shows what stuck
                runCatching { am.setStreamVolume(s, nv.roundToInt().coerceIn(lo, hi), 0) }
                vols[idx].intValue = am.getStreamVolume(s)
            }, lo.toFloat()..hi.toFloat()) {}
        }
    }
}

@Composable
private fun SoundModeTool() {
    val ctx = LocalContext.current
    val am = ctx.getSystemService(AudioManager::class.java)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    // follows the volume keys, the tile and Settings; access is checked again after a trip to Settings
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(700); tick++ } }
    val mode = remember(tick) { am.ringerMode }
    val access = remember(tick) { nm.isNotificationPolicyAccessGranted }
    var refused by remember { mutableStateOf(false) }
    fun openAccess() = runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
    fun set(m: Int) {
        if (m == AudioManager.RINGER_MODE_SILENT && !access) { openAccess(); return }
        refused = try { am.ringerMode = m; false } catch (e: SecurityException) { true }
        tick++
    }
    PCard {
        listOf(AudioManager.RINGER_MODE_NORMAL to tr("Sound", "Ses"), AudioManager.RINGER_MODE_VIBRATE to tr("Vibrate", "Titreşim"), AudioManager.RINGER_MODE_SILENT to tr("Silent", "Sessiz")).forEach { (m, label) ->
            Row(Modifier.fillMaxWidth().clickable { set(m) }, verticalAlignment = Alignment.CenterVertically) {
                PRadio(mode == m) { set(m) }
                Spacer(Modifier.width(8.dp))
                Text(label, color = T.text, fontSize = 14.sp)
            }
        }
        if (refused) ToolNote(tr("Do Not Disturb blocked the change", "Rahatsız Etmeyin değişikliği engelledi"), warn)
        if (!access) {
            ToolNote(tr("Silent needs Do Not Disturb access", "Sessiz için Rahatsız Etmeyin izni gerekir"))
            PButton(tr("Allow", "İzin ver"), Modifier.fillMaxWidth()) { openAccess() }
        }
    }
}

@Composable
private fun StopwatchTool() {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(Stopwatch.running(ctx)) }
    var elapsed by remember { mutableLongStateOf(Stopwatch.elapsed(ctx)) }
    // one clock with the tile: read it back all the time, so a tap on the tile shows here too
    LaunchedEffect(Unit) { while (true) { running = Stopwatch.running(ctx); elapsed = Stopwatch.elapsed(ctx); delay(if (running) 33 else 300) } }
    PCard {
        BigValue(Stopwatch.clock(elapsed), 44)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(if (running) tr("Stop", "Durdur") else if (elapsed > 0) tr("Resume", "Sürdür") else tr("Start", "Başlat"), Modifier.weight(1f)) {
                Stopwatch.toggle(ctx); running = Stopwatch.running(ctx); elapsed = Stopwatch.elapsed(ctx)
            }
            PButton(tr("Reset", "Sıfırla"), Modifier.weight(1f)) { Stopwatch.reset(ctx); running = false; elapsed = 0L }
        }
    }
}

/** The countdown timer, kept in prefs "timer" so it goes on when the screen or the app closes: "end" (wall-clock ms) while it runs,
 *  which the Quick Settings tile reads; "left" (ms) while paused or finished (0); "set" (ms) the chosen length. */
object TimerClock {
    private const val NOTE_ID = 7103
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private fun p(c: Context) = c.getSharedPreferences("timer", Context.MODE_PRIVATE)

    fun length(c: Context) = p(c).getLong("set", 60_000L)
    fun running(c: Context) = p(c).getLong("end", 0L) > 0L
    fun remaining(c: Context): Long {
        val s = p(c); val end = s.getLong("end", 0L)
        return if (end > 0L) (end - System.currentTimeMillis()).coerceAtLeast(0L) else s.getLong("left", length(c))
    }

    fun choose(c: Context, ms: Long) { cancel(c); p(c).edit().putLong("set", ms).remove("end").remove("left").apply() }
    fun start(c: Context) {
        val end = System.currentTimeMillis() + (remaining(c).takeIf { it > 0L } ?: length(c))
        p(c).edit().putLong("end", end).remove("left").apply()
        schedule(c, end)
    }
    fun pause(c: Context) { val left = remaining(c); cancel(c); p(c).edit().remove("end").putLong("left", left).apply() }
    fun reset(c: Context) { cancel(c); p(c).edit().remove("end").remove("left").apply() }
    /** The clock face picked on the timer screen: digits, ring, bold or tomato. */
    fun skin(c: Context) = p(c).getString("skin", "digits") ?: "digits"
    fun setSkin(c: Context, k: String) { p(c).edit().putString("skin", k).apply() }

    /** Re-arms the in-app wake-up after Pulse was restarted while a timer runs. */
    fun resume(c: Context) { p(c).getLong("end", 0L).takeIf { it > 0L }?.let { schedule(c, it) } }

    /** Finishes the timer once its end has passed. The in-app handler, the alarm and the screen all call it; the first one wins. */
    fun check(c: Context) {
        val end = p(c).getLong("end", 0L)
        if (end == 0L || end > System.currentTimeMillis()) return
        p(c).edit().remove("end").putLong("left", 0L).apply()
        notifyDone(c)
    }

    // shortcut: without SCHEDULE_EXACT_ALARM the alarm may come late in Doze; the in-app handler covers the time Pulse is alive
    private fun schedule(c: Context, end: Long) {
        val app = c.applicationContext
        pending?.let(main::removeCallbacks)
        pending = Runnable { check(app) }.also { main.postDelayed(it, (end - System.currentTimeMillis()).coerceAtLeast(0L)) }
        val am = app.getSystemService(AlarmManager::class.java)
        runCatching { if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(app)) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(app)) }
    }

    private fun cancel(c: Context) {
        val app = c.applicationContext
        pending?.let(main::removeCallbacks); pending = null
        app.getSystemService(AlarmManager::class.java).cancel(alarm(app))
        app.getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
    }

    private fun alarm(c: Context) = PendingIntent.getBroadcast(c, 7101, Intent(c, TimerReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notifyDone(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("timer", tr("Timer", "Zamanlayıcı"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        }) else Unit
        val open = PendingIntent.getActivity(c, 7102, Intent(c, MainActivity::class.java).putExtra("start", "toolkit:timer")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            nm.notify(NOTE_ID, androidx.core.app.NotificationCompat.Builder(c, "timer").setSmallIcon(R.drawable.ic_qs_timer)
                .setContentTitle(tr("Time's up", "Süre doldu")).setContentText(timerText(length(c)))
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}

/** Wakes Pulse when a timer ends while the app is closed. Needs a <receiver> entry in the manifest. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) { Opt.load(c); TimerClock.check(c); PomodoroClock.check(c) }
}

internal fun timerText(ms: Long): String {
    val s = (ms + 999) / 1000 // round up, so the last second shows 00:01 (as the tile)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun TimerTool() {
    val ctx = LocalContext.current
    // Android 13+ hides the "time's up" notification until notifications are allowed
    val askNotes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askPerm() {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    var running by remember { mutableStateOf(TimerClock.running(ctx)) }
    var left by remember { mutableLongStateOf(TimerClock.remaining(ctx)) }
    var length by remember { mutableLongStateOf(TimerClock.length(ctx)) }
    var pomo by remember { mutableStateOf(PomodoroClock.on(ctx)) }
    var skin by remember { mutableStateOf(TimerClock.skin(ctx)) }
    LaunchedEffect(Unit) {
        TimerClock.resume(ctx)
        while (true) { TimerClock.check(ctx); running = TimerClock.running(ctx); left = TimerClock.remaining(ctx); delay(200) }
    }
    fun sync() { running = TimerClock.running(ctx); left = TimerClock.remaining(ctx); length = TimerClock.length(ctx) }
    val done = !running && left == 0L
    PCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(!pomo, tr("Timer", "Zamanlayıcı")) { pomo = false; PomodoroClock.setOn(ctx, false) }
            PChip(pomo, "Pomodoro") { pomo = true; PomodoroClock.setOn(ctx, true) }
        }
        if (pomo) PomodoroPanel(skin) { askPerm() } else {
            TimerFace(skin, timerText(left), if (length > 0) left.toFloat() / length else 0f)
            if (done) ToolNote(tr("Time's up", "Süre doldu"), Look.accent)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 15, 30).forEach { m ->
                    PChip(length == m * 60_000L, tr("$m min", "$m dk")) { TimerClock.choose(ctx, m * 60_000L); sync() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (running) tr("Pause", "Duraklat") else if (left in 1 until length) tr("Resume", "Sürdür") else tr("Start", "Başlat"), Modifier.weight(1f)) {
                    if (running) TimerClock.pause(ctx) else { askPerm(); TimerClock.start(ctx) }
                    sync()
                }
                PButton(tr("Reset", "Sıfırla"), Modifier.weight(1f)) { TimerClock.reset(ctx); sync() }
            }
        }
        SkinRow(skin) { skin = it; TimerClock.setSkin(ctx, it) }
    }
}

/** The calculator's arithmetic on what the keypad types: + − × ÷ with the usual precedence, a minus in front of a number,
 *  and % ("50%" is 0.5; after + or − it is that share of the left side, so 200+10% is 220). Null when [s] is incomplete or divides by zero. */
internal object Calc {
    fun eval(s: String): Double? {
        var i = 0
        fun peek() = s.getOrNull(i)
        // a number, signed, with an optional %; the flag says it was a bare percentage
        fun factor(): Pair<Double, Boolean>? {
            if (peek() == '−') { i++; return factor()?.let { -it.first to it.second } }
            val st = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            val n = s.substring(st, i).toDoubleOrNull() ?: return null
            if (peek() == '%') { i++; return n / 100 to true }
            return n to false
        }
        fun term(): Pair<Double, Boolean>? {
            var (v, pct) = factor() ?: return null
            while (peek() == '×' || peek() == '÷') {
                val op = s[i++]
                val r = factor()?.first ?: return null
                v = if (op == '×') v * r else v / r
                pct = false
            }
            return v to pct
        }
        var v = term()?.first ?: return null
        while (peek() == '+' || peek() == '−') {
            val op = s[i++]
            val (r, pct) = term() ?: return null
            val d = if (pct) v * r else r
            v = if (op == '+') v + d else v - d
        }
        return v.takeIf { i == s.length && it.isFinite() }
    }

    /** 12 significant digits, no exponent, the keypad's minus sign. */
    fun show(v: Double): String = BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }.replace('-', '−')
}

@Composable
private fun CalculatorTool() {
    var e by rememberSaveable { mutableStateOf("") } // the typed expression
    var done by rememberSaveable { mutableStateOf(false) } // e holds a result: a digit starts over, an operator carries on
    var err by remember { mutableStateOf(false) }
    val ops = "+−×÷"
    fun num() = e.takeLastWhile { it.isDigit() || it == '.' }
    fun press(k: String) {
        err = false
        if (done && (k[0].isDigit() || k == ".")) e = ""
        done = false
        val last = e.lastOrNull()
        when {
            k == "C" -> e = ""
            k == "⌫" -> e = e.dropLast(1)
            k == "=" -> if (e.isNotEmpty()) { val v = Calc.eval(e); err = v == null; e = v?.let(Calc::show) ?: ""; done = v != null }
            e.length >= 40 -> {}
            k in ops -> e = when {
                last == null -> if (k == "−") k else e
                k == "−" && (last == '×' || last == '÷') -> e + k
                last in ops -> e.trimEnd { it in ops }.let { if (it.isEmpty()) (if (k == "−") k else "") else it + k }
                else -> e + k
            }
            k == "%" -> if (last != null && (last.isDigit() || last == '.')) e += "%"
            k == "." -> if ('.' !in num() && last != '%') e += if (num().isEmpty()) "0." else "."
            last == '%' -> {}
            num() == "0" -> e = e.dropLast(1) + k
            else -> e += k
        }
    }
    val preview = if (!done && e.drop(1).any { it in ops || it == '%' }) Calc.eval(e)?.let(Calc::show) else null
    PCard {
        FitText(if (err) tr("Error", "Hata") else e.ifEmpty { "0" }, if (err) warn else T.text, 40.sp, Modifier.fillMaxWidth().wrapContentWidth(Alignment.End))
        Text(preview?.let { "= $it" } ?: "", Modifier.fillMaxWidth(), color = T.sub, fontSize = 18.sp, textAlign = TextAlign.End, maxLines = 1)
        listOf(listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "=")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> PButton(k, Modifier.weight(if (k == "0") 2.08f else 1f), danger = k == "C") { press(k) } }
            }
        }
    }
}

@Composable
private fun ScreenScaleTool() {
    val ctx = LocalContext.current
    val dm = ctx.resources.displayMetrics
    PCard {
        Text(tr("Pulse text size", "Pulse yazı boyutu") + "  ${(Opt.fontScale * 100).roundToInt()}%", color = T.text, fontSize = 14.sp)
        PSlider(Opt.fontScale, { Opt.fontScale = it }, .8f..1.4f) { Opt.save(ctx) }
    }
    // the system-wide font size, the same steps the Quick Settings tile cycles through; the tile opens this screen when it cannot change it
    val sys = polled(1000) { TileActions.fontScale(ctx) }
    var shown by remember { mutableFloatStateOf(sys) }
    LaunchedEffect(sys) { shown = sys }
    var failed by remember { mutableStateOf(false) }
    PCard {
        Text(tr("System font size", "Sistem yazı boyutu") + "  ${(shown * 100).roundToInt()}%", color = T.text, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TileActions.scales.forEach { v ->
                PChip(abs(shown - v) < .01f, "${(v * 100).roundToInt()}%") {
                    failed = !TileActions.setFontScale(ctx, v) { ok -> failed = !ok; shown = TileActions.fontScale(ctx) }
                }
            }
        }
    }
    if (failed) AccessNeededCard(tr("System font size", "Sistem yazı boyutu"), tr("Changing it needs root or Shizuku.", "Değiştirmek için root veya Shizuku gerekir."))
    PCard {
        InfoRows(listOf(
            tr("Screen", "Ekran") to "${dm.widthPixels} × ${dm.heightPixels} px",
            "DPI" to dm.densityDpi.toString(),
            tr("Density", "Yoğunluk") to "%.2f×".format(dm.density),
        ))
    }
}

@Composable
private fun RotationTool() {
    val ctx = LocalContext.current
    val cur = polled(500) { TileActions.rotation(ctx) }
    var failed by remember { mutableStateOf(false) }
    val accent = Look.accent; val line = T.line; val sub = T.sub
    fun pick(r: Int) { failed = !TileActions.setRotation(ctx, r) { ok -> failed = !ok } }
    PCard {
        Text(tr("Tap a side: the phone turns that way. Middle: auto.", "Bir yöne dokun: telefon o yöne döner. Orta: otomatik."), color = T.text, fontSize = 14.sp)
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(220.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val dx = o.x - size.width / 2f; val dy = o.y - size.height / 2f
                    val r = when {
                        dx * dx + dy * dy < (size.width * .2f) * (size.width * .2f) -> -1
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) -> if (dx < 0) 1 else 3
                        else -> if (dy < 0) 0 else 2
                    }
                    pick(r)
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val c = Offset(w / 2, w / 2)
                val zone = w * .2f
                // the picked side is filled
                fun wedge(r: Int): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
                    when (r) {
                        0 -> { moveTo(c.x, c.y); lineTo(0f, 0f); lineTo(w, 0f) }
                        2 -> { moveTo(c.x, c.y); lineTo(0f, w); lineTo(w, w) }
                        1 -> { moveTo(c.x, c.y); lineTo(0f, 0f); lineTo(0f, w) }
                        else -> { moveTo(c.x, c.y); lineTo(w, 0f); lineTo(w, w) }
                    }
                    close()
                }
                if (cur in 0..3) drawPath(wedge(cur), accent.copy(alpha = .28f))
                drawRoundRect(line, size = Size(w, w), cornerRadius = CornerRadius(24f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawLine(line, Offset(0f, 0f), Offset(w, w), 2f); drawLine(line, Offset(w, 0f), Offset(0f, w), 2f)
                // a little phone in each side, turned the way it will look
                val pos = listOf(Offset(c.x, w * .14f), Offset(w * .14f, c.y), Offset(c.x, w * .86f), Offset(w * .86f, c.y))
                for (r in 0..3) {
                    val col = if (cur == r) accent else sub
                    rotate(-90f * r, pos[r]) {
                        drawRoundRect(col, Offset(pos[r].x - 11f, pos[r].y - 19f), Size(22f, 38f), CornerRadius(6f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                        drawLine(col, Offset(pos[r].x - 5f, pos[r].y + 13f), Offset(pos[r].x + 5f, pos[r].y + 13f), 3f)
                    }
                }
                drawCircle(T.panel, zone, c); drawCircle(if (cur < 0) accent else line, zone, c, style = androidx.compose.ui.graphics.drawscope.Stroke(if (cur < 0) 5f else 2f))
            }
            Text(tr("Auto", "Oto"), color = if (cur < 0) accent else T.text, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
    if (failed) AccessNeededCard(tr("Screen rotation", "Ekran yönü"), tr("Changing it needs root or Shizuku.", "Değiştirmek için root veya Shizuku gerekir."))
}

/** One developer-option switch (Settings.System [key]) as a Toolkit tool. */
@Composable
private fun FlagTool(key: String, text: String) {
    val ctx = LocalContext.current
    val on = polled(500) { TileActions.flagOn(ctx, key) }
    var failed by remember { mutableStateOf(false) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(on, { failed = !TileActions.toggleFlag(ctx, key) { ok -> failed = !ok } })
        }
    }
    if (failed) AccessNeededCard(text, tr("Changing it needs root or Shizuku.", "Değiştirmek için root veya Shizuku gerekir."))
}

@Composable
private fun MotionCueTool() {
    val ctx = LocalContext.current
    val on = polled(500) { com.umutk.pulse.MotionCueService.running }
    val canDraw = polled(1000) { com.umutk.pulse.Compat.canOverlay(ctx) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Dots at the screen edges that move with the car, against motion sickness", "Araba tutmasına karşı, araçla birlikte kayan kenar noktaları"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(on, { com.umutk.pulse.MotionCueService.toggle(ctx) })
        }
    }
    remember { com.umutk.pulse.MotionCueService.loadPrefs(ctx) }
    val wave = polled(500) { com.umutk.pulse.MotionCueService.wave }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Wave rising from the bottom of the screen", "Ekranın altından yükselen dalga"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(wave, { com.umutk.pulse.MotionCueService.setWave(ctx, it) })
        }
    }
    val focus = polled(500) { com.umutk.pulse.MotionCueService.focus }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Keep the middle of the screen clear (dots near the edges)", "Ekranın ortası boş kalsın (noktalar kenarlarda)"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(focus, { com.umutk.pulse.MotionCueService.setFocus(ctx, it) })
        }
    }
    if (!canDraw) AccessNeededCard(tr("Motion cues", "Hareket noktaları"), tr("Needs the permission to display over other apps.", "Diğer uygulamaların üzerinde gösterme izni gerekir."))
}

@Composable
private fun FloatingKeysTool() {
    val ctx = LocalContext.current
    val on = polled(500) { com.umutk.pulse.FloatingKeysService.running }
    val canDraw = polled(1000) { com.umutk.pulse.Compat.canOverlay(ctx) }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("A key button over every app: tap for the keyboard, hold for a small floating panel with Esc, Tab, Ctrl, arrows and the F keys (slide sideways for more)", "Her uygulamanın üstünde tuş düğmesi: dokununca klavye, basılı tutunca Esc, Tab, Ctrl, oklar ve F tuşlarının olduğu küçük yüzen panel (yana kaydırınca yenisi)"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PSwitch(on, { com.umutk.pulse.FloatingKeysService.toggle(ctx) })
        }
    }
    if (!canDraw) AccessNeededCard(tr("Floating keys", "Yüzen tuşlar"), tr("Needs the permission to display over other apps.", "Diğer uygulamaların üzerinde gösterme izni gerekir."))
    Text(tr("The keys are sent with the input command: they need root or Shizuku. Opening the keyboard needs the App Lock accessibility service.", "Tuşlar input komutuyla gönderilir: root veya Shizuku gerekir. Klavyeyi açmak için Uygulama Kilidi erişilebilirlik hizmeti gerekir."), color = T.sub, fontSize = 12.sp)
}

@Composable
private fun FinderTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanned by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var searched by remember { mutableStateOf(false) }
    val all = polled(1000) { com.umutk.pulse.Compat.isStorageManager() }
    val root = Environment.getExternalStorageDirectory()
    val limit = 200
    fun search() {
        val term = q.trim()
        if (term.isEmpty() || job != null) return
        job = scope.launch {
            hits = emptyList(); scanned = 0; searched = true
            try {
                withContext(Dispatchers.IO) {
                    val found = ArrayList<File>(); var n = 0
                    for (f in root.walkTopDown().onEnter { !it.name.startsWith(".") }) {
                        ensureActive()
                        if (!f.isFile) continue
                        if (++n % 200 == 0) scanned = n
                        if (f.name.contains(term, ignoreCase = true)) { found += f; hits = found.toList(); if (found.size >= limit) break }
                    }
                    scanned = n
                }
            } finally { job = null }
        }
    }
    if (!all) PCard {
        ToolNote(tr("Only media files are visible without All files access", "Tüm dosyalara erişim olmadan yalnızca medya dosyaları görünür"))
        PButton(tr("Allow", "İzin ver"), Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))) }
        }
    }
    PCard {
        PField(q, { q = it }, tr("File name", "Dosya adı"), Modifier.fillMaxWidth(), onDone = ::search)
        if (job != null) PButton(tr("Stop", "Durdur"), Modifier.fillMaxWidth()) { job?.cancel() }
        else PButton(tr("Search", "Ara"), Modifier.fillMaxWidth()) { search() }
        if (job != null) ToolNote(tr("Searching… $scanned files", "Aranıyor… $scanned dosya"))
        else if (searched) ToolNote(when {
            hits.isEmpty() -> tr("Nothing found in $scanned files", "$scanned dosyada bulunamadı")
            hits.size >= limit -> tr("First $limit results", "İlk $limit sonuç")
            else -> tr("${hits.size} found", "${hits.size} bulundu")
        })
    }
    if (hits.isNotEmpty()) PCard {
        hits.forEach { f ->
            Column(Modifier.fillMaxWidth().clickable { Downloads.openFile(ctx, f) }.padding(vertical = 4.dp)) {
                Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((f.parent ?: "").removePrefix(root.path).ifEmpty { "/" } + "  ·  " + android.text.format.Formatter.formatShortFileSize(ctx, f.length()),
                    color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PowerMenuTool() {
    var failed by remember { mutableStateOf(false) }
    PCard {
        PIcon("power", T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        PButton(tr("Show power menu", "Güç menüsünü göster"), Modifier.fillMaxWidth()) { failed = !TileActions.powerMenu() }
    }
    if (failed) AccessNeededCard(tr("Power menu", "Güç menüsü"), tr("Needs App lock (accessibility), root or Shizuku.", "App lock (erişilebilirlik), root veya Shizuku gerekir."))
}

/** The torch callback of the flashlight page: state changes, "camera in use" and the brightness level. */
@androidx.annotation.RequiresApi(23)
private fun torchCallback(cm: CameraManager, id: String, max: Int, onState: (Boolean) -> Unit, onBusy: () -> Unit, onLevel: (Int) -> Unit) = object : CameraManager.TorchCallback() {
    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        if (cameraId != id) return
        onState(enabled)
        if (enabled && max > 1 && Build.VERSION.SDK_INT >= 33) runCatching { onLevel(cm.getTorchStrengthLevel(id)) }
    }
    override fun onTorchModeUnavailable(cameraId: String) { if (cameraId == id) onBusy() }
    override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) { if (cameraId == id) onLevel(newStrengthLevel) }
}
