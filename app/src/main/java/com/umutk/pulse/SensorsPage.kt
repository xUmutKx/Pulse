package com.umutk.pulse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun unitOf(type: Int) = when (type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
    Sensor.TYPE_GYROSCOPE -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD -> "µT"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    else -> ""
}

private fun valuesOf(e: FloatArray, type: Int): String {
    val n = when (type) {
        Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY, Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY, Sensor.TYPE_STEP_COUNTER -> 1
        else -> minOf(3, e.size)
    }
    return (0 until n).joinToString("   ") { "%.2f".format(e[it]) } + unitOf(type).let { if (it.isEmpty()) "" else " $it" }
}

/** Every sensor of the phone with its live reading, vendor and power draw. */
@Composable
fun SensorsPage() {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensors = remember { sm.getSensorList(Sensor.TYPE_ALL).filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT && it.reportingMode != Sensor.REPORTING_MODE_SPECIAL_TRIGGER } }
    val live = remember { mutableStateMapOf<String, String>() }
    DisposableEffect(Unit) {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { live[e.sensor.name] = valuesOf(e.values, e.sensor.type) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensors.forEach { try { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) } catch (_: Exception) { } }
        onDispose { sm.unregisterListener(l) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            Text("Sensors", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${sensors.size} sensors", color = T.sub, fontSize = 12.sp)
        }
        sensors.forEach { s ->
            PCard {
                Text(s.name, color = Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(live[s.name] ?: "waiting for data…", color = T.text, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                Text("${s.vendor} · ${"%.2f".format(s.power)} mA · max ${"%.1f".format(s.maximumRange)} ${unitOf(s.type)}", color = T.sub, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
