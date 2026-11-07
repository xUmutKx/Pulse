package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.umutk.pulse.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.umutk.pulse.tr
import com.umutk.pulse.Opt
import kotlinx.coroutines.delay
import java.io.File

private const val BATTERY_DIR = "/sys/class/power_supply/battery/"
private const val NOT_AVAILABLE = "not available on this device"

private data class BatteryReading(
    val health: String?,
    val cycles: Int?,
    val fullMah: Long?,
    val designMah: Long?,
    val tempC: Float?,
    val voltageV: Float?,
    val source: String?,
    val status: String?,
)

private fun readSysfs(name: String): String = try {
    File(BATTERY_DIR + name).readText().trim()
} catch (e: Exception) {
    ""
}

private fun readBattery(ctx: Context): BatteryReading {
    val bi = try {
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (e: Exception) {
        null
    }

    val health = bi?.takeIf { it.hasExtra(BatteryManager.EXTRA_HEALTH) }?.let {
        when (it.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> null
        }
    }

    val intentCycles = bi?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
    val cycles = (if (intentCycles >= 0) intentCycles else readSysfs("cycle_count").toIntOrNull())
        ?.takeIf { it >= 0 }

    // sysfs charge values are in uAh, same unit the hardware page uses.
    val fullMah = readSysfs("charge_full").toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000 }
    val designMah = readSysfs("charge_full_design").toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000 }

    val tempC = bi?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
        ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)

    val voltageV = bi?.takeIf { it.hasExtra(BatteryManager.EXTRA_VOLTAGE) }
        ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.let { if (it > 0) it / 1000f else null }

    val source = bi?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }?.let {
        when (it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Not plugged in"
            else -> "Other"
        }
    }

    val status = bi?.takeIf { it.hasExtra(BatteryManager.EXTRA_STATUS) }?.let {
        when (it.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> null
        }
    }

    return BatteryReading(health, cycles, fullMah, designMah, tempC, voltageV, source, status)
}

private fun healthNote(health: String?): String = when (health) {
    "Good" -> "The battery reports no problems."
    "Overheated" -> "The battery got too hot at some point. Let the phone cool down and avoid heavy use while charging."
    "Dead" -> "The battery reports that it can no longer hold a charge. A replacement is the usual fix."
    "Over voltage" -> "The battery reported a voltage above its safe limit. Use the original charger and cable."
    "Too cold" -> "The battery is too cold to charge well. Let the phone warm up before charging."
    "Failure" -> "The battery reports an unspecified fault. A service check is a good idea."
    else -> NOT_AVAILABLE
}

private fun cyclesNote(cycles: Int?): String =
    if (cycles == null) "Not reported by this phone."
    else "How many full charge-and-discharge cycles the battery has been through. Phones are often rated for roughly 500 to 1000 cycles."

private fun capacityNote(full: Long?, design: Long?): String =
    if (full == null || design == null) "Not reported by this phone."
    else "Full capacity is what the battery holds now. Design capacity is what it held when new. Below about 80% usually means noticeable wear."

private fun temperatureNote(c: Float?): String = when {
    c == null -> "Not reported by this phone."
    c < 35f -> "Normal range while you use the phone."
    c < 45f -> "Warm. Common while charging or gaming, but keep an eye on it."
    else -> "Hot. Let the phone cool down and stop heavy use."
}

private fun voltageNote(v: Float?): String =
    if (v == null) "Not reported by this phone."
    else "Voltage moves with the charge level. A healthy lithium cell sits roughly between 3.0 and 4.4 V."

private fun sourceNote(source: String?, status: String?): String = when {
    source == null -> "Not reported by this phone."
    status != null -> "Where power comes from right now. Battery status: $status."
    else -> "Where power comes from right now."
}

@Composable
fun BatteryHealthScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var r by remember { mutableStateOf(readBattery(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            r = readBattery(ctx)
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Battery health", style = MaterialTheme.typography.titleLarge)
        }

        Metric(
            title = "Health",
            value = r.health,
            note = healthNote(r.health),
        )
        Metric(
            title = "Charge cycles",
            value = r.cycles?.toString(),
            note = cyclesNote(r.cycles),
        )
        Metric(
            title = "Capacity",
            value = r.fullMah?.let { f -> r.designMah?.let { d -> "$f of $d mAh (${100L * f / d}%)" } },
            note = capacityNote(r.fullMah, r.designMah),
        )
        // wear worked out from what flows in and out (needs no root): grows more exact with every 10 percent charged or drained while Pulse samples
        val est = remember(r) { BatteryLog.estimate(ctx) }
        val designU = remember { BatteryLog.designUah(ctx) / 1000 }
        Metric(
            title = "Measured capacity",
            value = est?.let { (mah, n) -> if (designU > 0) "$mah of $designU mAh (${100L * mah / designU}%) · $n" else "$mah mAh · $n" },
            note = "Estimated from the charge counter while the phone charges or drains, ten percent at a time, like AccuBattery. The number after the dot is how many measurements it rests on; it becomes steadier with each full charge. Pulse has to be sampling (the monitor on) while that happens.",
        )
        // the health: worked out from the measured capacity by default, or the number the user typed in
        var manual by remember { mutableStateOf(BatteryLog.manualPct(ctx)?.toString().orEmpty()) }
        val auto = remember(r) { BatteryLog.measuredPct(ctx) }
        val shown = BatteryLog.manualPct(ctx)
        Metric(
            title = "Battery health",
            value = when {
                shown != null -> "$shown% (set by you)"
                auto != null -> "$auto% (measured)"
                else -> null
            },
            note = "Worked out from the measured capacity. Type your own figure below if you know it (for example from another app); clear it to go back to the measured one.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = manual,
                onValueChange = { v -> manual = v.filter { it.isDigit() }.take(3) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(10.dp)).padding(10.dp),
                decorationBox = { inner -> if (manual.isEmpty()) Text("Your health in %", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 14.sp); inner() },
            )
            Text(
                tr("Save", "Kaydet"), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
                modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .14f), RoundedCornerShape(10.dp)).clickable {
                    BatteryLog.setManualPct(ctx, manual.toIntOrNull()?.takeIf { it in 1..150 })
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Metric(
            title = "Temperature",
            value = r.tempC?.let { Opt.temp(it) },
            note = temperatureNote(r.tempC),
        )
        Metric(
            title = "Voltage",
            value = r.voltageV?.let { "%.3f V".format(it) },
            note = voltageNote(r.voltageV),
        )
        Metric(
            title = "Charging source",
            value = r.source,
            note = sourceNote(r.source, r.status),
        )
    }
}

@Composable
private fun Metric(title: String, value: String?, note: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            value ?: NOT_AVAILABLE,
            style = MaterialTheme.typography.titleLarge,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
