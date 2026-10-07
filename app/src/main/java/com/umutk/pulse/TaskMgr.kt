package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The heat-map behind a cell, as in Task Manager: Windows 10 and older use the yellow-to-orange ramp, Windows 11 / Material tint with the accent colour. */
@Composable
private fun heatBg(f: Float): Color {
    val t = T
    if (f < .02f) return Color.Transparent
    if (!t.dark && (t.id == "win10" || t.id == "win7" || t.id == "xp" || t.id == "win95"))
        return when { f < .1f -> Color(0xFFFFF4C4); f < .3f -> Color(0xFFFFE8A3); f < .6f -> Color(0xFFFFCF70); else -> Color(0xFFFFA45C) }
    val lo = if (t.dark) .14f else .09f; val span = if (t.dark) .5f else .36f
    return Look.accent.copy(alpha = lo + f.coerceIn(0f, 1f) * span)
}

@Composable
private fun HeatCell(text: String, f: Float, width: Dp, bold: Boolean = false) {
    Box(Modifier.width(width).fillMaxHeight().background(heatBg(f)), contentAlignment = Alignment.CenterEnd) {
        Text(text, color = T.text, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(end = 8.dp), textAlign = TextAlign.End)
    }
}

private class PRow(val p: Proc, val app: Boolean, val label: String)

// ---------------------------------------------------------------- Processes

@Composable
fun ProcessesPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    var rows by remember { mutableStateOf<List<PRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(-1) }
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("mem") }
    var ask by remember { mutableStateOf<PRow?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            rows = withContext(Dispatchers.IO) { Sampler.processes().map { PRow(it, Extra.isApp(ctx, it.name), labelOf(ctx, it.name)) } }
            loaded = true; delay(3000)
        }
    }
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val shown = rows.filter { q.isBlank() || it.label.contains(q, true) || it.p.name.contains(q, true) }
        .let { l -> if (sort == "cpu") l.sortedByDescending { it.p.cpu } else if (sort == "name") l.sortedBy { it.label.lowercase() } else l.sortedByDescending { it.p.rssMb } }
    val selRow = rows.firstOrNull { it.p.pid == sel }
    fun end(r: PRow) { scope.launch { withContext(Dispatchers.IO) { Sampler.kill(r.p) }; ask = null; sel = -1 } }
    ask?.let { r ->
        AlertDialog(onDismissRequest = { ask = null }, containerColor = T.panel, titleContentColor = T.text, textContentColor = T.sub,
            title = { Text("End ${r.label}?") }, text = { Text("Unsaved data in this process will be lost. (PID ${r.p.pid})") },
            confirmButton = { TextButton({ end(r) }) { Text("End process", color = Color(0xFFC42B1C)) } },
            dismissButton = { TextButton({ ask = null }) { Text("Cancel", color = T.sub) } })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Processes", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selRow != null) PButton("End task", danger = true) { if (Cfg.confirmKill) ask = selRow else end(selRow) }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), singleLine = true, placeholder = { Text("Type a name or PID to search") },
            leadingIcon = { PIcon("search", T.sub, 18.dp) }, shape = RoundedCornerShape(T.radius.coerceAtMost(14.dp)),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = T.text, unfocusedTextColor = T.text, focusedBorderColor = Look.accent, unfocusedBorderColor = T.line, cursorColor = Look.accent))
        // column heads with the totals on top, like the real one; tap to sort
        Row(Modifier.fillMaxWidth().height(46.dp).border(BorderStroke(1.dp, T.line.copy(alpha = .6f)))) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { sort = "name" }.padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text("Name", color = if (sort == "name") Look.accent else T.sub, fontSize = 12.sp)
            }
            Column(Modifier.width(64.dp).fillMaxHeight().clickable { sort = "cpu" }.background(heatBg(s.cpu / 100f)).padding(end = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text("${s.cpu}%", color = T.text, fontSize = 15.sp); Text("CPU", color = if (sort == "cpu") Look.accent else T.sub, fontSize = 11.sp)
            }
            Column(Modifier.width(84.dp).fillMaxHeight().clickable { sort = "mem" }.background(heatBg(memPct / 100f)).padding(end = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text("$memPct%", color = T.text, fontSize = 15.sp); Text("Memory", color = if (sort == "mem") Look.accent else T.sub, fontSize = 11.sp)
            }
        }
        if (loaded && rows.isEmpty()) Center("The process list needs root.\nAndroid does not show other apps' processes without it.")
        else LazyColumn(Modifier.fillMaxSize()) {
            fun group(id: String, title: String, list: List<PRow>) {
                if (list.isEmpty()) return
                val open = id !in collapsed
                item(key = "h$id") {
                    Row(Modifier.fillMaxWidth().clickable { collapsed = if (open) collapsed + id else collapsed - id }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (open) "⌄" else "›", color = T.sub, fontSize = 16.sp, modifier = Modifier.width(18.dp))
                        Text("$title (${list.size})", color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (open) items(list.take(120), key = { it.p.pid }) { r ->
                    val on = sel == r.p.pid
                    Row(Modifier.fillMaxWidth().height(40.dp).background(if (on) Look.accent.copy(alpha = .16f) else Color.Transparent).clickable { sel = if (on) -1 else r.p.pid }, verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(26.dp))
                        AppIcon(r.p.name, 22.dp); Spacer(Modifier.width(8.dp))
                        Text(r.label, Modifier.weight(1f), color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HeatCell("%.1f%%".format(r.p.cpu), r.p.cpu / 100f, 64.dp)
                        HeatCell(mb(r.p.rssMb), r.p.rssMb / 1500f, 84.dp)
                    }
                }
            }
            group("apps", "Apps", shown.filter { it.app }); group("bg", "Background processes", shown.filter { !it.app })
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ---------------------------------------------------------------- Performance: resource list on the left, big graph and numbers on the right

@Composable
fun PerformancePage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    val h by Sampler.history.collectAsState()
    var sel by rememberSaveable { mutableStateOf("cpu") }
    var secs by remember { mutableStateOf<List<Section>>(emptyList()) }
    val cpuName = remember { Extra.cpuName() }
    LaunchedEffect(Unit) { while (true) { secs = withContext(Dispatchers.IO) { Device.sections(ctx) }; delay(3000) } }
    fun sec(t: String) = secs.firstOrNull { it.title == t }?.rows.orEmpty()
    val avgMhz = if (s.cores.isEmpty()) 0 else s.cores.sumOf { it.mhz } / s.cores.size
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val dataTotal = try { android.os.StatFs(android.os.Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes } } catch (e: Exception) { 1L to 1L }
    val diskPct = (100 * (dataTotal.first - dataTotal.second) / dataTotal.first.coerceAtLeast(1)).toInt()
    val netMax = (h["down"].orEmpty() + h["up"].orEmpty()).maxOrNull()?.coerceAtLeast(102400f) ?: 102400f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listW = if (maxWidth >= 600.dp) 200.dp else 124.dp
        Row(Modifier.fillMaxSize()) {
            // resource list
            Column(Modifier.width(listW).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PerfItem("CPU", "${s.cpu}%${if (avgMhz > 0) " " + "%.2f GHz".format(avgMhz / 1000f) else ""}", listOf(h["cpu"].orEmpty()), resColor("cpu"), 100f, sel == "cpu") { sel = "cpu" }
                PerfItem("Memory", "${mb(s.ramUsedMb)}/${mb(s.ramTotalMb)} ($memPct%)", listOf(h["ram"].orEmpty()), resColor("memory"), 100f, sel == "memory") { sel = "memory" }
                PerfItem("Storage", "$diskPct% used", emptyList(), resColor("disk"), 100f, sel == "disk") { sel = "disk" }
                PerfItem("Network", "D ${rate(s.down.toFloat())}  U ${rate(s.up.toFloat())}", listOf(h["down"].orEmpty(), h["up"].orEmpty()), resColor("net"), netMax, sel == "net") { sel = "net" }
                PerfItem("GPU", if (s.gpu >= 0) "${s.gpu}%" else "—", listOf(h["gpu"].orEmpty()), resColor("gpu"), 100f, sel == "gpu") { sel = "gpu" }
                PerfItem("Battery", "${s.batPct}%${if (s.charging) " ⚡" else ""}  ${s.batMa} mA", listOf(h["bat"].orEmpty()), resColor("battery"), 100f, sel == "battery") { sel = "battery" }
                PerfItem("Thermal", if (s.cpuTemp > 0) Opt.temp(s.cpuTemp) else "—", listOf(h["temp"].orEmpty()), resColor("thermal"), 100f, sel == "thermal") { sel = "thermal" }
                Spacer(Modifier.height(12.dp))
            }
            // detail
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 6.dp, end = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (sel) {
                    "cpu" -> {
                        DetailTitle("CPU", cpuName)
                        TMGraph("% Utilization", "100%", listOf(h["cpu"].orEmpty()), listOf(resColor("cpu")), 100f)
                        StatGrid(listOf("Utilization" to "${s.cpu}%", "Speed" to if (avgMhz > 0) "%.2f GHz".format(avgMhz / 1000f) else "—", "Processes" to "${s.procs}",
                            "Temperature" to if (s.cpuTemp > 0) Opt.temp(s.cpuTemp) else "—", "Up time" to Extra.uptime(), "Cores" to "${s.cores.size}"))
                        PCard { InfoRows(Extra.cpuRows()) }
                        if (s.cores.isNotEmpty()) {
                            Text("Logical processors", color = T.sub, fontSize = 12.sp)
                            s.cores.indices.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { i -> Column(Modifier.weight(1f)) {
                                        Graph(listOf(h["c$i"].orEmpty()), listOf(resColor("cpu")), 100f, height = 40.dp, grid = false)
                                        Text("CPU $i · ${s.cores[i].usage}% · ${s.cores[i].mhz} MHz", color = T.sub, fontSize = 10.sp, maxLines = 1)
                                    } }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else Text("Per-core detail needs root.", color = T.sub, fontSize = 12.sp)
                    }
                    "memory" -> {
                        DetailTitle("Memory", mb(s.ramTotalMb))
                        TMGraph("Memory usage", mb(s.ramTotalMb), listOf(h["ram"].orEmpty()), listOf(resColor("memory")), 100f)
                        Text("Memory composition", color = T.sub, fontSize = 12.sp)
                        BarMeter(memPct / 100f, resColor("memory"), h = 22.dp)
                        StatGrid(listOf("In use" to mb(s.ramUsedMb), "Available" to mb(s.ramTotalMb - s.ramUsedMb), "Cached" to mb(s.ramCachedMb), "Swap" to if (s.swapTotalMb > 0) "${mb(s.swapUsedMb)} / ${mb(s.swapTotalMb)}" else "none"))
                    }
                    "disk" -> {
                        DetailTitle("Storage", "/data")
                        Text("Capacity in use", color = T.sub, fontSize = 12.sp)
                        BarMeter(diskPct / 100f, resColor("disk"), h = 22.dp)
                        StatGrid(listOf("Used" to "$diskPct%"))
                        PCard { InfoRows(sec("Storage")) }
                    }
                    "net" -> {
                        DetailTitle("Network", "Wi-Fi / mobile")
                        TMGraph("Receive (top) and send", rate(netMax), listOf(h["down"].orEmpty(), h["up"].orEmpty()), listOf(resColor("net"), Color(0xFFE5B80B)), netMax)
                        StatGrid(listOf("Receive" to rate(s.down.toFloat()), "Send" to rate(s.up.toFloat()), "Total received" to "%.1f MB".format(s.rxTotal / 1048576f), "Total sent" to "%.1f MB".format(s.txTotal / 1048576f)))
                        val ips = Extra.netRows()
                        if (ips.isNotEmpty() || sec("Wi-Fi").isNotEmpty()) PCard { InfoRows(sec("Wi-Fi") + ips.map { "IPv4 (${it.first})" to it.second }) }
                    }
                    "gpu" -> {
                        DetailTitle("GPU", remember { Extra.gpuName() })
                        if (s.gpu >= 0) {
                            TMGraph("% Utilization", "100%", listOf(h["gpu"].orEmpty()), listOf(resColor("gpu")), 100f)
                            StatGrid(listOf("Utilization" to "${s.gpu}%"))
                        } else PCard { Text("GPU usage cannot be read on this phone. Adreno (kgsl) and Mali phones usually allow it with root.", color = T.sub, fontSize = 13.sp) }
                    }
                    "battery" -> {
                        DetailTitle("Battery", if (s.charging) "charging" else "not charging")
                        TMGraph("% Charge", "100%", listOf(h["bat"].orEmpty()), listOf(resColor("battery")), 100f)
                        StatGrid(listOf("Charge" to "${s.batPct}%", "Current" to "${s.batMa} mA", "Voltage" to "%.2f V".format(s.batV), "Temperature" to Opt.temp(s.batTemp)))
                        PCard { InfoRows(sec("Battery")) }
                    }
                    else -> {
                        DetailTitle("Thermal", "temperature zones")
                        TMGraph("Hottest zone", "100°", listOf(h["temp"].orEmpty()), listOf(resColor("thermal")), 100f)
                        val z = sec("Temperatures")
                        if (z.isEmpty()) PCard { Text("Temperature sensors cannot be read (root may be needed).", color = T.sub, fontSize = 13.sp) }
                        z.forEach { (k, v) ->
                            Column { Row { Text(k, Modifier.weight(1f), color = T.text, fontSize = 13.sp); Text(v, color = T.sub, fontSize = 13.sp) }
                                BarMeter(((v.filter { it.isDigit() || it == '.' }).toFloatOrNull() ?: 0f) / 100f, resColor("thermal"), h = 6.dp) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PerfItem(title: String, line: String, series: List<List<Float>>, color: Color, max: Float, on: Boolean, onClick: () -> Unit) {
    val t = T
    val shape = RoundedCornerShape(t.radius.coerceAtMost(8.dp))
    Row(Modifier.fillMaxWidth().clip(shape).background(if (on) (if (t.dark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f)) else Color.Transparent)
        .then(if (on && t.tabs) Modifier.border(1.dp, color, shape) else Modifier).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Windows 11's selection bar
        Box(Modifier.width(3.dp).height(if (on && !t.tabs) 30.dp else 0.dp).background(Look.accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(if (on && !t.tabs) 4.dp else 7.dp))
        Box(Modifier.width(38.dp).height(34.dp).border(1.dp, color).background(color.copy(alpha = .06f))) {
            if (series.isNotEmpty()) Graph(series, listOf(color, Color(0xFFE5B80B)), max, height = 32.dp, grid = false)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PIcon("disk", color, 18.dp) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(line, color = T.sub, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun DetailTitle(title: String, right: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, color = T.text, fontSize = 24.sp, modifier = Modifier.weight(1f))
        Text(right, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
    }
}

/** The big graph with its scale labels ("% Utilization … 100%", "60 seconds … 0"), framed in the resource colour. */
@Composable
private fun TMGraph(title: String, top: String, series: List<List<Float>>, colors: List<Color>, max: Float) {
    Column {
        Row { Text(title, Modifier.weight(1f), color = T.sub, fontSize = 11.sp); Text(top, color = T.sub, fontSize = 11.sp) }
        Graph(series, colors, max, height = 150.dp)
        Row { Text("60 seconds", Modifier.weight(1f), color = T.sub, fontSize = 11.sp); Text("0", color = T.sub, fontSize = 11.sp) }
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEachIndexed { i, (k, v) -> Stat(k, v, true, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
