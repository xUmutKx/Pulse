package com.umutk.pulse

import com.umutk.pulse.features.AppManagerScreen
import com.umutk.pulse.features.NetworksSection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PHead(text: String, trailing: String = "", guide: String = "") {
    var info by remember { mutableStateOf(false) }
    if (info) com.umutk.pulse.features.GuideDialog(guide) { info = false }
    val infoBtn: @Composable () -> Unit = { if (guide.isNotEmpty()) androidx.compose.foundation.layout.Box(Modifier.size(32.dp).clickable { info = true }, contentAlignment = Alignment.Center) { PIcon("info", T.sub, 20.dp) } }
    if (oem) { OemTitle(text, infoBtn); return }
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        infoBtn()
    }
}

@Composable
private fun SecCard(sc: Section) = PCard {
    Text(sc.title, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    InfoRows(sc.rows)
}

/** A scrolling page: a title, optional content on top, then cards of rows that reload every [everyMs] (0 = once). */
@Composable
fun LiveSections(title: String, everyMs: Long = 3000, top: @Composable ColumnScope.() -> Unit = {}, load: (android.content.Context) -> List<Section>) {
    val ctx = LocalContext.current
    var secs by remember { mutableStateOf<List<Section>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            secs = withContext(Dispatchers.IO) { try { load(ctx) } catch (e: Throwable) { listOf(Section("Error", listOf("Could not read" to (e.message ?: e.javaClass.simpleName)))) } }
            if (everyMs <= 0) break
            delay(everyMs)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead(title)
        top()
        if (secs.isEmpty()) Text("Reading…", color = T.sub, fontSize = 13.sp)
        secs.forEach { SecCard(it) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Tile(label: String, value: String, color: Color, frac: Float, modifier: Modifier) {
    PCard(modifier) {
        Text(label, color = T.sub, fontSize = 12.sp)
        FitText(value, T.text, 24.sp)
        BarMeter(frac, color)
    }
}

// ---------------------------------------------------------------- dashboard

@Composable
fun DashboardPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    val h by Sampler.history.collectAsState()
    val storage = remember { try { val st = android.os.StatFs(android.os.Environment.getDataDirectory().path); 1f - st.availableBytes.toFloat() / st.totalBytes } catch (e: Exception) { 0f } }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ramPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Overview", if (s.root) "root" else "")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Processor", "%${s.cpu}", resColor("cpu"), s.cpu / 100f, Modifier.weight(1f))
            Tile("Memory", "%$ramPct", resColor("memory"), ramPct / 100f, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Temperature", Opt.temp(s.cpuTemp), resColor("thermal"), (s.cpuTemp / 90f), Modifier.weight(1f))
            Tile("Battery", "%${s.batPct}" + "", resColor("battery"), s.batPct / 100f, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Storage", "%${(storage * 100).toInt()}", resColor("disk"), storage, Modifier.weight(1f))
            Tile("Graphics", if (s.gpu >= 0) "%${s.gpu}" else "—", resColor("gpu"), s.gpu.coerceAtLeast(0) / 100f, Modifier.weight(1f))
        }
        PCard {
            Text("Network", color = T.sub, fontSize = 12.sp)
            Row { Text("↓ ${rate(s.down.toFloat())}", color = resColor("net"), fontSize = 16.sp, modifier = Modifier.weight(1f)); Text("↑ ${rate(s.up.toFloat())}", color = resColor("net"), fontSize = 16.sp) }
            Graph(listOf(h["down"].orEmpty(), h["up"].orEmpty()), listOf(resColor("net"), resColor("gpu")), maxOf(1024f, (h["down"].orEmpty() + h["up"].orEmpty()).maxOrNull() ?: 1f) * 1.2f, height = 70.dp)
        }
        PCard {
            Text("Processor history (60 s)", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["cpu"].orEmpty()), listOf(resColor("cpu")), 100f, height = 70.dp)
            Text("Memory history", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["ram"].orEmpty()), listOf(resColor("memory")), 100f, height = 70.dp)
        }
        PCard {
            Text("Quick actions", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (s.root) "Root is available: these work." else "These need root.", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Drop RAM cache") { scope.launch { withContext(Dispatchers.IO) { Shell.run("sync; echo 3 > /proc/sys/vm/drop_caches") }; msg = "RAM cache dropped" } }
                PButton("App caches") { scope.launch { withContext(Dispatchers.IO) { Shell.run("pm trim-caches 999G") }; msg = "App caches trimmed" } }
                PButton("Kill background apps") { scope.launch { withContext(Dispatchers.IO) { Shell.run("am kill-all") }; msg = "Background apps killed" } }
            }
            if (msg.isNotEmpty()) Text(msg, color = Look.accent, fontSize = 12.sp)
        }
        PCard { InfoRows(listOf("Processes" to "${s.procs}", "Up time" to hms(android.os.SystemClock.elapsedRealtime()), "Swap" to "${s.swapUsedMb} / ${s.swapTotalMb} MB", "Cached" to mb(s.ramCachedMb))) }
        // the guardian pages, below the numbers: thermal, battery, storage, memory
        PCard {
            val st = remember { try { android.os.StatFs(android.os.Environment.getDataDirectory().path) } catch (e: Exception) { null } }
            val total = st?.totalBytes ?: 0L
            val free = st?.availableBytes ?: 0L
            Text("Storage", color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            BarMeter(if (total > 0) 1f - free.toFloat() / total else 0f, resColor("disk"), h = 10.dp)
            Text("${Guard.gb(total - free)} of ${Guard.gb(total)} used · ${Guard.gb(free)} free", color = T.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- battery / network / thermal

@Composable
fun BatteryPage() {
    val h by Sampler.history.collectAsState()
    LiveSections("Battery", 2000, top = {
        PCard {
            Text("Current (mA)", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["ma"].orEmpty()), listOf(resColor("battery")), maxOf(500f, (h["ma"].orEmpty().maxOrNull() ?: 0f) * 1.2f), height = 80.dp)
            Text("Level (%)", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["bat"].orEmpty()), listOf(resColor("cpu")), 100f, height = 60.dp)
        }
    }) { Hw.battery(it) }
}

@Composable
fun NetworkPage() {
    val h by Sampler.history.collectAsState()
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LiveSections("Network", 4000, top = {
        PCard {
            Graph(listOf(h["down"].orEmpty(), h["up"].orEmpty()), listOf(resColor("net"), resColor("gpu")), maxOf(1024f, (h["down"].orEmpty() + h["up"].orEmpty()).maxOrNull() ?: 1f) * 1.2f, height = 80.dp)
            Text("Orange: download · Blue: upload", color = T.sub, fontSize = 11.sp)
        }
        PCard {
            Text("Latency test", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PButton(if (busy) "Measuring…" else "Start") {
                if (!busy) { busy = true; scope.launch {
                    val targets = listOf("Cloudflare" to ("1.1.1.1" to 443), "Google DNS" to ("8.8.8.8" to 53), "Google" to ("google.com" to 443))
                    results = withContext(Dispatchers.IO) {
                        targets.map { (n, hp) ->
                            val r = Hw.ping(hp.first, hp.second); val ok = r.filter { it >= 0 }
                            if (ok.isEmpty()) "$n: unreachable" else "$n: ${ok.min()} / ${ok.average().toInt()} / ${ok.max()} ms (min / avg / max), lost ${r.size - ok.size}/${r.size}"
                        }
                    }
                    busy = false
                } }
            }
            results.forEach { Text(it, color = T.text, fontSize = 13.sp) }
        }
        NetworksSection()
    }) { Hw.network(it) }
}

@Composable
fun ThermalPage() {
    val h by Sampler.history.collectAsState()
    LiveSections("Thermal", 2500, top = {
        PCard {
            Text("Hottest zone", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["temp"].orEmpty()), listOf(resColor("thermal")), 100f, height = 70.dp)
        }
    }) { Hw.thermal(it) }
}

// ---------------------------------------------------------------- display with frame rate meter

@Composable
fun DisplayPage() {
    var fps by remember { mutableStateOf(listOf<Float>()) }
    DisposableEffect(Unit) {
        val ch = android.view.Choreographer.getInstance()
        var frames = 0; var t0 = 0L
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(ns: Long) {
                if (t0 == 0L) t0 = ns
                frames++
                if (ns - t0 >= 1_000_000_000L) { fps = (fps + (frames * 1e9f / (ns - t0))).takeLast(60); frames = 0; t0 = ns }
                ch.postFrameCallback(this)
            }
        }
        ch.postFrameCallback(cb)
        onDispose { ch.removeFrameCallback(cb) }
    }
    LiveSections("Display", 5000, top = {
        PCard {
            Text("Frame rate (this screen): ${"%.0f".format(fps.lastOrNull() ?: 0f)} FPS", color = T.text, fontSize = 15.sp)
            Graph(listOf(fps), listOf(resColor("gpu")), 144f, height = 60.dp)
            Text("When the screen is still the system may lower the frame rate; scroll to see it rise.", color = T.sub, fontSize = 11.sp)
        }
    }) { Hw.display(it) }
}

// ---------------------------------------------------------------- cores

@Composable
fun CoresPage() {
    val s by Sampler.snap.collectAsState()
    val h by Sampler.history.collectAsState()
    var info by remember { mutableStateOf<List<Hw.CoreInfo>>(emptyList()) }
    LaunchedEffect(Unit) { while (true) { info = withContext(Dispatchers.IO) { Hw.cores() }; delay(3000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Cores", "${s.cores.size} cores")
        // each core keeps its own colour; tapping a legend entry hides or shows that core's line
        var hidden by remember { mutableStateOf(setOf<Int>()) }
        val coreColor = { i: Int -> Color.hsv(i * 360f / maxOf(1, s.cores.size), .6f, .85f) }
        val visible = s.cores.indices.filter { it !in hidden }
        PCard {
            Graph(visible.map { h["c$it"].orEmpty() }, visible.map(coreColor), 100f, height = 90.dp)
            s.cores.indices.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        val on = i !in hidden
                        Row(Modifier.weight(1f).clickable { hidden = if (on) hidden + i else hidden - i }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(if (on) coreColor(i) else Color.Transparent, CircleShape).border(1.5.dp, coreColor(i), CircleShape))
                            Spacer(Modifier.width(5.dp))
                            Text("CPU $i", color = if (on) T.text else T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        s.cores.forEachIndexed { i, c ->
            val ci = info.getOrNull(i)
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CPU $i", color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (ci?.online == false) "offline" else "${c.mhz} MHz · %${c.usage}", color = T.sub, fontSize = 13.sp)
                }
                BarMeter(c.usage / 100f, resColor("cpu"))
                if (ci != null) Text("${ci.gov} · ${ci.minMhz}–${ci.maxMhz} MHz", color = T.sub, fontSize = 11.sp)
            }
        }
        PCard { InfoRows(Extra.cpuRows()) }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- benchmark

@Composable
fun BenchPage() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    val res = remember { mutableStateMapOf<String, String>().also { m -> listOf("cpu1", "cpuN", "mem", "wr", "rd").forEach { k -> sp.getString("b_$k", null)?.let { v -> m[k] = v } } } }
    fun run(key: String, f: () -> String) { if (busy.isNotEmpty()) return; busy = key; scope.launch { val r = withContext(Dispatchers.Default) { f() }; res[key] = r; sp.edit().putString("b_$key", r).apply(); busy = "" } }
    fun cpu(threads: Int): String {
        val t = android.os.SystemClock.elapsedRealtime()
        val ex = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val fs = (0 until threads).map { ex.submit<Int> { var c = 0; for (n in 2..150000) { var p = true; var d = 2; while (d * d <= n) { if (n % d == 0) { p = false; break }; d++ }; if (p) c++ }; c } }
        fs.forEach { it.get() }; ex.shutdown()
        val ms = (android.os.SystemClock.elapsedRealtime() - t).coerceAtLeast(1)
        return "${threads * 1_000_000L / ms} points ($ms ms)"
    }
    fun memory(): String {
        val a = ByteArray(32 shl 20); val b = ByteArray(32 shl 20)
        val t = android.os.SystemClock.elapsedRealtime()
        repeat(12) { a.copyInto(b) }
        val ms = (android.os.SystemClock.elapsedRealtime() - t).coerceAtLeast(1)
        return "${32 * 12 * 1000L / ms} MB/s"
    }
    fun disk(write: Boolean): String {
        val f = java.io.File(ctx.cacheDir, "pulse_bench.bin"); val chunk = ByteArray(1 shl 20) { it.toByte() }
        return try {
            if (write) {
                val t = android.os.SystemClock.elapsedRealtime()
                java.io.FileOutputStream(f).use { o -> repeat(96) { o.write(chunk) }; o.fd.sync() }
                "${96 * 1000L / (android.os.SystemClock.elapsedRealtime() - t).coerceAtLeast(1)} MB/s"
            } else {
                if (!f.exists()) return "run the write test first"
                val t = android.os.SystemClock.elapsedRealtime()
                java.io.FileInputStream(f).use { i -> while (i.read(chunk) > 0) { } }
                val r = "${f.length() / 1048576 * 1000L / (android.os.SystemClock.elapsedRealtime() - t).coerceAtLeast(1)} MB/s (may come from cache)"
                f.delete(); r
            }
        } catch (e: Exception) { "error: ${e.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Benchmark")
        Text("Short tests. Compare your own phone before and after a change, not against other phones.", color = T.sub, fontSize = 12.sp)
        listOf(
            Triple("cpu1", "Processor · single core", { cpu(1) }),
            Triple("cpuN", "Processor · all cores", { cpu(Runtime.getRuntime().availableProcessors()) }),
            Triple("mem", "Memory speed", { memory() }),
            Triple("wr", "Storage · write (96 MB)", { disk(true) }),
            Triple("rd", "Storage · read", { disk(false) }),
        ).forEach { (k, name, f) ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = T.text, fontSize = 14.sp)
                        Text(if (busy == k) "running…" else res[k] ?: "not run yet", color = Look.accent, fontSize = 15.sp)
                    }
                    PButton("Run") { run(k, f) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- apps

@Composable
fun AppsPage() {
    val open = LocalOpenTool.current
    // tablet: app list on the left, the picked app's details on the right; phone: details open as a sub screen
    var sel by rememberSaveable { mutableStateOf("") }
    if (com.umutk.pulse.ui.winClass() == com.umutk.pulse.ui.WinClass.Expanded) com.umutk.pulse.ui.ListDetail(
        hasDetail = sel.isNotEmpty(), listWidth = 380.dp,
        list = { AppManagerScreen(onOpen = { sel = it }) },
        detail = { if (sel.isNotEmpty()) key(sel) { com.umutk.pulse.features.AppDetailsScreen(sel, { sel = "" }) } })
    else com.umutk.pulse.ui.MaxWidthBox { AppManagerScreen(onOpen = { open("app:$it") }) }
}

// ---------------------------------------------------------------- searchable lists

@Composable
private fun SearchList(title: String, items: List<Pair<String, String>>) {
    var q by remember { mutableStateOf("") }
    val shown = remember(items, q) { items.filter { q.isBlank() || it.first.contains(q, true) || it.second.contains(q, true) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            PHead(title, "${shown.size} / ${items.size}")
            PField(q, { q = it }, "Search", Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        items(shown) { (k, v) -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(k, Modifier.weight(1f), color = T.sub, fontSize = 12.sp); Text(v, Modifier.weight(1f), color = T.text, fontSize = 12.sp) } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun FeaturesPage() {
    val ctx = LocalContext.current
    var l by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { l = withContext(Dispatchers.IO) { Hw.features(ctx).map { it to "present" } } }
    SearchList("Hardware features", l)
}

@Composable
fun PropsPage() {
    var l by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { l = withContext(Dispatchers.IO) { Hw.props() } }
    SearchList("System properties", l)
}

// ---------------------------------------------------------------- storage with folder sizes

@Composable
fun StoragePage() {
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LiveSections("Storage", 0, top = {
        PCard {
            Text("Folder sizes", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            PButton(if (busy) "Calculating…" else "Calculate") { if (!busy) { busy = true; scope.launch { folders = withContext(Dispatchers.IO) { Hw.folderSizes() }; busy = false } } }
            folders?.let { f -> if (f.isEmpty()) Text("Could not read (storage permission may be needed)", color = T.sub, fontSize = 12.sp) else InfoRows(f.map { it.first to "%.1f MB".format(it.second / 1048576f) }) }
        }
    }) { Hw.storage(it) }
}
