package com.umutk.pulse.features

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.umutk.pulse.PLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class UptimeInfo(
    val uptimeMs: Long,
    val bootTimeMs: Long,
    val bootCount: Int?,
    val androidVersion: String,
    val device: String,
    val ramTotal: Long,
    val ramFree: Long,
    val storageTotal: Long,
    val storageFree: Long,
    val userApps: Int,
    val systemApps: Int,
)

/** Full-screen report of uptime, boot info, OS, device, memory, storage and installed app counts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UptimeReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<UptimeInfo?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { readUptimeInfo(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uptime & device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val data = info
        if (data == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { PLoader() }
            return@Scaffold
        }

        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReportCard("Uptime") {
                    InfoRow("Time since boot", formatDuration(data.uptimeMs))
                    InfoRow("Last reboot", dateFormat.format(Date(data.bootTimeMs)))
                    InfoRow("Boot count", data.bootCount?.toString() ?: "Not available")
                }
            }
            item {
                ReportCard("System") {
                    InfoRow("Android", data.androidVersion)
                    InfoRow("Device", data.device)
                }
            }
            item {
                ReportCard("Memory") {
                    InfoRow("RAM total", formatBytes(data.ramTotal))
                    InfoRow("RAM free", formatBytes(data.ramFree))
                }
            }
            item {
                ReportCard("Storage") {
                    InfoRow("Total", formatBytes(data.storageTotal))
                    InfoRow("Free", formatBytes(data.storageFree))
                }
            }
            item {
                ReportCard("Apps") {
                    InfoRow("User apps", data.userApps.toString())
                    InfoRow("System apps", data.systemApps.toString())
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun readUptimeInfo(context: Context): UptimeInfo {
    val uptimeMs = SystemClock.elapsedRealtime()

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

    val stat = StatFs(Environment.getDataDirectory().path)

    // Hidden setting; most devices hide it from apps, so a null result is expected.
    val bootCount = try {
        Settings.Global.getInt(context.contentResolver, "boot_count", -1).takeIf { it >= 0 }
    } catch (_: Exception) {
        null
    }

    val apps = context.packageManager.getInstalledApplications(0)
    val systemApps = apps.count { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }

    return UptimeInfo(
        uptimeMs = uptimeMs,
        bootTimeMs = System.currentTimeMillis() - uptimeMs,
        bootCount = bootCount,
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
        ramTotal = mem.totalMem,
        ramFree = mem.availMem,
        storageTotal = stat.totalBytes,
        storageFree = stat.availableBytes,
        userApps = apps.size - systemApps,
        systemApps = systemApps,
    )
}

private fun formatDuration(ms: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(ms)
    val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (days > 0 || hours > 0) append("${hours}h ")
        append("${minutes}m")
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    return String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
}
