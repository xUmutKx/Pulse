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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
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

/**
 * Per-app network blocking, through root (iptables rules on the app's uid, one for mobile data and one for Wi-Fi). Without root Android gives no way to cut one
 * app off by itself, so the switches say so. The rules are gone after a reboot; they are put back each time this page opens.
 */
object NetBlock {
    private fun sp(c: Context) = c.getSharedPreferences("netblock", Context.MODE_PRIVATE)
    private fun key(uid: Int, mobile: Boolean) = "$uid:" + (if (mobile) "m" else "w")
    fun isBlocked(c: Context, uid: Int, mobile: Boolean) = sp(c).getBoolean(key(uid, mobile), false)
    private fun cmd(uid: Int, mobile: Boolean, on: Boolean): String {
        val itf = if (mobile) "rmnet+" else "wlan+"
        val rule = "OUTPUT -m owner --uid-owner $uid -o $itf -j REJECT"
        return "iptables -D $rule 2>/dev/null; ip6tables -D $rule 2>/dev/null; " +
            (if (on) "iptables -I $rule; ip6tables -I $rule; " else "") +
            "iptables -C $rule 2>/dev/null && echo __ON || echo __OFF"
    }
    /** Switches one block on or off; [done] gets whether the rule really is as asked (read back from the firewall), on the main thread. */
    fun set(c: Context, uid: Int, mobile: Boolean, on: Boolean, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        Thread {
            val out = if (com.umutk.pulse.Shell.root == true) com.umutk.pulse.Shell.runRoot(cmd(uid, mobile, on)) else ""
            val ok = com.umutk.pulse.Shell.root == true && out.contains(if (on) "__ON" else "__OFF")
            if (ok) sp(app).edit().apply { if (on) putBoolean(key(uid, mobile), true) else remove(key(uid, mobile)) }.apply()
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }
    /** After a reboot the rules are gone: the saved ones are put back. */
    fun restore(c: Context) {
        if (com.umutk.pulse.Shell.root != true) return
        val keys = sp(c).all.filter { it.value == true }.keys
        if (keys.isEmpty()) return
        Thread { keys.forEach { k -> val uid = k.substringBefore(':').toIntOrNull() ?: return@forEach; com.umutk.pulse.Shell.runRoot(cmd(uid, k.endsWith(":m"), true)) } }.start()
    }
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

private val WIFI_COLOR get() = Look.accent
private val MOBILE_COLOR = Color(0xFFFFA726)

private fun dayStartAgo(daysAgo: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
}.timeInMillis

/** One day: the bytes of each hour on Wi-Fi and on mobile data, and the apps of that day. */
private class DayData(val start: Long, val hourWifi: List<Long>, val hourMobile: List<Long>, val rows: List<NetRow>)

private fun sumOf(p: Pair<Long, Long>?) = (p?.first ?: 0L) + (p?.second ?: 0L)

private fun loadDay(ctx: Context, start: Long): DayData {
    val now = System.currentTimeMillis()
    val wifi = ArrayList<Long>(24); val mobile = ArrayList<Long>(24)
    for (h in 0 until 24) {
        val s = start + h * HOUR_MS
        if (s >= now) { wifi += 0L; mobile += 0L; continue }
        val e = minOf(s + HOUR_MS, now)
        wifi += sumOf(netSum(ctx, ConnectivityManager.TYPE_WIFI, s, e))
        mobile += sumOf(netSum(ctx, ConnectivityManager.TYPE_MOBILE, s, e))
    }
    return DayData(start, wifi, mobile, netUsageRange(ctx, start, minOf(start + DAY_MS, now)))
}

/** Per-app mobile and Wi-Fi bytes between two times, biggest total first. */
private fun netUsageRange(ctx: Context, start: Long, end: Long): List<NetRow> {
    if (!Extra.usageGranted(ctx)) return emptyList()
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

/** Bars per slot, Wi-Fi and mobile data stacked (or one of them alone), drawn in the look's colours. A tap on a bar tells which one. */
@Composable
private fun StackBars(wifi: List<Long>, mobile: List<Long>, filter: Int, height: androidx.compose.ui.unit.Dp, selected: Int = -1, onTap: ((Int) -> Unit)? = null) {
    val n = wifi.size.coerceAtLeast(1)
    val shown = List(n) { i -> (if (filter != 2) wifi.getOrElse(i) { 0L } else 0L) + (if (filter != 1) mobile.getOrElse(i) { 0L } else 0L) }
    val max = (shown.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val grid = T.sub.copy(alpha = .25f)
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(height).then(if (onTap != null) Modifier.pointerInput(n) { detectTapGestures { o -> onTap(((o.x / size.width) * n).toInt().coerceIn(0, n - 1)) } } else Modifier)
    ) {
        val slot = size.width / n
        val bw = slot * .68f
        drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
        for (i in 0 until n) {
            val x = i * slot + (slot - bw) / 2f
            val w = if (filter != 2) wifi.getOrElse(i) { 0L } else 0L
            val m = if (filter != 1) mobile.getOrElse(i) { 0L } else 0L
            val mh = m / max * size.height
            val wh = w / max * size.height
            val dim = if (selected >= 0 && selected != i) .45f else 1f
            val r = androidx.compose.ui.geometry.CornerRadius(bw / 3f)
            if (mh > 0f) drawRoundRect(MOBILE_COLOR.copy(alpha = dim), androidx.compose.ui.geometry.Offset(x, size.height - mh), androidx.compose.ui.geometry.Size(bw, mh), r)
            if (wh > 0f) drawRoundRect(WIFI_COLOR.copy(alpha = dim), androidx.compose.ui.geometry.Offset(x, size.height - mh - wh), androidx.compose.ui.geometry.Size(bw, wh), r)
            if (selected == i) drawRect(T.text.copy(alpha = .08f), androidx.compose.ui.geometry.Offset(i * slot, 0f), androidx.compose.ui.geometry.Size(slot, size.height))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String, value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Column { Text(value, color = T.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(text, color = T.sub, fontSize = 11.sp) }
}

private fun dayLabel(start: Long, daysAgo: Int): String = when (daysAgo) {
    0 -> com.umutk.pulse.tr("Today", "Bugün")
    1 -> com.umutk.pulse.tr("Yesterday", "Dün")
    else -> java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(start))
}

/**
 * Network use: Wi-Fi and mobile data kept apart (a switch at the top picks both, Wi-Fi or mobile), a thirty-day strip, and one page per day to swipe
 * through: the hourly graph on top, then the apps of that day.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NetUsageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Extra.usageGranted(ctx)) }
    var filter by remember { mutableStateOf(0) }   // 0 both, 1 Wi-Fi, 2 mobile data
    var range by remember { mutableStateOf(0) }    // 0 one day at a time, 1 the last 7 days, 2 the last 30 days (the ring and the apps)
    var openPkg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { NetBlock.restore(ctx) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = Extra.usageGranted(ctx) }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    val days = 30
    // the strip: Wi-Fi and mobile bytes of each of the last thirty days, oldest at the left
    val daily by produceState<Pair<List<Long>, List<Long>>?>(null, granted) {
        value = if (!granted) null else withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val w = ArrayList<Long>(); val m = ArrayList<Long>()
            for (i in days - 1 downTo 0) {
                val s = dayStartAgo(i); val e = minOf(s + DAY_MS, now)
                w += sumOf(netSum(ctx, ConnectivityManager.TYPE_WIFI, s, e)); m += sumOf(netSum(ctx, ConnectivityManager.TYPE_MOBILE, s, e))
            }
            w to m
        }
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = days - 1) { days }
    val scope = rememberCoroutineScope()
    val cache = remember { androidx.compose.runtime.mutableStateMapOf<Int, DayData>() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(com.umutk.pulse.tr("Back", "Geri"), color = T.accent) }
            Spacer(Modifier.weight(1f))
            Text(com.umutk.pulse.tr("Network usage", "Ağ kullanımı"), color = T.text, fontSize = 18.sp)
        }
        if (!granted) {
            PCard {
                Text(com.umutk.pulse.tr("Showing network use per app needs the \"usage access\" permission.", "Uygulama başına ağ kullanımı için \"kullanım erişimi\" izni gerekir."), color = T.text, fontSize = 14.sp)
                PButton(com.umutk.pulse.tr("Allow", "İzin ver")) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return@Column
        }
        // both / Wi-Fi / mobile data
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to com.umutk.pulse.tr("Both", "Hepsi"), 1 to "Wi-Fi", 2 to com.umutk.pulse.tr("Mobile data", "Mobil veri")).forEach { (id, label) ->
                val on = filter == id
                Text(label, Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) Look.accent else T.panel).clickable { filter = id }.padding(vertical = 9.dp),
                    color = if (on) Color.White else T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        // day / week / month
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to com.umutk.pulse.tr("Day", "Gün"), 1 to com.umutk.pulse.tr("Week", "Hafta"), 2 to com.umutk.pulse.tr("Month", "Ay")).forEach { (id, label) ->
                val on = range == id
                Text(label, Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(if (on) T.text.copy(alpha = .16f) else Color.Transparent).clickable { range = id }.padding(vertical = 7.dp),
                    color = T.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (range > 0) {
            val span = if (range == 1) 7 else 30
            val sum by produceState<List<NetRow>?>(null, span) { value = withContext(Dispatchers.IO) { netUsageRange(ctx, dayStartAgo(span - 1), System.currentTimeMillis()) } }
            val rowsR = (sum ?: emptyList()).map { r -> r to when (filter) { 1 -> r.wifi; 2 -> r.mobile; else -> r.total } }.filter { it.second > 0L }.sortedByDescending { it.second }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    val tw = (sum ?: emptyList()).sumOf { it.wifi }; val tm = (sum ?: emptyList()).sumOf { it.mobile }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        if (filter != 2) Legend(WIFI_COLOR, "Wi-Fi", bytesText(tw))
                        if (filter != 1) Legend(MOBILE_COLOR, com.umutk.pulse.tr("Mobile data", "Mobil veri"), bytesText(tm))
                    }
                    // the pie of the period: the biggest apps and the rest
                    if (sum == null) Center("…") else if (rowsR.isNotEmpty()) PCard {
                        val slices = rowsR.take(8).map { it.first.label to it.second } + (rowsR.drop(8).sumOf { it.second }.takeIf { it > 0 }?.let { listOf(com.umutk.pulse.tr("Others", "Diğer") to it) }.orEmpty())
                        DonutChart(slices, Modifier.fillMaxWidth(), centerText = bytesText(rowsR.sumOf { it.second }))
                    }
                }
                val top = rowsR.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
                items(rowsR.take(150), key = { "r|${it.first.pkg}|${it.first.label}" }) { (r, t) -> NetAppRow(r, t, top, filter, openPkg == r.pkg + r.label) { openPkg = if (openPkg == r.pkg + r.label) "" else r.pkg + r.label } }
                item { Spacer(Modifier.height(24.dp)) }
            }
            return@Column
        }
        // thirty days: a tap goes to that day
        daily?.let { (w, m) -> StackBars(w, m, filter, 54.dp, selected = pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } } }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            val ago = days - 1 - page
            val start = remember(page) { dayStartAgo(ago) }
            val data = cache[page]
            LaunchedEffect(page) {
                if (cache[page] == null || ago == 0) cache[page] = withContext(Dispatchers.IO) { loadDay(ctx, start) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(dayLabel(start, ago), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    val tw = data?.hourWifi?.sum() ?: 0L; val tm = data?.hourMobile?.sum() ?: 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        if (filter != 2) Legend(WIFI_COLOR, "Wi-Fi", bytesText(tw))
                        if (filter != 1) Legend(MOBILE_COLOR, com.umutk.pulse.tr("Mobile data", "Mobil veri"), bytesText(tm))
                    }
                    // the hourly graph on top
                    PCard {
                        Text(com.umutk.pulse.tr("Hour by hour", "Saat saat"), color = T.sub, fontSize = 12.sp)
                        if (data == null) Center("…") else {
                            StackBars(data.hourWifi, data.hourMobile, filter, 110.dp)
                            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("00", "06", "12", "18", "24").forEach { Text(it, color = T.sub, fontSize = 10.sp) }
                            }
                        }
                    }
                    Text(com.umutk.pulse.tr("Apps", "Uygulamalar"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                }
                val rows = (data?.rows ?: emptyList()).map { r -> val t = when (filter) { 1 -> r.wifi; 2 -> r.mobile; else -> r.total }; r to t }.filter { it.second > 0L }.sortedByDescending { it.second }
                if (data != null && rows.isEmpty()) item { Center(com.umutk.pulse.tr("No network use recorded", "Kayıtlı ağ kullanımı yok")) }
                val top = rows.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
                items(rows.take(150), key = { "${page}|${it.first.pkg}|${it.first.label}" }) { (r, t) -> NetAppRow(r, t, top, filter, openPkg == r.pkg + r.label) { openPkg = if (openPkg == r.pkg + r.label) "" else r.pkg + r.label } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** One app's line: its icon, the bytes and a bar. A tap opens its two switches (block mobile data / block Wi-Fi), which need root. */
@Composable
private fun NetAppRow(r: NetRow, t: Long, top: Long, filter: Int, open: Boolean, toggle: () -> Unit) {
    val ctx = LocalContext.current
    val uid = remember(r.pkg) { if (r.pkg.isEmpty()) -1 else runCatching { ctx.packageManager.getApplicationInfo(r.pkg, 0).uid }.getOrDefault(-1) }
    var rev by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.padding(vertical = 6.dp).then(if (uid >= 0) Modifier.clickable { toggle() } else Modifier)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.pkg.isNotEmpty()) AppIcon(r.pkg, 26.dp) else Spacer(Modifier.width(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(r.label, Modifier.weight(1f), color = T.text, fontSize = 14.sp, maxLines = 1)
            if (uid >= 0 && (NetBlock.isBlocked(ctx, uid, true) || NetBlock.isBlocked(ctx, uid, false))) PIcon("block", Color(0xFFE5484D), 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(bytesText(t), color = T.sub, fontSize = 13.sp)
        }
        if (filter == 0) Text("Wi-Fi ${bytesText(r.wifi)} · ${com.umutk.pulse.tr("mobile", "mobil")} ${bytesText(r.mobile)}", Modifier.padding(start = 34.dp, top = 2.dp), color = T.sub, fontSize = 11.sp, maxLines = 1)
        BarMeter(t.toFloat() / top, if (filter == 2) MOBILE_COLOR else T.accent, Modifier.padding(start = 34.dp, top = 4.dp), h = 4.dp)
        if (open && uid >= 0) Column(Modifier.padding(start = 34.dp, top = 6.dp)) {
            if (com.umutk.pulse.Shell.root != true) Text(com.umutk.pulse.tr("Blocking one app needs root; Android has no other way to cut a single app off.", "Tek bir uygulamayı kesmek root ister; Android'de başka yolu yok."), color = T.sub, fontSize = 12.sp)
            else {
                listOf(true to com.umutk.pulse.tr("Block mobile data", "Mobil veriyi engelle"), false to com.umutk.pulse.tr("Block Wi-Fi", "Wi-Fi'yi engelle")).forEach { (mob, label) ->
                    val on = remember(rev) { NetBlock.isBlocked(ctx, uid, mob) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        com.umutk.pulse.PSwitch(on, { want -> NetBlock.set(ctx, uid, mob, want) { ok -> rev++; msg = if (ok) "" else com.umutk.pulse.tr("The firewall did not take the rule.", "Güvenlik duvarı kuralı kabul etmedi.") } })
                    }
                }
                if (msg.isNotEmpty()) Text(msg, color = Color(0xFFE5484D), fontSize = 12.sp)
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
