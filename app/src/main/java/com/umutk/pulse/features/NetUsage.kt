package com.umutk.pulse.features

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.umutk.pulse.AppIcon
import com.umutk.pulse.BarMeter
import com.umutk.pulse.Center
import com.umutk.pulse.Extra
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/** One app's network use for a period, in bytes. Mobile and Wi-Fi are kept apart. */
data class NetRow(val pkg: String, val label: String, val mobile: Long, val wifi: Long) {
    val total get() = mobile + wifi
}

private const val UID_TETHERING = -5
private const val UID_REMOVED = -4

private fun periodStart(month: Boolean): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    if (month) set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun bytesText(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/**
 * Sums rx+tx per uid for one network type. Needs usage access; returns empty on any failure
 * so the screen falls back to "nothing recorded" instead of crashing.
 */
private fun perUid(ctx: Context, type: Int, start: Long, end: Long): Map<Int, Long> {
    val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val sums = HashMap<Int, Long>()
    try {
        // null subscriber = all SIMs / all Wi-Fi; a real subscriber id needs a privileged permission
        val stats: NetworkStats = nsm.queryDetails(type, null, start, end)
        try {
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                sums[b.uid] = (sums[b.uid] ?: 0L) + b.rxBytes + b.txBytes
            }
        } finally { stats.close() }
    } catch (e: Exception) { }
    return sums
}

/** Per-app mobile and Wi-Fi bytes for a period, biggest total first. */
fun netUsage(ctx: Context, month: Boolean): List<NetRow> {
    if (!Extra.usageGranted(ctx)) return emptyList()
    val start = periodStart(month)
    val end = System.currentTimeMillis()
    val mobile = perUid(ctx, ConnectivityManager.TYPE_MOBILE, start, end)
    val wifi = perUid(ctx, ConnectivityManager.TYPE_WIFI, start, end)
    val pm = ctx.packageManager
    return (mobile.keys + wifi.keys).distinct().mapNotNull { uid ->
        val m = mobile[uid] ?: 0L
        val w = wifi[uid] ?: 0L
        if (m + w <= 0L) return@mapNotNull null
        val pkg = pm.getPackagesForUid(uid)?.firstOrNull().orEmpty()
        val label = when {
            uid == UID_TETHERING -> "Hotspot and tethering"
            uid == UID_REMOVED -> "Removed apps"
            pkg.isEmpty() -> "System (uid $uid)"
            else -> try { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() } catch (e: Exception) { pkg }
        }
        NetRow(pkg, label, m, w)
    }.sortedByDescending { it.total }
}

@Composable
fun NetUsageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Extra.usageGranted(ctx)) }
    var month by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<NetRow>>(emptyList()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = Extra.usageGranted(ctx) }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(granted, month) {
        while (granted) {
            rows = withContext(Dispatchers.IO) { netUsage(ctx, month) }
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = T.accent) }
            Spacer(Modifier.weight(1f))
            Text("Network usage", color = T.text, fontSize = 18.sp)
        }
        if (!granted) {
            PCard {
                Text("Showing network use per app needs the \"usage access\" permission.", color = T.text, fontSize = 14.sp)
                PButton("Allow") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return@Column
        }
        Row(Modifier.padding(bottom = 4.dp)) {
            TextButton(onClick = { month = false }) { Text("Today", color = if (!month) T.accent else T.sub) }
            TextButton(onClick = { month = true }) { Text("This month", color = if (month) T.accent else T.sub) }
        }
        if (rows.isEmpty()) Center("No network use recorded")
        else {
            val top = rows.first().total.coerceAtLeast(1L)
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.take(150), key = { "${it.pkg}|${it.label}" }) { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.pkg.isNotEmpty()) AppIcon(r.pkg, 26.dp) else Spacer(Modifier.width(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(r.label, Modifier.weight(1f), color = T.text, fontSize = 14.sp, maxLines = 1)
                            Text(bytesText(r.total), color = T.sub, fontSize = 13.sp)
                        }
                        Text(
                            "mobile ${bytesText(r.mobile)} · Wi-Fi ${bytesText(r.wifi)}",
                            Modifier.padding(start = 34.dp, top = 2.dp), color = T.sub, fontSize = 11.sp, maxLines = 1
                        )
                        BarMeter(r.total.toFloat() / top, T.accent, Modifier.padding(start = 34.dp, top = 4.dp), h = 4.dp)
                    }
                }
            }
        }
    }
}
