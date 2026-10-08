package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- what the guardians read

/** Thermal zones, battery details, storage and disk traffic: the numbers behind the four guardian pages. */
object Guard {
    class Zone(val name: String, val group: String, val c: Float)

    private fun read(path: String): String = try { File(path).readText().trim() } catch (e: Exception) { if (Shell.root == true) Shell.run("cat $path").trim() else "" }

    private fun group(type: String): String {
        val s = type.lowercase()
        return when {
            "gpu" in s -> "Graphics"
            "bat" in s || "charg" in s -> "Battery"
            "skin" in s || "quiet" in s || "case" in s || "back" in s || "xo-therm" in s || "board" in s -> "Device"
            "modem" in s || "mdm" in s || "wlan" in s || "wifi" in s || "pa-therm" in s || "pa_" in s || "5g" in s -> "Radio"
            "cpu" in s || "tsens" in s || "big" in s || "little" in s || "soc" in s || "core" in s || "nsp" in s || "ddr" in s || "mid" in s -> "Processor"
            else -> "Other"
        }
    }

    /** Every thermal zone that reports a believable temperature, grouped. */
    fun zones(): List<Zone> {
        val out = ArrayList<Zone>()
        for (i in 0 until 90) {
            val raw = try { File("/sys/class/thermal/thermal_zone$i/temp").readText().trim().toFloatOrNull() } catch (e: Exception) { null } ?: continue
            val c = if (raw > 1000) raw / 1000 else raw
            if (c !in 5f..150f) continue
            val type = try { File("/sys/class/thermal/thermal_zone$i/type").readText().trim() } catch (e: Exception) { "zone$i" }
            out += Zone(type, group(type), c)
        }
        return out
    }

    class Bat(val plug: String, val health: String, val cycles: Int, val tech: String, val remMah: Int, val fullMah: Int, val designMah: Int)

    fun battery(ctx: Context): Bat {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val plug = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) { 1 -> "Charger"; 2 -> "USB"; 4 -> "Wireless"; 8 -> "Dock"; else -> "Not plugged in" }
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"; BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"; else -> "—"
        }
        var cycles = if (Build.VERSION.SDK_INT >= 34) (i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1) else -1
        if (cycles < 0) cycles = read("/sys/class/power_supply/battery/cycle_count").toIntOrNull() ?: -1
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceAtLeast(1)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)          // µAh left
        val rem = if (counter > 0) counter / 1000 else 0
        val full = (read("/sys/class/power_supply/battery/charge_full").toLongOrNull() ?: 0L).let { if (it > 0) (it / 1000).toInt() else if (rem > 0) rem * 100 / pct else 0 }
        val design = ((read("/sys/class/power_supply/battery/charge_full_design").toLongOrNull() ?: 0L) / 1000).toInt()
        return Bat(plug, health, cycles, i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifBlank { "—" }, rem, full, design)
    }

    /** Sectors read and written so far by the whole disks (not their partitions), or null when /proc/diskstats cannot be read. */
    fun diskSectors(): Pair<Long, Long>? {
        val txt = read("/proc/diskstats")
        if (txt.isBlank()) return null
        var r = 0L; var w = 0L
        txt.lineSequence().forEach { l ->
            val f = l.trim().split(Regex("\\s+"))
            if (f.size < 10) return@forEach
            val n = f[2]
            val whole = Regex("^(sd[a-z]+|mmcblk\\d+|nvme\\d+n\\d+)$").matches(n)
            if (whole) { r += f[5].toLongOrNull() ?: 0L; w += f[9].toLongOrNull() ?: 0L }
        }
        return r to w
    }

    /** The biggest app data folders (root only: /data/data belongs to the apps). */
    fun appData(): List<Pair<String, Long>> =
        Shell.run("du -sk /data/data/* 2>/dev/null | sort -rn | head -12").lineSequence().mapNotNull { l ->
            val p = l.trim().split(Regex("\\s+"), 2)
            if (p.size < 2) null else (p[1].substringAfterLast('/')) to ((p[0].toLongOrNull() ?: return@mapNotNull null) * 1024)
        }.toList()

    fun heat(c: Float): Color = when { c < 38f -> Color(0xFF2E9E5B); c < 45f -> Color(0xFFE0B83C); c < 50f -> Color(0xFFE67E22); else -> Color(0xFFC0392B) }
    fun heatWord(c: Float) = when { c < 38f -> "Cool"; c < 45f -> "Warm"; c < 50f -> "Hot"; else -> "Very hot" }
    fun gb(b: Long) = if (b >= 1073741824L) "%.1f GB".format(b / 1073741824.0) else "%.0f MB".format(b / 1048576.0)
}

// ---------------------------------------------------------------- small pieces

/** A ring gauge (three quarters of a circle) with a big number inside. */
@Composable
fun Gauge(frac: Float, color: Color, big: String, small: String, size: Dp = 132.dp) {
    val track = T.line.copy(alpha = .4f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = 12.dp.toPx()
            val box = Size(this.size.width - w, this.size.height - w)
            drawArc(color = track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color = color, startAngle = 135f, sweepAngle = 270f * frac.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(w / 2, w / 2), size = box, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, color = T.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(small, color = T.sub, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(color.copy(alpha = .15f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))

@Composable
private fun Caption(text: String) = Text(text, color = T.sub, fontSize = 12.sp)

/** Name, a bar and a value on one line. */
@Composable
private fun BarRow(name: String, frac: Float, value: String, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row { Text(name, color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1); Text(value, color = T.sub, fontSize = 13.sp) }
        BarMeter(frac, color, h = 6.dp)
    }
}

/** A graph with a title and its range written under it. */
@Composable
private fun LongGraph(title: String, series: List<Float>, color: Color, max: Float, unit: String, height: Dp = 80.dp) {
    Caption(title)
    Graph(listOf(series), listOf(color), max, height = height, n = 360)
    Row { Caption("30 min ago"); Spacer(Modifier.weight(1f)); Caption(if (series.isEmpty()) "collecting…" else "now: %.0f %s   peak: %.0f".format(series.last(), unit, series.max())) }
}

@Composable
private fun Page(title: String, trailing: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead(title, trailing)
        content()
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- the module

private class G(val id: String, val label: String)
private val Gs = listOf(G("thermal", "Thermal"), G("battery", "Battery"), G("storage", "Storage"), G("memory", "Memory"))

/** Samsung's Good Guardians idea for any phone: one detailed page each for heat, battery, storage and memory, with long graphs. */
@Composable
fun GuardiansModule() {
    var sub by rememberSaveable { mutableStateOf("thermal") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Gs.forEach { p ->
                val on = sub == p.id
                Column(Modifier.clickable { sub = p.id }.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FitText(p.label, if (on) Look.accent else T.sub, 14.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    Box(Modifier.padding(top = 4.dp).width(28.dp).height(2.dp).background(if (on) Look.accent else Color.Transparent))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (sub) { "thermal" -> ThermalGuardian(); "battery" -> BatteryGuardian(); "storage" -> StorageGuardian(); else -> MemoryGuardian() }
        }
    }
}

// ---------------------------------------------------------------- thermal

@Composable
fun ThermalGuardian() {
    val s by Sampler.snap.collectAsState()
    val lh by Sampler.longHistory.collectAsState()
    var zones by remember { mutableStateOf<List<Guard.Zone>>(emptyList()) }
    val cores = remember { Hw.cores() }
    LaunchedEffect(Unit) { while (true) { zones = withContext(Dispatchers.IO) { Guard.zones() }; delay(2500) } }
    val hot = maxOf(zones.maxOfOrNull { it.c } ?: 0f, s.cpuTemp)
    val ratios = s.cores.mapIndexedNotNull { i, c -> cores.getOrNull(i)?.maxMhz?.takeIf { it > 0 }?.let { c.mhz.toFloat() / it } }
    val speed = if (ratios.isEmpty()) 1f else ratios.average().toFloat()
    val throttling = hot >= 45f && s.cpu >= 50 && speed < .6f
    val col = Guard.heat(hot)
    Page("Thermal", if (s.root) "root" else "") {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gauge((hot - 20f) / 40f, col, Opt.temp(hot), "hottest")
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (throttling) "Slowing down to cool off" else Guard.heatWord(hot), if (throttling) Color(0xFFC0392B) else col)
                    Text("Battery ${Opt.temp(s.batTemp)}", color = T.text, fontSize = 14.sp)
                    Text("Processor at ${(speed * 100).toInt()}% of its top speed", color = T.sub, fontSize = 13.sp)
                    Text("${s.cpu}% load", color = T.sub, fontSize = 13.sp)
                }
            }
        }
        PCard {
            LongGraph("Hottest sensor (30 min)", lh["temp"].orEmpty(), Guard.heat(hot), 80f, "°C", 90.dp)
            LongGraph("Battery temperature (30 min)", lh["btemp"].orEmpty(), resColor("battery"), 60f, "°C")
        }
        val groups = zones.groupBy { it.group }.toList().sortedByDescending { g -> g.second.maxOf { it.c } }
        if (groups.isEmpty()) PCard { Text("This phone does not let apps read its thermal sensors.", color = T.sub, fontSize = 13.sp) }
        groups.forEach { (g, list) ->
            PCard {
                Row { Text(g, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(Opt.temp(list.maxOf { it.c }), color = Guard.heat(list.maxOf { it.c }), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                list.sortedByDescending { it.c }.take(8).forEach { z -> BarRow(z.name, (z.c - 20f) / 60f, Opt.temp(z.c), Guard.heat(z.c)) }
            }
        }
        if (s.cores.isNotEmpty()) PCard {
            Text("Processor speed per core", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            s.cores.forEachIndexed { i, c ->
                val mx = cores.getOrNull(i)?.maxMhz ?: 0
                BarRow("Core $i", if (mx > 0) c.mhz.toFloat() / mx else 0f, "${c.mhz} MHz" + if (mx > 0) " / $mx" else "", resColor("cpu"))
            }
        }
    }
}

// ---------------------------------------------------------------- battery

@Composable
fun BatteryGuardian() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    val lh by Sampler.longHistory.collectAsState()
    var b by remember { mutableStateOf<Guard.Bat?>(null) }
    LaunchedEffect(Unit) { while (true) { b = withContext(Dispatchers.IO) { Guard.battery(ctx) }; delay(5000) } }
    val ma = kotlin.math.abs(s.batMa)
    val watts = ma * s.batV / 1000f
    val recent = lh["ma"].orEmpty().takeLast(12)
    val avgMa = if (recent.isEmpty()) ma.toFloat() else recent.average().toFloat()
    val info = b
    val left = when {
        info == null || avgMa < 25f -> "—"
        s.charging -> if (info.fullMah > info.remMah && info.remMah > 0) hms(((info.fullMah - info.remMah) / avgMa * 3600_000f).toLong()) + " to full" else "full soon"
        info.remMah > 0 -> hms((info.remMah / avgMa * 3600_000f).toLong()) + " left"
        else -> "—"
    }
    val col = if (s.batPct <= 15 && !s.charging) Color(0xFFC0392B) else if (s.charging) Color(0xFF2E9E5B) else resColor("battery")
    Page("Battery", if (s.root) "root" else "") {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gauge(s.batPct / 100f, col, "${s.batPct}%", if (s.charging) "charging" else "on battery")
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (s.charging) "Charging" else "Discharging", col)
                    Text(left, color = T.text, fontSize = 14.sp)
                    Text("%.1f W · $ma mA".format(watts), color = T.sub, fontSize = 13.sp)
                    Text("%.2f V · ${Opt.temp(s.batTemp)}".format(s.batV), color = T.sub, fontSize = 13.sp)
                }
            }
        }
        PCard {
            LongGraph("Charge level (30 min)", lh["bat"].orEmpty(), resColor("cpu"), 100f, "%")
            LongGraph("Current (30 min)", lh["ma"].orEmpty(), col, maxOf(500f, (lh["ma"].orEmpty().maxOrNull() ?: 0f) * 1.2f), "mA")
            LongGraph("Power (30 min)", lh["w"].orEmpty(), Color(0xFFE67E22), maxOf(5f, (lh["w"].orEmpty().maxOrNull() ?: 0f) * 1.2f), "W")
        }
        PCard {
            Text("Health", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (info == null) Text("Reading…", color = T.sub, fontSize = 13.sp) else {
                val healthPct = if (info.designMah > 0 && info.fullMah > 0) 100f * info.fullMah / info.designMah else 0f
                if (healthPct > 0f) BarRow("Capacity vs new", healthPct / 100f, "%.0f%%  (${info.fullMah} / ${info.designMah} mAh)".format(healthPct), if (healthPct >= 80f) resColor("battery") else Color(0xFFE67E22))
                InfoRows(listOfNotNull(
                    "Condition" to info.health, "Power source" to info.plug, "Technology" to info.tech,
                    if (info.cycles >= 0) "Charge cycles" to "${info.cycles}" else null,
                    if (info.remMah > 0) "Charge left" to "${info.remMah} mAh" else null,
                    if (info.fullMah > 0 && info.designMah == 0) "Full capacity" to "${info.fullMah} mAh (estimated)" else null,
                ))
            }
        }
    }
}

// ---------------------------------------------------------------- storage

@Composable
fun StorageGuardian() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val total = remember { try { StatFs(Environment.getDataDirectory().path).totalBytes } catch (e: Exception) { 0L } }
    var free by remember { mutableStateOf(try { StatFs(Environment.getDataDirectory().path).availableBytes } catch (e: Exception) { 0L }) }
    var folders by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var apps by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var msg by remember { mutableStateOf("") }
    val rd = remember { mutableStateListOf<Float>() }; val wr = remember { mutableStateListOf<Float>() }
    var ioOk by remember { mutableStateOf(true) }
    val root = Shell.root == true
    LaunchedEffect(Unit) {
        folders = withContext(Dispatchers.IO) { try { Hw.folderSizes() } catch (e: Throwable) { emptyList() } }
        if (root) apps = withContext(Dispatchers.IO) { Guard.appData() }
    }
    LaunchedEffect(Unit) {
        var prev: Pair<Long, Long>? = null
        while (true) {
            val cur = withContext(Dispatchers.IO) { Guard.diskSectors() }
            free = try { StatFs(Environment.getDataDirectory().path).availableBytes } catch (e: Exception) { free }
            if (cur == null) { ioOk = false } else {
                val p = prev
                if (p != null) { rd.add(((cur.first - p.first) * 512 / 1024f).coerceAtLeast(0f)); wr.add(((cur.second - p.second) * 512 / 1024f).coerceAtLeast(0f)); if (rd.size > 60) { rd.removeAt(0); wr.removeAt(0) } }
                prev = cur
            }
            delay(1000)
        }
    }
    val used = (total - free).coerceAtLeast(0L)
    val frac = if (total > 0) used.toFloat() / total else 0f
    val col = if (frac > .9f) Color(0xFFC0392B) else if (frac > .75f) Color(0xFFE67E22) else resColor("disk")
    Page("Storage", if (root) "root" else "") {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gauge(frac, col, "${(frac * 100).toInt()}%", "used")
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (frac > .9f) "Almost full" else if (frac > .75f) "Getting full" else "Plenty of room", col)
                    Text("${Guard.gb(used)} of ${Guard.gb(total)}", color = T.text, fontSize = 14.sp)
                    Text("${Guard.gb(free)} free", color = T.sub, fontSize = 13.sp)
                }
            }
        }
        PCard {
            Text("Disk traffic (60 s)", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!ioOk) Text("This phone does not let apps read disk statistics.", color = T.sub, fontSize = 13.sp) else {
                val mx = maxOf(512f, (rd + wr).maxOrNull() ?: 0f) * 1.2f
                Graph(listOf(rd.toList(), wr.toList()), listOf(resColor("disk"), Color(0xFFE67E22)), mx, height = 80.dp)
                Row { Text("Read ${rate((rd.lastOrNull() ?: 0f) * 1024f)}", color = resColor("disk"), fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("Write ${rate((wr.lastOrNull() ?: 0f) * 1024f)}", color = Color(0xFFE67E22), fontSize = 13.sp) }
            }
        }
        PCard {
            Text("Where the space goes", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val f = folders
            if (f == null) Text("Measuring the folders…", color = T.sub, fontSize = 13.sp)
            else if (f.isEmpty()) Text("Could not read the shared storage (all-files access may be needed).", color = T.sub, fontSize = 13.sp)
            else { val mx = (f.firstOrNull()?.second ?: 1L).coerceAtLeast(1L); f.take(10).forEach { (n, sz) -> BarRow(n, sz.toFloat() / mx, Guard.gb(sz), resColor("disk")) } }
        }
        if (root) PCard {
            Text("Apps by data (root)", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val a = apps
            if (a == null) Text("Measuring…", color = T.sub, fontSize = 13.sp)
            else { val mx = (a.firstOrNull()?.second ?: 1L).coerceAtLeast(1L); a.forEach { (n, sz) -> BarRow(labelOf(ctx, n), sz.toFloat() / mx, Guard.gb(sz), Color(0xFF8B12AE)) } }
        }
        PCard {
            Text("Clean up", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Trim app caches" + if (root) "" else " (root)") { scope.launch { withContext(Dispatchers.IO) { Shell.run("pm trim-caches 999G") }; msg = if (root) "App caches trimmed" else "This needs root" } }
                PButton("Android storage settings") { try { ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { msg = "Could not open the settings" } }
            }
            if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- memory

@Composable
fun MemoryGuardian() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Sampler.snap.collectAsState()
    val lh by Sampler.longHistory.collectAsState()
    var top by remember { mutableStateOf<List<Proc>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    val root = s.root
    LaunchedEffect(root) { while (true) { top = withContext(Dispatchers.IO) { try { Sampler.processes().sortedByDescending { it.rssMb }.take(8) } catch (e: Throwable) { emptyList() } }; delay(4000) } }
    val total = s.ramTotalMb.coerceAtLeast(1)
    val frac = s.ramUsedMb.toFloat() / total
    val col = if (frac > .9f) Color(0xFFC0392B) else if (frac > .75f) Color(0xFFE67E22) else resColor("memory")
    val free = (total - s.ramUsedMb - s.ramCachedMb).coerceAtLeast(0)
    Page("Memory", if (root) "root" else "") {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gauge(frac, col, "${(frac * 100).toInt()}%", "in use")
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (frac > .9f) "Under pressure" else if (frac > .75f) "Busy" else "Comfortable", col)
                    Text("${mb(s.ramUsedMb)} of ${mb(s.ramTotalMb)}", color = T.text, fontSize = 14.sp)
                    Text("${mb(total - s.ramUsedMb)} available", color = T.sub, fontSize = 13.sp)
                }
            }
            // used / cached / free in one bar
            Row(Modifier.fillMaxWidth().height(12.dp).background(T.line.copy(alpha = .3f), RoundedCornerShape(6.dp))) {
                Box(Modifier.weight(s.ramUsedMb.coerceAtLeast(1).toFloat()).fillMaxHeight().background(col))
                Box(Modifier.weight(s.ramCachedMb.coerceAtLeast(1).toFloat()).fillMaxHeight().background(col.copy(alpha = .4f)))
                Box(Modifier.weight(free.coerceAtLeast(1).toFloat()).fillMaxHeight())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Caption("■ in use"); Caption("■ cached"); Caption("□ free") }
        }
        PCard { LongGraph("Memory in use (30 min)", lh["ram"].orEmpty(), col, 100f, "%", 90.dp) }
        PCard {
            Text("Biggest apps", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (top.isEmpty()) Text(if (root) "Reading…" else "Other apps' memory needs root.", color = T.sub, fontSize = 13.sp)
            else { val mx = top.first().rssMb.coerceAtLeast(1); top.forEach { p -> BarRow(labelOf(ctx, p.name), p.rssMb.toFloat() / mx, mb(p.rssMb), resColor("memory")) } }
        }
        PCard {
            InfoRows(listOf("Total" to mb(s.ramTotalMb), "In use" to mb(s.ramUsedMb), "Cached" to mb(s.ramCachedMb), "Swap" to "${s.swapUsedMb} / ${s.swapTotalMb} MB", "Processes" to "${s.procs}"))
        }
        PCard {
            Text("Free up memory", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (root) "Root is available: these work." else "These need root.", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Drop RAM cache") { scope.launch { withContext(Dispatchers.IO) { Shell.run("sync; echo 3 > /proc/sys/vm/drop_caches") }; msg = "RAM cache dropped" } }
                PButton("Kill background apps") { scope.launch { withContext(Dispatchers.IO) { Shell.run("am kill-all") }; msg = "Background apps killed" } }
            }
            if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 12.sp)
        }
    }
}
