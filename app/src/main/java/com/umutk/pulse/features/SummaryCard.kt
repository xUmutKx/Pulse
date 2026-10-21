package com.umutk.pulse.features

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.umutk.pulse.Hw
import com.umutk.pulse.Opt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import com.umutk.pulse.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 5_000L

private data class SummarySnapshot(
    val freeStorageBytes: Long?,
    val freeRamBytes: Long?,
    val batteryPercent: Int?,
    val tempCelsius: Float?,
)

private fun readSummary(appContext: Context): SummarySnapshot {
    val freeStorage = runCatching {
        StatFs(Environment.getDataDirectory().path).availableBytes
    }.getOrNull()

    val freeRam = runCatching {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    }.getOrNull()

    val battery = runCatching {
        appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
    val batteryTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }

    // Prefer the SoC zone reading from sysfs; fall back to the battery sensor.
    val zoneTemp = runCatching { Hw.maxTemp() }.getOrNull()?.takeIf { it > 0f }

    return SummarySnapshot(
        freeStorageBytes = freeStorage,
        freeRamBytes = freeRam,
        batteryPercent = batteryPercent,
        tempCelsius = zoneTemp ?: batteryTemp,
    )
}

private fun formatGb(bytes: Long?): String {
    if (bytes == null) return "—"
    val gb = bytes / 1073741824.0
    return if (gb < 10) "%.1f GB".format(gb) else "%.0f GB".format(gb)
}

/**
 * Compact home-screen summary: free storage, free RAM, battery level and temperature.
 * Values are read on Dispatchers.IO and refreshed every 5 seconds while composed.
 */
@Composable
fun SummaryCard(
    onStorage: () -> Unit,
    onRam: () -> Unit,
    onBattery: () -> Unit,
    onTemp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val snapshot by produceState<SummarySnapshot?>(initialValue = null, appContext) {
        while (true) {
            value = withContext(Dispatchers.IO) { readSummary(appContext) }
            delay(REFRESH_MS)
        }
    }
    val snap = snapshot

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile(
            icon = Icons.Filled.Storage,
            value = formatGb(snap?.freeStorageBytes),
            label = "Free storage",
            onClick = onStorage,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            icon = Icons.Filled.Memory,
            value = formatGb(snap?.freeRamBytes),
            label = "Free RAM",
            onClick = onRam,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            icon = Icons.Filled.BatteryFull,
            value = snap?.batteryPercent?.let { "$it%" } ?: "—",
            label = "Battery",
            onClick = onBattery,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            icon = Icons.Filled.Thermostat,
            value = snap?.tempCelsius?.let { Opt.temp(it) } ?: "—",
            label = "Temp",
            onClick = onTemp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTile(
    icon: ImageVector,
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.width(22.dp).height(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
