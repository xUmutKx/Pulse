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
import android.content.SharedPreferences
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import com.umutk.pulse.Graph
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.mb
import com.umutk.pulse.resColor
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
    if (android.os.Build.VERSION.SDK_INT < 23) return emptyMap()
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
        val top = rows.firstOrNull()?.total?.coerceAtLeast(1L) ?: 1L
        LazyColumn(Modifier.fillMaxSize()) {
            item { NetworksSection() }
            if (rows.isEmpty()) item { Center("No network use recorded") }
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

// ---------------------------------------------------------------- networks section (Network page)

private const val PREFS = "netuse"
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/** Bytes received and sent per network: last 24 hours and 30 days, plus hourly (24) and daily (30) series. */
private data class NetTotal(
    val name: String, val day: Pair<Long, Long>, val month: Pair<Long, Long>,
    val hourly: List<Pair<Long, Long>>, val daily: List<Pair<Long, Long>>
)

/** One app's bytes on the active network over the last 30 days. */
private data class NetApp(val label: String, val bytes: Long)

private class NetScan(val denied: Boolean, val active: String?, val totals: List<NetTotal>, val apps: List<NetApp>)

/** Type and name of the active network: the Wi-Fi SSID ("Wi-Fi" when it cannot be read) or "Mobile data". */
private fun activeNet(ctx: Context): Pair<Int, String>? {
    if (android.os.Build.VERSION.SDK_INT < 23) return null
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return null) ?: return null
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            @Suppress("DEPRECATION")
            val ssid = (ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid
            ConnectivityManager.TYPE_WIFI to (ssid?.trim('"')?.takeUnless { it.isEmpty() || it == "<unknown ssid>" } ?: "Wi-Fi")
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityManager.TYPE_MOBILE to "Mobile data"
        else -> null
    }
}

/** rx and tx bytes of one network type since [start]; null when the query is denied. */
private fun netSum(ctx: Context, type: Int, start: Long, end: Long): Pair<Long, Long>? = if (android.os.Build.VERSION.SDK_INT < 23) null else try {
    val b = (ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager).querySummaryForDevice(type, null, start, end)
    b.rxBytes to b.txBytes
} catch (e: Exception) { null }

/** rx/tx per slot of [stepMs], oldest first, ending now; null when denied. */
private fun netSeries(ctx: Context, type: Int, now: Long, slots: Int, stepMs: Long): List<Pair<Long, Long>>? {
    val out = ArrayList<Pair<Long, Long>>(slots)
    for (i in slots downTo 1) {
        val s = netSum(ctx, type, now - i * stepMs, now - (i - 1) * stepMs) ?: return null
        out += s
    }
    return out
}

/** Top 15 apps by bytes on one network type; uids come from PackageManager. */
private fun topApps(ctx: Context, type: Int, start: Long, end: Long): List<NetApp> {
    if (android.os.Build.VERSION.SDK_INT < 23) return emptyList()
    val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val pm = ctx.packageManager
    val b = NetworkStats.Bucket()
    return pm.getInstalledApplications(0).distinctBy { it.uid }.mapNotNull { ai ->
        val bytes = try {
            val s = nsm.queryDetailsForUid(type, null, start, end, ai.uid)
            var t = 0L
            try { while (s.hasNextBucket()) { s.getNextBucket(b); t += b.rxBytes + b.txBytes } } finally { s.close() }
            t
        } catch (e: Exception) { 0L }
        if (bytes <= 0L) null else NetApp(ai.loadLabel(pm).toString(), bytes)
    }.sortedByDescending { it.bytes }.take(15)
}

/** One comma-separated string per network under "v:<name>": 4 totals, then 24 hourly and 30 daily rx/tx pairs. */
private fun encodeTotal(t: NetTotal): String =
    (listOf(t.day.first, t.day.second, t.month.first, t.month.second) + (t.hourly + t.daily).flatMap { listOf(it.first, it.second) })
        .joinToString(",")

private fun decodeTotal(name: String, s: String?): NetTotal? {
    val v = s?.split(",")?.map { it.toLongOrNull() ?: 0L } ?: return null
    if (v.size != 4 + 2 * (24 + 30)) return null
    fun pairsAt(from: Int, n: Int) = (0 until n).map { v[from + 2 * it] to v[from + 2 * it + 1] }
    return NetTotal(name, v[0] to v[1], v[2] to v[3], pairsAt(4, 24), pairsAt(52, 30))
}

/** Saved totals for every network seen before; "names" holds the set of network names. */
private fun savedTotals(sp: SharedPreferences): List<NetTotal> =
    (sp.getStringSet("names", emptySet()) ?: emptySet()).sorted().mapNotNull { decodeTotal(it, sp.getString("v:$it", null)) }

private fun netScan(ctx: Context, sp: SharedPreferences): NetScan {
    val now = System.currentTimeMillis()
    val granted = Extra.usageGranted(ctx)
    val act = activeNet(ctx)
    if (!granted || act == null) return NetScan(!granted, act?.second, savedTotals(sp), emptyList())
    val (type, name) = act
    val day = netSum(ctx, type, now - DAY_MS, now)
    val month = netSum(ctx, type, now - 30 * DAY_MS, now)
    val hourly = netSeries(ctx, type, now, 24, HOUR_MS)
    val daily = netSeries(ctx, type, now, 30, DAY_MS)
    if (day == null || month == null || hourly == null || daily == null) return NetScan(true, name, savedTotals(sp), emptyList())
    sp.edit()
        .putStringSet("names", (sp.getStringSet("names", emptySet()) ?: emptySet()) + name)
        .putString("v:$name", encodeTotal(NetTotal(name, day, month, hourly, daily)))
        .apply()
    return NetScan(false, name, savedTotals(sp), topApps(ctx, type, now - 30 * DAY_MS, now))
}

@Composable
private fun NetLine(label: String, rx: Long, tx: Long) = Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), color = T.sub, fontSize = 13.sp)
    Text("received ${mb((rx shr 20).toInt())} · sent ${mb((tx shr 20).toInt())}", color = T.text, fontSize = 13.sp)
}

/** Received (orange) and sent (blue) per slot, drawn with the house Graph. */
@Composable
private fun NetGraph(title: String, data: List<Pair<Long, Long>>) {
    val max = (data.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L).coerceAtLeast(1L).toFloat() * 1.2f
    Text(title, color = T.sub, fontSize = 12.sp)
    Graph(listOf(data.map { it.first.toFloat() }, data.map { it.second.toFloat() }), listOf(resColor("net"), resColor("gpu")), max, height = 60.dp, n = data.size)
}

/** Network page card: per network, hourly and daily graphs, the 24 h and 30 day totals, then the top apps on the active network. */
@Composable
fun NetworksSection() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var scan by remember { mutableStateOf<NetScan?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            scan = withContext(Dispatchers.IO) { netScan(ctx, sp) }
            delay(60_000)
        }
    }
    val s = scan ?: return
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Networks", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (s.denied) Row(
                Modifier.clickable { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Needs usage access", color = T.sub, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                PIcon("settings", T.accent, 20.dp)
            }
        }
        if (s.totals.isEmpty() && !s.denied) Center("No network use recorded")
        s.totals.forEach { t ->
            Text(if (t.name == s.active) "${t.name} (current)" else t.name, color = T.text, fontSize = 14.sp, maxLines = 1)
            NetGraph("Last 24 hours, per hour", t.hourly)
            NetGraph("Last 30 days, per day", t.daily)
            NetLine("Last 24 hours", t.day.first, t.day.second)
            NetLine("Total, last 30 days", t.month.first, t.month.second)
        }
    }
    if (s.apps.isNotEmpty()) PCard {
        Text("Top apps on ${s.active}, last 30 days", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        val top = s.apps.first().bytes.coerceAtLeast(1L)
        s.apps.forEach { a ->
            Column(Modifier.fillMaxWidth()) {
                Row { Text(a.label, Modifier.weight(1f), color = T.text, fontSize = 13.sp, maxLines = 1); Text(mb((a.bytes shr 20).toInt()), color = T.sub, fontSize = 13.sp) }
                BarMeter(a.bytes.toFloat() / top, T.accent, h = 6.dp)
            }
        }
    }
}
