package com.umutk.pulse.features

import android.app.ActivityManager
import android.app.NotificationManager
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
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.Opt
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.mb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** One tile of the Toolkit: its label, icon and the screen it opens. */
private class Tool(val label: String, val icon: String, val body: @Composable () -> Unit)

/** The tool a Quick Settings tile asked for ("toolkit:<tool>"); ToolkitPage opens it and clears this. */
object ToolkitOpen { var tool by mutableStateOf("") }

private val tools = listOf(
    Tool("Compass", "compass") { Compass() },
    Tool("Level", "level") { Level() },
    Tool("Flashlight", "flash") { Flashlight() },
    Tool("Lux meter", "light") { LuxMeter() },
    Tool("Dice", "dice") { Dice() },
    Tool("Coin flip", "coin") { CoinFlip() },
    Tool("Clipboard", "clipboard") { ClipboardTool() },
    Tool("Screenshot", "screenshot") { ScreenshotTool() },
    Tool("Sensors off", "sensors") { SensorsOffTool() },
    Tool("Battery", "battery") { BatteryTool() },
    Tool("Memory", "memory") { MemoryTool() },
    Tool("Temperature", "thermal") { TemperatureTool() },
    Tool("Volume", "volume") { VolumeTool() },
    Tool("Sound mode", "vibrate") { SoundModeTool() },
    Tool("Stopwatch", "stopwatch") { StopwatchTool() },
    Tool("Timer", "timer") { TimerTool() },
    Tool("Calculator", "calc") { CalculatorTool() },
    Tool("Screen scale", "scale") { ScreenScaleTool() },
    Tool("Finder", "search") { FinderTool() },
    Tool("Power menu", "power") { PowerMenuTool() },
)

/** The Toolkit page: a 3-column grid of tiles; a tile opens its tool, the chevron goes back. */
@Composable
fun ToolkitPage() {
    var open by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(ToolkitOpen.tool) { if (ToolkitOpen.tool.isNotEmpty()) { open = tools.indexOfFirst { it.icon == ToolkitOpen.tool }; ToolkitOpen.tool = "" } }
    if (open >= 0) {
        val t = tools[open]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clickable { open = -1 }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.rotate(90f)) }
            PHead(t.label)
            t.body()
            Spacer(Modifier.height(12.dp))
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Toolkit")
        tools.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, t ->
                    PCard(Modifier.weight(1f).clip(RoundedCornerShape(T.radius)).clickable { open = r * 3 + c }) {
                        PIcon(t.icon, Look.accent, 28.dp, Modifier.align(Alignment.CenterHorizontally))
                        Text(t.label, Modifier.fillMaxWidth(), color = T.text, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToolNote(s: String) = Text(s, Modifier.fillMaxWidth(), color = T.sub, fontSize = 14.sp, textAlign = TextAlign.Center)

private fun hasSensor(ctx: Context, type: Int) = ctx.getSystemService(SensorManager::class.java).getDefaultSensor(type) != null

/** The latest reading of a sensor, null until the first one arrives. */
@Composable
private fun sensorValues(type: Int): State<FloatArray?> {
    val ctx = LocalContext.current
    val st = remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(type) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { st.value = e.values.clone() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(type)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }
    return st
}

/** The battery broadcast, read again every two seconds. */
@Composable
private fun rememberBattery(): Intent? {
    val ctx = LocalContext.current
    var b by remember { mutableStateOf<Intent?>(null) }
    LaunchedEffect(Unit) { while (true) { b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)); delay(2000) } }
    return b
}

@Composable
private fun Compass() {
    val ctx = LocalContext.current
    val v by sensorValues(Sensor.TYPE_ROTATION_VECTOR)
    if (!hasSensor(ctx, Sensor.TYPE_ROTATION_VECTOR)) { ToolNote("No compass sensor"); return }
    val vals = v
    if (vals == null) { ToolNote("Waiting for the sensor..."); return }
    val m = FloatArray(9); val o = FloatArray(3)
    SensorManager.getRotationMatrixFromVector(m, vals)
    SensorManager.getOrientation(m, o)
    val az = Math.toDegrees(o[0].toDouble()).toFloat()
    val deg = (az + 360f) % 360f
    val dir = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg + 22.5f) / 45f).toInt() % 8]
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 * .8f
            drawCircle(T.line, size.minDimension / 2, c, style = Stroke(3f))
            // the needle turns against the heading, so the red tip keeps pointing north
            rotate(-az, c) {
                drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x - r * .15f, c.y); lineTo(c.x + r * .15f, c.y); close() }, Color(0xFFE53935))
                drawPath(Path().apply { moveTo(c.x, c.y + r); lineTo(c.x - r * .15f, c.y); lineTo(c.x + r * .15f, c.y); close() }, T.text)
            }
        }
    }
    PCard {
        Text("${deg.roundToInt()}°  $dir", Modifier.fillMaxWidth(), color = T.text, fontSize = 28.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Level() {
    val ctx = LocalContext.current
    val v by sensorValues(Sensor.TYPE_ACCELEROMETER)
    if (!hasSensor(ctx, Sensor.TYPE_ACCELEROMETER)) { ToolNote("No accelerometer"); return }
    val a = v
    if (a == null) { ToolNote("Waiting for the sensor..."); return }
    val x = a[0]; val y = a[1]
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawCircle(T.line, r, c, style = Stroke(3f))
            drawCircle(T.line, r * .12f, c, style = Stroke(2f))
            // the bubble moves away from the tilt; it stops at the edge of the glass
            val k = r / 9.81f * 1.2f
            var bx = -x * k; var by = y * k
            val d = sqrt(bx * bx + by * by); val lim = r * .85f
            if (d > lim) { bx *= lim / d; by *= lim / d }
            drawCircle(Look.accent, 18.dp.toPx(), Offset(c.x + bx, c.y + by))
        }
    }
    PCard {
        val tilt = sqrt(x * x + y * y)
        ToolNote(if (tilt < .3f) "Level" else "Tilted")
        InfoRows(listOf("X" to "%.2f m/s²".format(x), "Y" to "%.2f m/s²".format(y)))
    }
}

@Composable
private fun Flashlight() {
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = remember { runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(id) { onDispose { if (id != null) runCatching { cm.setTorchMode(id, false) } } }
    if (id == null) { PCard { ToolNote("No flash on this phone") }; return }
    PCard {
        PIcon("flash", if (on) Look.accent else T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        PButton(if (on) "Turn off" else "Turn on") { on = !on; runCatching { cm.setTorchMode(id, on) } }
    }
}

@Composable
private fun LuxMeter() {
    val ctx = LocalContext.current
    val v by sensorValues(Sensor.TYPE_LIGHT)
    if (!hasSensor(ctx, Sensor.TYPE_LIGHT)) { PCard { ToolNote("No light sensor") }; return }
    val lx = v?.get(0) ?: 0f
    PCard {
        Text("%.0f lx".format(lx), Modifier.fillMaxWidth(), color = T.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        BarMeter((lx / 1000f).coerceIn(0f, 1f), Look.accent)
        ToolNote(when { lx < 10f -> "Dark"; lx < 300f -> "Indoor"; else -> "Bright" })
    }
}

@Composable
private fun Dice() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var face by remember { mutableIntStateOf(1) }
    var rolling by remember { mutableStateOf(false) }
    PCard {
        Box(Modifier.align(Alignment.CenterHorizontally).size(150.dp).background(T.panel, RoundedCornerShape(T.radius)).border(1.dp, T.line, RoundedCornerShape(T.radius)), contentAlignment = Alignment.Center) {
            Text("$face", color = T.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
        PButton("Roll") { if (!rolling) scope.launch { rolling = true; repeat(12) { face = Random.nextInt(1, 7); delay(60) }; ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putInt("dice_last", face).apply(); rolling = false } }
    }
}

@Composable
private fun CoinFlip() {
    val ctx = LocalContext.current
    var turns by remember { mutableIntStateOf(0) }
    val rot by animateFloatAsState(turns * 180f, tween(700))
    PCard {
        Box(Modifier.align(Alignment.CenterHorizontally).size(160.dp).graphicsLayer { rotationY = rot }.background(Look.accent, CircleShape))
        ToolNote(if (turns % 2 == 0) "Heads" else "Tails")
        // an odd number of half-turns always lands on the other side
        PButton("Flip") { turns += 2 * Random.nextInt(3, 6) + 1; ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("coin_last", if (turns % 2 == 0) "Heads" else "Tails").apply() }
    }
}

@Composable
private fun ClipboardTool() {
    val ctx = LocalContext.current
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    var shown by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    fun read() { shown = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty() }
    LaunchedEffect(Unit) { read() }
    PCard {
        Text(if (shown.isEmpty()) "Clipboard is empty" else shown, color = T.text, fontSize = 14.sp)
        PButton("Refresh") { read() }
    }
    PCard {
        OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Text to copy") })
        PButton("Copy") { if (text.isNotEmpty()) { cm.setPrimaryClip(ClipData.newPlainText("Pulse", text)); read() } }
    }
}

@Composable
private fun ScreenshotTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    if (Shell.root != true) { PCard { ToolNote("needs root") }; return }
    PCard {
        PButton("Take screenshot") {
            scope.launch {
                msg = withContext(Dispatchers.IO) {
                    val f = "/sdcard/Pictures/Screenshots/pulse_${System.currentTimeMillis()}.png"
                    Shell.run("mkdir -p /sdcard/Pictures/Screenshots && screencap -p $f")
                    if (File(f).exists()) { ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putLong("shot_last", System.currentTimeMillis()).apply(); "Saved $f" } else "Screenshot failed"
                }
            }
        }
        if (msg.isNotEmpty()) ToolNote(msg)
    }
}

@Composable
private fun SensorsOffTool() {
    PCard {
        PIcon("sensors", T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        ToolNote(if (Shell.root == true) "Root is on. No sensor switch is set up." else "needs root")
    }
}

@Composable
private fun BatteryTool() {
    val i = rememberBattery()
    if (i == null) { PCard { ToolNote("Reading...") }; return }
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> "Unknown"
    }
    PCard {
        Text("${level * 100 / scale}%", Modifier.fillMaxWidth(), color = T.text, fontSize = 40.sp, textAlign = TextAlign.Center)
        BarMeter((level.toFloat() / scale).coerceIn(0f, 1f), Look.accent)
        InfoRows(listOf(
            "Status" to status,
            "Temperature" to Opt.temp(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f),
            "Voltage" to "${i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)} mV",
        ))
    }
}

@Composable
private fun MemoryTool() {
    val ctx = LocalContext.current
    var mi by remember { mutableStateOf<ActivityManager.MemoryInfo?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            delay(2000)
        }
    }
    val m = mi
    if (m == null) { PCard { ToolNote("Reading...") }; return }
    val used = m.totalMem - m.availMem
    PCard {
        Text("${used * 100 / m.totalMem}% used", Modifier.fillMaxWidth(), color = T.text, fontSize = 28.sp, textAlign = TextAlign.Center)
        BarMeter(used.toFloat() / m.totalMem, Look.accent)
        InfoRows(listOf("Total" to mb((m.totalMem shr 20).toInt()), "Used" to mb((used shr 20).toInt()), "Available" to mb((m.availMem shr 20).toInt())))
    }
}

@Composable
private fun TemperatureTool() {
    val i = rememberBattery()
    if (i == null) { PCard { ToolNote("Reading...") }; return }
    val c = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
    PCard {
        Text(Opt.temp(c), Modifier.fillMaxWidth(), color = T.text, fontSize = 40.sp, textAlign = TextAlign.Center)
        ToolNote("Battery temperature")
    }
}

@Composable
private fun VolumeTool() {
    val am = LocalContext.current.getSystemService(AudioManager::class.java)
    val streams = listOf("Music" to AudioManager.STREAM_MUSIC, "Ring" to AudioManager.STREAM_RING, "Alarm" to AudioManager.STREAM_ALARM,
        "Notification" to AudioManager.STREAM_NOTIFICATION, "Call" to AudioManager.STREAM_VOICE_CALL, "System" to AudioManager.STREAM_SYSTEM)
    PCard {
        streams.forEach { (name, s) ->
            val mx = am.getStreamMaxVolume(s).toFloat().coerceAtLeast(1f)
            var v by remember(s) { mutableFloatStateOf(am.getStreamVolume(s).toFloat()) }
            Text("$name ${v.roundToInt()} / ${mx.toInt()}", color = T.text, fontSize = 13.sp)
            PSlider(v, { nv -> v = nv; am.setStreamVolume(s, nv.roundToInt(), 0) }, 0f..mx, {})
        }
    }
}

@Composable
private fun SoundModeTool() {
    val ctx = LocalContext.current
    val am = ctx.getSystemService(AudioManager::class.java)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    var mode by remember { mutableIntStateOf(am.ringerMode) }
    if (!nm.isNotificationPolicyAccessGranted) {
        PCard {
            ToolNote("Needs notification access")
            PButton("Open access settings") { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        }
        return
    }
    PCard {
        listOf(AudioManager.RINGER_MODE_NORMAL to "Normal", AudioManager.RINGER_MODE_VIBRATE to "Vibrate", AudioManager.RINGER_MODE_SILENT to "Silent").forEach { (m, label) ->
            Row(Modifier.fillMaxWidth().clickable { mode = m; am.setRingerMode(m) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == m, onClick = null)
                Text(label, color = T.text, fontSize = 14.sp)
            }
        }
    }
}

private fun clock(ms: Long): String { val t = ms / 10; return "%02d:%02d.%02d".format(t / 6000, t / 100 % 60, t % 100) }

@Composable
private fun StopwatchTool() {
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        val start = System.currentTimeMillis() - elapsed
        while (running) { elapsed = System.currentTimeMillis() - start; delay(50) }
    }
    PCard {
        Text(clock(elapsed), Modifier.fillMaxWidth(), color = T.text, fontSize = 40.sp, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(if (running) "Stop" else "Start", Modifier.weight(1f)) { running = !running }
            PButton("Reset", Modifier.weight(1f)) { running = false; elapsed = 0L }
        }
    }
}

@Composable
private fun TimerTool() {
    var set by remember { mutableIntStateOf(60) }
    var left by remember { mutableIntStateOf(60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) { while (running && left > 0) { delay(1000); left-- }; running = false }
    PCard {
        Text("%02d:%02d".format(left / 60, left % 60), Modifier.fillMaxWidth(), color = T.text, fontSize = 40.sp, textAlign = TextAlign.Center)
        if (left == 0) ToolNote("Done")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 300, 600).forEach { s -> PButton("${s / 60} min", Modifier.weight(1f)) { set = s; left = s; running = false } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(if (running) "Pause" else "Start", Modifier.weight(1f)) { if (left > 0) running = !running }
            PButton("Reset", Modifier.weight(1f)) { running = false; left = set }
        }
    }
}

@Composable
private fun CalculatorTool() {
    var cur by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<Char?>(null) }
    var fresh by remember { mutableStateOf(false) }
    fun calc(a: Double, b: Double, o: Char) = when (o) { '+' -> a + b; '-' -> a - b; '*' -> a * b; else -> a / b }
    fun show(v: Double) = if (v.isNaN() || v.isInfinite()) "Error" else if (v % 1.0 == 0.0) v.toLong().toString() else "%.8f".format(v).trimEnd('0').trimEnd('.')
    fun press(k: String) {
        when (k) {
            "C" -> { cur = "0"; acc = null; op = null; fresh = false }
            "=" -> { val o = op; if (o != null) { cur = show(calc(acc ?: 0.0, cur.toDoubleOrNull() ?: 0.0, o)); acc = null; op = null; fresh = true } }
            "+", "-", "*", "/" -> {
                val v = cur.toDoubleOrNull() ?: 0.0
                val a = acc; val o = op
                val r = if (a == null) v else if (o != null && !fresh) calc(a, v, o) else a
                acc = r; op = k[0]; cur = show(r); fresh = true
            }
            else -> {
                if (fresh || cur == "Error") { cur = "0"; fresh = false }
                cur = when { k == "." && "." in cur -> cur; cur == "0" && k != "." -> k; cur.length >= 12 -> cur; else -> cur + k }
            }
        }
    }
    PCard {
        Text(cur, Modifier.fillMaxWidth(), color = T.text, fontSize = 36.sp, textAlign = TextAlign.End, maxLines = 1)
        listOf(listOf("7", "8", "9", "/"), listOf("4", "5", "6", "*"), listOf("1", "2", "3", "-"), listOf("0", ".", "=", "+")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> PButton(k, Modifier.weight(1f)) { press(k) } }
            }
        }
        PButton("C", Modifier.fillMaxWidth()) { press("C") }
    }
}

@Composable
private fun ScreenScaleTool() {
    val ctx = LocalContext.current
    val dm = ctx.resources.displayMetrics
    PCard {
        Text("Text scale ${(Opt.fontScale * 100).toInt()}%", color = T.text, fontSize = 14.sp)
        PSlider(Opt.fontScale, { Opt.fontScale = it }, .8f..1.4f) { Opt.save(ctx) }
        InfoRows(listOf("Density" to "%.2f".format(dm.density), "DPI" to dm.densityDpi.toString(), "Width" to "${dm.widthPixels} px"))
    }
}

@Composable
private fun FinderTool() {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    PCard {
        OutlinedTextField(value = q, onValueChange = { q = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("File name") })
        PButton("Search") {
            val term = q.trim()
            if (term.isNotEmpty() && !busy) {
                busy = true
                scope.launch {
                    // the walk stops after 100 hits, so a common name does not scan the whole phone
                    hits = withContext(Dispatchers.IO) {
                        Environment.getExternalStorageDirectory().walkTopDown().onEnter { !it.name.startsWith(".") }
                            .filter { it.isFile && it.name.contains(term, ignoreCase = true) }.take(100).map { it.absolutePath }.toList()
                    }
                    busy = false
                }
            }
        }
        ToolNote(if (busy) "Searching..." else "${hits.size} found (max 100)")
        hits.forEach { Text(it, color = T.sub, fontSize = 12.sp) }
    }
}

@Composable
private fun PowerMenuTool() {
    PCard {
        PIcon("power", T.sub, 48.dp, Modifier.align(Alignment.CenterHorizontally))
        ToolNote("Not available")
    }
}
