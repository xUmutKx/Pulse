package com.umutk.pulse.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.umutk.pulse.Look
import com.umutk.pulse.T
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Steps, distance and calories from the step counter, and a heart rate from the camera flash. Estimates only. */
@Composable
fun HealthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor = remember { sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    val prefs = remember { ctx.getSharedPreferences("health", Context.MODE_PRIVATE) }
    val flash = remember { hasFlash(ctx) }
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
                val day = LocalDate.now().toString()
                val prevDay = prefs.getString("day", "")
                val last = prefs.getLong("last", -1L)
                var base = prefs.getLong("base", -1L)
                // shortcut: steps before the app first reads a day are not counted; a new day starts from yesterday's last reading
                if (prevDay != day || base < 0 || total < base) {
                    base = if (prevDay == LocalDate.now().minusDays(1).toString() && last >= 0 && last <= total) last else total
                }
                prefs.edit().putString("day", day).putLong("base", base).putLong("last", total).apply()
                today = total - base
                sinceBoot = total
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

    fun startHeart() {
        hrMsg = null
        hrProgress = 0f
        meter = HeartMeter(ctx, { hrProgress = it }) { r ->
            meter = null
            hrProgress = -1f
            if (r != null) { bpm = r; prefs.edit().putInt("bpm", r).apply() }
            else hrMsg = "No clear pulse. Cover the lens and the flash fully with a fingertip, keep still, and try again."
        }.also { it.start() }
    }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camOk = it; if (it) startHeart() }
    DisposableEffect(Unit) { onDispose { meter?.cancel() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = T.text) }
            Text("Health", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        PCard {
            Text("Steps today", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val t = today
            when {
                stepSensor == null -> Text("This phone has no step counter sensor.", color = T.sub, fontSize = 13.sp)
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
                        "Stride" to if (height > 0f) "%d cm, from your height".format((strideM * 100f).toInt()) else "75 cm default, set height below",
                    ))
                }
            }
        }
        PCard {
            Text("Heart rate", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            when {
                !flash -> Text("This phone has no camera flash, so heart rate cannot be measured here.", color = T.sub, fontSize = 13.sp)
                hrProgress >= 0f -> {
                    Text("Measuring... %d%%".format((hrProgress * 100).toInt()), color = T.text, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { hrProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Keep your fingertip still over the camera and the flash.", color = T.sub, fontSize = 12.sp)
                    PButton("Stop") { meter?.cancel(); meter = null; hrProgress = -1f }
                }
                else -> {
                    if (bpm > 0) Text("%d BPM".format(bpm), color = T.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                    else Text("No reading yet.", color = T.sub, fontSize = 13.sp)
                    hrMsg?.let { Text(it, color = T.sub, fontSize = 13.sp) }
                    PButton(if (camOk) "Measure (15 s)" else "Allow camera and measure") { if (camOk) startHeart() else askCam.launch(Manifest.permission.CAMERA) }
                    Text("Put a fingertip over the camera and the flash. A rough estimate, not a medical measurement.", color = T.sub, fontSize = 12.sp)
                }
            }
        }
        PCard {
            Text("Body", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumBox("Weight (kg)", weightTxt, Modifier.weight(1f)) { weightTxt = it; it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 20f..300f }?.let { v -> prefs.edit().putFloat("weight", v).apply() } }
                NumBox("Height (cm)", heightTxt, Modifier.weight(1f)) { heightTxt = it; it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 100f..250f }?.let { v -> prefs.edit().putFloat("height", v).apply() } }
            }
            Text("Used for distance and calories. Empty height means the default stride.", color = T.sub, fontSize = 12.sp)
        }
        PCard {
            Text("Other health data", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            InfoRows(listOf(
                "Steps" to if (stepSensor != null) "Available (above)" else "Not on this phone",
                "Heart rate" to if (flash) "Camera flash, above" else "Needs a camera flash",
                "Sleep" to "Not available: no standard Android sensor",
                "Blood oxygen" to "Not available: no standard Android sensor",
                "Blood pressure" to "Not available: no standard Android sensor",
            ))
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
