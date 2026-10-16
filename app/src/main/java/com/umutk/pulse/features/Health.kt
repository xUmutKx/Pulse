package com.umutk.pulse.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.umutk.pulse.InfoRows
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PSwitch
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.time.LocalDate

// sensors Android exposes by name (vendor SpO2 sensors, an Arable sensor); shown only once they send a value
private val EXTRA_SENSOR = Regex("spo2|oxygen|arable", RegexOption.IGNORE_CASE)

/** Steps, distance and calories from the step counter, a heart rate from the camera flash, an activity timer and location. Estimates only. */
@Composable
fun HealthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor = remember { sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    val extraSensors = remember { sm.getSensorList(Sensor.TYPE_ALL).filter { EXTRA_SENSOR.containsMatchIn(it.name) } }
    val prefs = remember { ctx.getSharedPreferences("health", Context.MODE_PRIVATE) }
    val flash = remember { hasFlash(ctx) }
    val hasLoc = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) }
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var today by remember { mutableStateOf<Long?>(null) }
    var sinceBoot by remember { mutableStateOf<Long?>(null) }
    var weightTxt by remember { mutableStateOf(prefs.getFloat("weight", 70f).toString()) }
    var heightTxt by remember { mutableStateOf(prefs.getFloat("height", 0f).takeIf { it > 0f }?.toString() ?: "") }
    var bpm by remember { mutableStateOf(prefs.getInt("bpm", 0)) }
    var hrProgress by remember { mutableFloatStateOf(-1f) } // -1 = not measuring
    var hrMsg by remember { mutableStateOf<String?>(null) }
    var meter by remember { mutableStateOf<HeartMeter?>(null) }
    var camOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    // activity session: start time (0 = not running) and the last length, kept in prefs so a run survives leaving the page
    var actStart by remember { mutableLongStateOf(prefs.getLong("act_start", 0L)) }
    var actLast by remember { mutableLongStateOf(prefs.getLong("act_last", 0L)) }
    var actType by remember { mutableStateOf(ActivityType.fromName(prefs.getString("act_type", null))) }
    var actDist by remember { mutableFloatStateOf(prefs.getFloat("act_dist", 0f)) }
    var finished by remember { mutableStateOf<ActRecord?>(null) }
    var log by remember { mutableStateOf(loadActLog(prefs)) }
    var tab by remember { mutableStateOf(0) }
    var rev by remember { mutableStateOf(0) } // bumped when goals or body numbers change
    var showGoals by remember { mutableStateOf(false) }
    val goalSteps = remember(rev) { HealthStore.goalSteps(prefs) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locOn by remember { mutableStateOf(prefs.getBoolean("loc_on", false) && actStart > 0L) }
    var fineOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var lastLoc by remember { mutableStateOf<Location?>(null) }
    val extra = remember { mutableStateMapOf<String, String>() }

    val weight = weightTxt.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..300f } ?: 70f
    val height = heightTxt.replace(',', '.').toFloatOrNull()?.takeIf { it in 100f..250f } ?: 0f
    // stride from height (cm), default 0.75 m
    val strideM = if (height > 0f) height * 0.415f / 100f else 0.75f

    // The counter is cumulative since boot; the day's steps are the counter minus the value at the day's baseline.
    DisposableEffect(stepSensor, granted) {
        if (stepSensor == null || !granted) return@DisposableEffect onDispose { }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val total = e.values[0].toLong()
                today = HealthStore.onCounter(prefs, total)
                sinceBoot = total
                HealthStore.push(ctx)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(l, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l) }
    }

    // the screen can stay open past midnight without a new step, so the number is reset here
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            if (prefs.getString("day", "") != LocalDate.now().toString() && today != null) today = 0L
        }
    }

    // oxygen / Arable values: only sensors that actually report
    DisposableEffect(Unit) {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.isNotEmpty()) extra[e.sensor.name] = e.values.joinToString(" ") { "%.1f".format(it) }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        extraSensors.forEach { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm.unregisterListener(l) }
    }

    // activity timer ticks once a second while a session runs
    LaunchedEffect(actStart) {
        while (actStart > 0L) { nowMs = System.currentTimeMillis(); delay(1000) }
    }

    fun setLoc(on: Boolean) { locOn = on; prefs.edit().putBoolean("loc_on", on).apply() }
    fun startAct() {
        actStart = System.currentTimeMillis(); nowMs = actStart; actDist = 0f; finished = null; prefs.edit().putLong("act_start", actStart).putFloat("act_dist", 0f).apply()
        setLoc(actType.gps && fineOk)
    }
    // location is asked only when a GPS activity starts; if it is refused the activity still runs, without a track
    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { fineOk = it; startAct() }
    fun toggleActivity() {
        if (actStart == 0L) {
            if (actType.gps && hasLoc && !fineOk) askLoc.launch(Manifest.permission.ACCESS_FINE_LOCATION) else startAct()
        } else {
            actLast = System.currentTimeMillis() - actStart
            ActRecord(actType.key, actStart, actLast, actDist).also { addActRecord(prefs, it); finished = it; log = loadActLog(prefs) }
            actStart = 0L
            HealthStore.push(ctx)
            prefs.edit().putLong("act_start", 0L).putLong("act_last", actLast).apply()
            setLoc(false)
        }
    }
    // GPS and network providers only (platform API, no Play services); updates run while the page is open
    DisposableEffect(locOn, fineOk) {
        if (!locOn || !fineOk) return@DisposableEffect onDispose { }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var prev: Location? = null // distance counts only fixes seen in this session
        val l = LocationListener { n ->
            lastLoc = n
            if (n.accuracy < 50f) { prev?.let { actDist += it.distanceTo(n) }; prev = n; prefs.edit().putFloat("act_dist", actDist).apply() }
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
        lastLoc = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        providers.forEach { lm.requestLocationUpdates(it, 2000L, 0f, l, Looper.getMainLooper()) }
        onDispose { lm.removeUpdates(l) }
    }

    fun startHeart() {
        hrMsg = null
        hrProgress = 0f
        meter = HeartMeter(ctx, { hrProgress = it }) { r ->
            meter = null
            hrProgress = -1f
            if (r != null) { bpm = r; HealthStore.addHr(prefs, r); HealthStore.push(ctx) }
            else hrMsg = "No clear pulse."
        }.also { it.start() }
    }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camOk = it; if (it) startHeart() }
    DisposableEffect(Unit) { onDispose { meter?.cancel() } }

    androidx.activity.compose.BackHandler(tab == 1) { tab = 0 }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (tab == 1) tab = 0 else onBack() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = T.text) }
            Text(if (tab == 1) tr("New activity", "Yeni etkinlik") else "Health", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        // summary bar: steps against the daily goal, heart rate beside it
        PCard {
            val steps = today ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.DirectionsWalk, null, Modifier.size(18.dp), tint = Look.accent)
                Text("%,d / %,d".format(steps, goalSteps), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp), tint = Look.accent)
                Text(if (bpm > 0) "$bpm" else "-", color = T.text, fontSize = 15.sp)
            }
            LinearProgressIndicator(progress = { (steps / goalSteps.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (tab == 0) HealthOverview(prefs, log, today ?: HealthStore.today(prefs), bpm, rev) { showGoals = true } else {
        // no step counter on the device: the card is not shown at all
        if (stepSensor != null) PCard {
            Text("Steps today", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val t = today
            when {
                !granted -> {
                    Text("Pulse needs permission to read your steps.", color = T.sub, fontSize = 13.sp)
                    PButton("Allow step counting") { ask.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                }
                t == null -> Text("Waiting for the first reading...", color = T.sub, fontSize = 13.sp)
                else -> {
                    Text("%,d".format(t), color = T.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    Text("%,d steps since boot".format(sinceBoot ?: 0L), color = T.sub, fontSize = 12.sp)
                    InfoRows(listOf(
                        "Distance" to "%.2f km".format(t * strideM / 1000f),
                        "Calories" to "%d kcal (estimate)".format((t * 0.04f * weight / 70f).toInt()),
                        "Stride" to if (height > 0f) "%d cm, from your height".format((strideM * 100f).toInt()) else "75 cm default",
                    ))
                }
            }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Timer, null, Modifier.size(18.dp), tint = Look.accent)
                Text("Activity", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            val running = actStart > 0L
            ActivityPicker(selected = actType, enabled = !running) { a ->
                actType = a
                prefs.edit().putString("act_type", a.name).apply()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityIcon(actType.key, T.sub, 24.dp)
                Text(actType.label, color = T.sub, fontSize = 13.sp)
            }
            val secs = (if (running) nowMs - actStart else actLast) / 1000
            Text("%02d:%02d:%02d".format(secs / 3600, secs % 3600 / 60, secs % 60), color = T.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
            if (running && locOn) Text(fmtDist(actDist), color = T.sub, fontSize = 13.sp)
            PButton(if (running) "Stop" else "Start", danger = running, icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow) { toggleActivity() }
        }
        finished?.let { if (actStart == 0L) FinishCard(it) }
        // no location hardware on the device: the card is not shown at all
        if (hasLoc && locOn) PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp), tint = Look.accent)
                Text(tr("Location", "Konum"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            if (fineOk) {
                val l = lastLoc
                if (l != null) Text("%.5f, %.5f   ±%d m".format(l.latitude, l.longitude, l.accuracy.toInt()), color = T.text, fontSize = 13.sp)
                else Text("No fix yet.", color = T.sub, fontSize = 13.sp)
            }
        }
        // no camera flash on the device: heart rate needs it, so the card is not shown at all
        if (flash) PCard {
            Text("Heart rate", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            when {
                hrProgress >= 0f -> {
                    Text("Measuring... %d%%".format((hrProgress * 100).toInt()), color = T.text, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { hrProgress }, modifier = Modifier.fillMaxWidth())
                    PButton("Stop") { meter?.cancel(); meter = null; hrProgress = -1f }
                }
                else -> {
                    if (bpm > 0) Text("%d BPM".format(bpm), color = T.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    else Text("No reading yet.", color = T.sub, fontSize = 13.sp)
                    hrMsg?.let { Text(it, color = T.sub, fontSize = 13.sp) }
                    PButton(if (camOk) "Measure (15 s)" else "Allow camera and measure") { if (camOk) startHeart() else askCam.launch(Manifest.permission.CAMERA) }
                    Text("Estimate, not a medical measurement.", color = T.sub, fontSize = 12.sp)
                }
            }
        }
        if (extra.isNotEmpty()) PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Air, null, Modifier.size(18.dp), tint = Look.accent)
                Text("Oxygen and sensors", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            InfoRows(extra.toList())
        }
        PCard {
            Text("Body", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumBox("Weight (kg)", weightTxt, Modifier.weight(1f)) { weightTxt = it; it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 20f..300f }?.let { v -> prefs.edit().putFloat("weight", v).apply(); rev++ } }
                NumBox("Height (cm)", heightTxt, Modifier.weight(1f)) { heightTxt = it; it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 100f..250f }?.let { v -> prefs.edit().putFloat("height", v).apply(); rev++ } }
            }
            Text("Used for distance and calories.", color = T.sub, fontSize = 12.sp)
        }
        }
    }
    if (showGoals) GoalsDialog(prefs, { showGoals = false }) { rev++ }
    if (tab == 0) {
        val fab = Look.accent
        androidx.compose.foundation.layout.Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp)
            .background(fab, androidx.compose.foundation.shape.CircleShape).clickable { tab = 1 }, contentAlignment = Alignment.Center) {
            Icon(androidx.compose.material.icons.Icons.Filled.Add, tr("New activity", "Yeni etkinlik"), Modifier.size(26.dp), tint = com.umutk.pulse.onAccent(fab))
        }
    }
    }
}

@Composable
private fun NumBox(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, modifier = modifier, singleLine = true,
        label = { Text(label, fontSize = 12.sp) }, textStyle = TextStyle(color = T.text, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
