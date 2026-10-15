package com.umutk.pulse

import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.features.AccessNeededCard
import com.umutk.pulse.features.ownProcesses
import com.umutk.pulse.features.ProcStats
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The heat-map behind a cell, as in Task Manager: Windows 10 and older use the yellow-to-orange ramp, Windows 11 / Material tint with the accent colour. */
@Composable
private fun heatBg(f: Float): Color {
    val t = T
    if (f < .02f || win95Authentic || xpAuthentic) return Color.Transparent // 95 / XP lists had no heat colours
    if (!t.dark && (t.id == "win10" || t.id == "win7" || t.id == "xp" || t.id == "win95"))
        return when { f < .1f -> Color(0xFFFFF4C4); f < .3f -> Color(0xFFFFE8A3); f < .6f -> Color(0xFFFFCF70); else -> Color(0xFFFFA45C) }
    val lo = if (t.dark) .14f else .09f; val span = if (t.dark) .5f else .36f
    return Look.accent.copy(alpha = lo + f.coerceIn(0f, 1f) * span)
}

@Composable
private fun HeatCell(text: String, f: Float, width: Dp, bold: Boolean = false, ink: Color = T.text) {
    Box(Modifier.width(width).fillMaxHeight().clipToBounds().background(heatBg(f)), contentAlignment = Alignment.CenterEnd) {
        Text(text, color = ink, fontSize = if (win95Authentic) 12.sp else 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(end = 8.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private class PRow(val p: Proc, val app: Boolean, val label: String, val uid: Int = -1)

// ---------------------------------------------------------------- Processes

@Composable
fun ProcessesPage() {
    val ctx = LocalContext.current
    val openTool = LocalOpenTool.current
    val s by Sampler.snap.collectAsState()
    ColW.load(ctx)
    var rows by remember { mutableStateOf<List<PRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(-1) }
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("mem") }
    var ask by remember { mutableStateOf<PRow?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    var net by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }     // uid -> bytes/s
    var sizes by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }   // uid -> app + data bytes
    LaunchedEffect(Unit) {
        while (true) {
            rows = withContext(Dispatchers.IO) { Sampler.processes().ifEmpty { ownProcesses(ctx) }.map { PRow(it, Extra.isApp(ctx, it.name), labelOf(ctx, it.name), ProcStats.uidOf(ctx, it.name)) } }
            loaded = true
            net = withContext(Dispatchers.IO) { ProcStats.netRates(ctx) }
            sizes = withContext(Dispatchers.IO) { rows.map { it.uid }.distinct().mapNotNull { u -> ProcStats.size(ctx, u)?.let { u to it } }.toMap() }
            delay(3000)
        }
    }
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val diskPct = try { android.os.StatFs(android.os.Environment.getDataDirectory().path).let { 100 * (it.totalBytes - it.availableBytes) / it.totalBytes.coerceAtLeast(1) }.toInt() } catch (e: Exception) { 0 }
    val shown =rows.filter { q.isBlank() || it.label.contains(q, true) || it.p.name.contains(q, true) || it.p.pid.toString() == q }
    // processes of one app (com.x, com.x:service, com.x:push) fold into a single line, as Task Manager does
    val groups = shown.groupBy { it.p.name.substringBefore(':') }.values.map { PGroup(it) }
        .let { l -> when (sort) {
            "cpu" -> l.sortedByDescending { it.cpu }; "name" -> l.sortedBy { it.title.lowercase() }; "pid" -> l.sortedBy { it.head.p.pid }
            "net" -> l.sortedByDescending { net[it.uid] ?: 0f }; "disk" -> l.sortedByDescending { sizes[it.uid] ?: 0L }
            else -> l.sortedByDescending { it.mem } } }
    val selRow = rows.firstOrNull { it.p.pid == sel }
    val hs = rememberScrollState()
    fun end(r: PRow) { scope.launch { withContext(Dispatchers.IO) { Sampler.kill(r.p) }; ask = null; sel = -1 } }
    ask?.let { r -> PDialog("End ${r.label}?", "Unsaved data in this process will be lost. (PID ${r.p.pid})", "End process", { end(r) }, { ask = null }) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (win95Authentic) Text("Processes", color = Color.Black, fontSize = 13.sp, modifier = Modifier.weight(1f))
            else Text("Processes", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // see-through like the other buttons, with a soft shadow and a lighter edge so it reads as floating above the list
            if (selRow != null && win95Authentic) PButton("End Task") { if (Cfg.confirmKill) ask = selRow else end(selRow) }
            else if (selRow != null) {
                val shape = RoundedCornerShape(if (T.material) 50 else 6)
                Box(Modifier.shadow(8.dp, shape, clip = false).clip(shape).background(T.text.copy(alpha = .06f)).border(1.dp, T.text.copy(alpha = .45f), shape)
                    .clickable { if (Cfg.confirmKill) ask = selRow else end(selRow) }.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text("End task", color = Color(0xFFC42B1C), fontSize = 13.sp)
                }
            }
        }
        PField(q, { q = it }, "Type a name or PID to search", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        // column heads with the totals on top, like the real one; tap to sort. Name stays put, the numbers scroll sideways.
        // 95: header buttons (grey, raised) on top of a white sunken list
        Row(Modifier.fillMaxWidth().height(if (win95Authentic) 44.dp else 58.dp).then(if (win95Authentic) Modifier.padding(horizontal = 2.dp) else if (xpAuthentic) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFEBEADB)))).border(1.dp, Color(0xFFD6D2C2)) else Modifier.border(BorderStroke(1.dp, T.line.copy(alpha = .6f))))) { // XP: list-view header, white to beige
            Box(Modifier.width(ColW.w("name")).fillMaxHeight()) {
                Box(Modifier.fillMaxSize().clickable { sort = "name" }.then(if (win95Authentic) Modifier.background(Color(0xFFC0C0C0)).bevel(true) else Modifier).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Name", color = if (win95Authentic) Color.Black else if (sort == "name") Look.accent else T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ColHandle("name", Modifier.align(Alignment.CenterEnd))
            }
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hs)) {
                HeadCell("${s.cpu}%", "CPU", "cpu", sort == "cpu", heatBg(s.cpu / 100f)) { sort = "cpu" }
                HeadCell("$memPct%", "Memory", "mem", sort == "mem", heatBg(memPct / 100f)) { sort = "mem" }
                HeadCell(rate((s.down + s.up).toFloat()), "Network", "net", sort == "net", resColor("net").copy(alpha = .25f)) {
                    // a tap sorts, a double tap opens Network usage
                    sort = "net"
                    val now = System.currentTimeMillis()
                    if (now - NetTap.last < 400) openTool("netusage")
                    NetTap.last = now
                }
                HeadCell("$diskPct%", "Storage", "disk", sort == "disk", resColor("disk").copy(alpha = .25f)) { sort = "disk" }
                HeadCell("", "Status", "status", false, Color.Transparent) {}
                HeadCell("", "PID", "pid", sort == "pid", Color.Transparent) { sort = "pid" }
                HeadCell("", "User", "user", false, Color.Transparent) {}
                HeadCell("", "Virtual", "virt", false, Color.Transparent) {}
                HeadCell("", "Priority", "prio", false, Color.Transparent) {}
                HeadCell("", "Parent", "ppid", false, Color.Transparent) {}
            }
        }
        AccessNeededCard(tr("Processes", "İşlemler"), tr("Showing this app's own processes only. Other apps' processes, CPU and virtual size need root or Shizuku.", "Yalnızca bu uygulamanın kendi işlemleri gösteriliyor. Diğer uygulamaların işlemleri, CPU ve sanal bellek için root veya Shizuku gerekir."), Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (loaded && rows.isEmpty()) Center(tr("No processes could be read", "İşlemler okunamadı"))
        else LazyColumn(Modifier.fillMaxSize().then(if (win95Authentic) Modifier.padding(horizontal = 2.dp).background(Color.White).bevel(false).padding(2.dp) else Modifier)) {
            fun section(id: String, title: String, list: List<PGroup>) {
                if (list.isEmpty()) return
                val open = id !in collapsed
                item(key = "h$id") {
                    Row(Modifier.fillMaxWidth().clickable { collapsed = if (open) collapsed + id else collapsed - id }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (open) "⌄" else "›", color = T.sub, fontSize = 16.sp, modifier = Modifier.width(18.dp))
                        if (win95Authentic) Text("$title (${list.size})", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        else Text("$title (${list.size})", color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (open) list.take(120).forEach { g ->
                    val many = g.rows.size > 1
                    val isOpen = g.key in expanded
                    item(key = "g${g.key}") {
                        val on = !many && sel == g.head.p.pid
                        Row(Modifier.fillMaxWidth().height(40.dp).background(if (on && xpAuthentic) Color(0xFF316AC5) else if (on && !win95Authentic) Look.accent.copy(alpha = .16f) else Color.Transparent).clickable { if (many) expanded = if (isOpen) expanded - g.key else expanded + g.key else sel = if (on) -1 else g.head.p.pid }, verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(ColW.w("name")).clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (many) (if (isOpen) "⌄" else "›") else "", color = T.sub, fontSize = 16.sp, modifier = Modifier.width(26.dp).padding(start = 8.dp))
                                AppIcon(g.head.p.name, 22.dp); Spacer(Modifier.width(8.dp))
                                // 95 list view: the selected item's label is navy with white text
                                if (win95Authentic) Text(if (many) "${g.title} (${g.rows.size})" else g.head.label, color = if (on) Color.White else Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.background(if (on) Color(0xFF000080) else Color.Transparent).padding(horizontal = 2.dp))
                                else Text(if (many) "${g.title} (${g.rows.size})" else g.head.label, color = if (on && xpAuthentic) Color.White else T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hs)) { Cells(g.cpu, g.mem, if (many) null else g.head.p, if (on && xpAuthentic) Color.White else T.text, net[g.uid], sizes[g.uid]) }
                        }
                    }
                    if (many && isOpen) items(g.rows.sortedByDescending { it.p.rssMb }, key = { "p${it.p.pid}" }) { r ->
                        val on = sel == r.p.pid
                        Row(Modifier.fillMaxWidth().height(36.dp).background(if (on && xpAuthentic) Color(0xFF316AC5) else if (on && !win95Authentic) Look.accent.copy(alpha = .16f) else Color.Transparent).clickable { sel = if (on) -1 else r.p.pid }, verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(ColW.w("name")).clipToBounds(), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(52.dp))
                                Text(r.p.name.substringAfter(':', r.p.name.substringAfterLast('.')), color = if (on && (win95Authentic || xpAuthentic)) Color.White else T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = if (on && win95Authentic) Modifier.background(Color(0xFF000080)).padding(horizontal = 2.dp) else Modifier)
                            }
                            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hs)) { Cells(r.p.cpu, r.p.rssMb, r.p, if (on && xpAuthentic) Color.White else T.text) }
                        }
                    }
                }
            }
            section("apps", "Apps", groups.filter { g -> g.rows.any { it.app } }); section("bg", "Background processes", groups.filter { g -> g.rows.none { it.app } })
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}


private class PGroup(val rows: List<PRow>) {
    val head = rows.maxByOrNull { it.p.rssMb }!!
    val key = rows[0].p.name.substringBefore(':')
    val title = (rows.firstOrNull { !it.p.name.contains(':') } ?: head).label.substringBefore(" (")
    val cpu = rows.sumOf { it.p.cpu.toDouble() }.toFloat()
    val mem = rows.sumOf { it.p.rssMb }
    val uid = rows[0].uid
}

@Composable
private fun HeadCell(top: String, name: String, key: String, active: Boolean, bg: Color, onClick: () -> Unit) {
    val w = ColW.w(key)
    Box(Modifier.width(w).fillMaxHeight()) {
        HeadCellBody(top, name, w, active, bg, onClick)
        ColHandle(key, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun HeadCellBody(top: String, name: String, w: Dp, active: Boolean, bg: Color, onClick: () -> Unit) {
    if (win95Authentic) { // 95 column header: a grey raised button, black text, total above the name
        Column(Modifier.width(w).fillMaxHeight().clickable(onClick = onClick).background(Color(0xFFC0C0C0)).bevel(true).padding(top = 4.dp, end = 6.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Top) {
            if (top.isNotEmpty()) Text(top, color = Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(name, color = Color.Black, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(Modifier.width(w).fillMaxHeight().clickable(onClick = onClick).background(bg).padding(top = 4.dp, end = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Top) {
        if (top.isNotEmpty()) Text(top, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(name, color = if (active) Look.accent else T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Cells(cpu: Float, mem: Int, p: Proc?, ink: Color = T.text, net: Float? = null, size: Long? = null) {
    HeatCell("%.1f%%".format(cpu), cpu / 100f, ColW.w("cpu"), ink = ink)
    HeatCell(mb(mem), mem / 1500f, ColW.w("mem"), ink = ink)
    // speed right now (0 shows as "0 KB/s" so a quiet app reads as measured, not missing); heat from 1 MB/s
    HeatCell(net?.let { rate(it) } ?: "", (net ?: 0f) / 1048576f, ColW.w("net"), ink = ink)
    HeatCell(size?.let { sizeText(it) } ?: "", 0f, ColW.w("disk"), ink = ink)
    HeatCell(if (p == null) "" else if (p.state == "R") "Running" else "Sleeping", 0f, ColW.w("status"), ink = ink)
    HeatCell(p?.pid?.toString() ?: "", 0f, ColW.w("pid"), ink = ink)
    HeatCell(p?.user ?: "", 0f, ColW.w("user"), ink = ink)
    HeatCell(if (p == null) "" else mb(p.vszMb), 0f, ColW.w("virt"), ink = ink)
    HeatCell(p?.nice?.toString() ?: "", 0f, ColW.w("prio"), ink = ink)
    HeatCell(p?.ppid?.toString() ?: "", 0f, ColW.w("ppid"), ink = ink)
}

private fun sizeText(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "%.0f MB".format(b / 1048576.0)

/** Shared, persisted column widths of the Processes table (header and rows read the same state). */
private object ColW {
    private val def = mapOf("name" to 170, "cpu" to 64, "mem" to 84, "net" to 96, "disk" to 84, "status" to 76, "pid" to 60, "user" to 84, "virt" to 84, "prio" to 64, "ppid" to 64)
    private val map = mutableStateMapOf<String, Float>()
    private var loaded = false
    fun load(ctx: android.content.Context) {
        if (loaded) return
        loaded = true
        val sp = ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
        def.keys.forEach { k -> if (sp.contains("proc_col_$k")) map[k] = sp.getFloat("proc_col_$k", def[k]!!.toFloat()) }
    }
    fun w(k: String): Dp = (map[k] ?: def[k]!!.toFloat()).dp
    fun set(k: String, v: Float) { map[k] = v.coerceIn(48f, 360f) }
    fun save(ctx: android.content.Context, k: String) { map[k]?.let { ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putFloat("proc_col_$k", it).apply() } }
}

/** Drag handle on the right edge of a column head: the head's separator line (Material: a small pill) with a wide invisible touch area. */
@Composable
private fun ColHandle(key: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier.width(20.dp).fillMaxHeight().pointerInput(key) {
        detectHorizontalDragGestures(onDragEnd = { ColW.save(ctx, key) }, onDragCancel = { ColW.save(ctx, key) }) { ch, dx ->
            ch.consume()
            ColW.set(key, ColW.w(key).value + dx / dens.density)
        }
    }, contentAlignment = Alignment.CenterEnd) {
        if (T.material) Box(Modifier.padding(end = 3.dp).width(4.dp).height(22.dp).clip(RoundedCornerShape(50)).background(T.sub.copy(alpha = .55f)))
        else Box(Modifier.fillMaxHeight(.7f).width(1.dp).background(T.line.copy(alpha = .9f)))
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
        val wide = maxWidth >= 600.dp
        val rows: List<@Composable () -> Unit> = listOf(
            { PerfItem("CPU", "${s.cpu}%${if (avgMhz > 0) " " + "%.2f GHz".format(avgMhz / 1000f) else ""}", listOf(h["cpu"].orEmpty()), resColor("cpu"), 100f, sel == "cpu") { sel = "cpu" } },
            { PerfItem("Memory", "${mb(s.ramUsedMb)}/${mb(s.ramTotalMb)} ($memPct%)", listOf(h["ram"].orEmpty()), resColor("memory"), 100f, sel == "memory") { sel = "memory" } },
            { PerfItem("Storage", "$diskPct% used", emptyList(), resColor("disk"), 100f, sel == "disk") { sel = "disk" } },
            { PerfItem("Network", "D ${rate(s.down.toFloat())}  U ${rate(s.up.toFloat())}", listOf(h["down"].orEmpty(), h["up"].orEmpty()), resColor("net"), netMax, sel == "net") { sel = "net" } },
            { PerfItem("GPU", if (s.gpu >= 0) "${s.gpu}%" else "—", listOf(h["gpu"].orEmpty()), resColor("gpu"), 100f, sel == "gpu") { sel = "gpu" } },
            { PerfItem("Battery", "${s.batPct}%  ${s.batMa} mA", listOf(h["bat"].orEmpty()), resColor("battery"), 100f, sel == "battery") { sel = "battery" } },
            { PerfItem("Thermal", if (s.cpuTemp > 0) Opt.temp(s.cpuTemp) else "—", listOf(h["temp"].orEmpty()), resColor("thermal"), 100f, sel == "thermal") { sel = "thermal" } },
        )
        // the detail page: its own scroll, full width under the list on a phone
        val detail: @Composable (Modifier) -> Unit = { m ->
            Column(m.verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 6.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        } else AccessNeededCard(tr("Per-core detail", "Çekirdek ayrıntısı"), tr("Overall CPU is shown above.", "Genel CPU yukarıda gösteriliyor."))
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
        Row(Modifier.fillMaxSize()) {
            // resource list on the left, detail on the right
            Column(Modifier.width(if (wide) 200.dp else 104.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { rows.forEach { it() } }
            detail(Modifier.weight(1f).fillMaxHeight())
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
        Box(Modifier.width(24.dp).height(30.dp).border(1.dp, color).background(color.copy(alpha = .06f))) {
            if (series.isNotEmpty()) Graph(series, listOf(color, Color(0xFFE5B80B)), max, height = 32.dp, grid = false)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PIcon("disk", color, 18.dp) }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            FitText(title, T.text, 13.sp, weight = FontWeight.Medium)
            // one line that shrinks to fit, so the value sits right after the name instead of wrapping away
            FitText(line, T.sub, 10.sp, minSize = 7f)
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

/** Time of the last tap on the Network column head, for the double tap. */
private object NetTap { var last = 0L }
