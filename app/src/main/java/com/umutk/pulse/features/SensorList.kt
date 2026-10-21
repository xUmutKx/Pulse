package com.umutk.pulse.features

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.T

/** How many sensors can stream live values at the same time. */
private const val MaxLive = 3

private fun sensorUnit(type: Int) = when (type) {
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

private fun sensorTypeName(s: Sensor): String =
    (s.stringType ?: "").substringAfterLast('.').ifEmpty { "type ${s.type}" }

private fun formatReading(values: FloatArray, type: Int): String {
    val unit = sensorUnit(type)
    return values.take(3).joinToString("   ") { "%.2f".format(it) } + if (unit.isEmpty()) "" else " $unit"
}

/**
 * Every sensor the device reports. Tap a row to start its live value, tap again to stop.
 * At most [MaxLive] sensors stream at once; taps beyond that are ignored until one stops.
 */
// Sensor.getHandle() is hidden in compileSdk 34; a type/vendor/name hash keeps the keys unique enough for a list of one phone's sensors.
private val Sensor.handle: Int get() = type * 31 * 31 + vendor.hashCode() * 31 + name.hashCode()

@Composable
fun SensorListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensors = remember { sm.getSensorList(Sensor.TYPE_ALL) }
    val byHandle = remember { sensors.associateBy { it.handle } }
    val live = remember { mutableStateListOf<Int>() }
    val values = remember { mutableStateMapOf<Int, String>() }

    // Re-keyed whenever the set of live sensors changes; onDispose unregisters the listener.
    DisposableEffect(live.toList()) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                values[e.sensor.handle] = formatReading(e.values, e.sensor.type)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        live.forEach { handle ->
            byHandle[handle]?.let { s ->
                try {
                    sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
                } catch (_: Exception) {
                }
            }
        }
        onDispose { sm.unregisterListener(listener) }
    }

    fun toggle(s: Sensor) {
        if (s.handle in live) {
            live.remove(s.handle)
            values.remove(s.handle)
        } else if (live.size < MaxLive) {
            live.add(s.handle)
        }
    }

    BackHandler { onBack() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PButton("Back") { onBack() }
            PHead("Sensors", "${sensors.size} sensors · ${live.size}/$MaxLive live")
        }
        if (sensors.isEmpty()) {
            item { Text("No sensors on this device", color = T.sub, fontSize = 13.sp) }
        }
        items(sensors, key = { it.handle }) { s ->
            val on = s.handle in live
            PCard(Modifier.clickable { toggle(s) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.name, color = if (on) Look.accent else T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${sensorTypeName(s)} · ${s.vendor}", color = T.sub, fontSize = 11.sp)
                        Text("max ${"%.2f".format(s.maximumRange)} ${sensorUnit(s.type)}".trimEnd(), color = T.sub, fontSize = 11.sp)
                    }
                    Icon(
                        if (on) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        null,
                        Modifier.size(22.dp),
                        tint = Look.accent,
                    )
                }
                if (on) {
                    Text(values[s.handle] ?: "waiting for data…", color = T.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
