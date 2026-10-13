package com.umutk.pulse

import android.content.Intent
import com.umutk.pulse.features.themeCredit
import android.provider.Settings
import androidx.activity.compose.BackHandler
import com.umutk.pulse.features.DonutChart
import com.umutk.pulse.features.NoteCount
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- shared bits

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

/** The app's own launcher icon, loaded off the main thread and cached. The generic icon is only a fallback for packages that cannot be resolved. */
private fun loadAppIcon(ctx: android.content.Context, pkg: String): androidx.compose.ui.graphics.ImageBitmap? = iconCache[pkg] ?: try {
    ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap().also { iconCache[pkg] = it }
} catch (e: Exception) { null }

@Composable
fun AppIcon(name: String, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val ctx = LocalContext.current
    val pkg = name.substringBefore(':')
    val bmp by produceState(iconCache[pkg], pkg) { value = withContext(Dispatchers.IO) { loadAppIcon(ctx, pkg) } }
    val icon = bmp
    if (icon != null) Image(icon, null, Modifier.size(size)) else PIcon("processes", T.sub, size * 0.8f, Modifier.padding(2.dp))
}

fun labelOf(ctx: android.content.Context, name: String): String {
    val pkg = name.substringBefore(':')
    return try { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager).toString() + name.substringAfter(':', "").let { if (it.isEmpty()) "" else " ($it)" } } catch (e: Exception) { name }
}

@Composable
fun PButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val t = T
    if (t.tabs) {
        Box(modifier.background(t.bg).then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(t.radius))).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else t.text); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (danger) Color(0xFFB00020) else t.text, fontSize = 13.sp)
        }
    } else {
        val bg = if (!t.material) (if (danger) Color(0xFFC42B1C) else Look.accent) else if (danger) MaterialTheme.colorScheme.error else Look.accent
        val fg = if (!t.material) Color.White else if (danger) MaterialTheme.colorScheme.onError else onAccent(bg)
        Button(onClick, modifier, shape = if (t.material) CircleShape else RoundedCornerShape(t.radius.coerceAtMost(12.dp)), colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg), contentPadding = if (t.material) PaddingValues(horizontal = 24.dp, vertical = 10.dp) else PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text, fontSize = if (t.material) 14.sp else 13.sp, fontWeight = if (t.material) FontWeight.Medium else null)
        }
    }
}

@Composable
private fun Header(text: String, trailing: String = "", onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        if (onBack != null) Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.rotate(90f)) }
        Text(text, color = T.text, fontSize = 22.sp, fontWeight = if (T.material) FontWeight.Normal else FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = T.sub, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- app history

@Composable
fun HistoryPage() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Extra.usageGranted(ctx)) }
    var rows by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    var unlocks by remember { mutableStateOf(0) }
    var notes by remember { mutableStateOf(0) }
    var notesOn by remember { mutableStateOf(NoteCount.enabled(ctx)) }
    var pie by remember { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = Extra.usageGranted(ctx); notesOn = NoteCount.enabled(ctx) }
        }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(granted) {
        while (granted) {
            rows = withContext(Dispatchers.IO) { Extra.usageToday(ctx) }
            unlocks = withContext(Dispatchers.IO) { Extra.unlocksToday(ctx) }
            delay(10000)
        }
    }
    LaunchedEffect(Unit) { while (true) { notes = NoteCount.today(ctx); delay(5000) } }
    val total = rows.sumOf { it.ms }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Header("App history", "today")
        PCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                HistoryStat("Screen time", if (granted) hms(total) else "-")
                HistoryStat("Unlocks", if (granted) "$unlocks" else "-")
                HistoryStat("Notifications", if (notesOn) "$notes" else "Off")
            }
            if (!notesOn) PButton("Allow notification count") {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (!granted) {
            PCard {
                Text("Showing how long each app was open needs the \"usage access\" permission.", color = T.text, fontSize = 14.sp)
                PButton("Allow") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } else if (rows.isEmpty()) Center("Nothing recorded today")
        else {
            PButton(if (pie) "Show list" else "Show pie chart", Modifier.fillMaxWidth()) { pie = !pie }
            if (pie) {
                val slices = rows.take(8).map { it.label to it.ms } +
                    (rows.drop(8).sumOf { it.ms }.takeIf { it > 0 }?.let { listOf("Others" to it) }.orEmpty())
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    DonutChart(slices, Modifier.size(260.dp), centerText = hms(total))
                }
            } else {
                val top = rows.firstOrNull()?.ms?.coerceAtLeast(1) ?: 1
                LazyColumn(Modifier.weight(1f)) {
                    items(rows.take(80), key = { it.pkg }) { r ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(r.pkg, 26.dp); Spacer(Modifier.width(8.dp))
                                Text(r.label, Modifier.weight(1f), color = T.text, fontSize = 14.sp, maxLines = 1)
                                Text(hms(r.ms), color = T.sub, fontSize = 13.sp)
                            }
                            BarMeter(r.ms.toFloat() / top, Look.accent, Modifier.padding(start = 34.dp, top = 4.dp), h = 4.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = T.sub, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- startup apps

@Composable
fun StartupPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    var list by remember { mutableStateOf<List<Boot>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { list = withContext(Dispatchers.IO) { Startup.list(ctx) } }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Header("Startup apps", "${list.count { it.enabled }} enabled / ${list.size}")
        Text(if (s.root) "Apps that start themselves when the phone boots. Switch one off and it stays quiet at boot." else "You can see the list; switching apps on and off needs root.", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.pkg + it.receiver }) { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(b.pkg, 30.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text(if (b.enabled) "Enabled" else "Disabled", color = if (b.enabled) Look.accent else T.sub, fontSize = 11.sp)
                    }
                    PSwitch(b.enabled, { on -> if (s.root) scope.launch { withContext(Dispatchers.IO) { Startup.set(b, on) }; list = withContext(Dispatchers.IO) { Startup.list(ctx) } } }, enabled = s.root)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- details (flat table) and device

@Composable
fun DetailsPage() {
    var list by remember { mutableStateOf<List<Proc>>(emptyList()) }
    var sort by remember { mutableStateOf("pid") }
    var q by remember { mutableStateOf("") }
    val hs = rememberScrollState()
    LaunchedEffect(Unit) { while (true) { list = withContext(Dispatchers.IO) { Sampler.processes() }; delay(3000) } }
    val f = list.filter { q.isBlank() || it.name.contains(q, true) || it.user.contains(q, true) || it.pid.toString() == q }
    val shown = when (sort) { "cpu" -> f.sortedByDescending { it.cpu }; "mem" -> f.sortedByDescending { it.rssMb }; "name" -> f.sortedBy { it.name.lowercase() }; "user" -> f.sortedBy { it.user }; "vsz" -> f.sortedByDescending { it.vszMb }; else -> f.sortedBy { it.pid } }
    val mono = FontFamily.Monospace
    val cols = listOf(Triple("PID", "pid", 60.dp), Triple("Status", "", 78.dp), Triple("User", "user", 92.dp), Triple("CPU", "cpu", 52.dp), Triple("Memory", "mem", 76.dp), Triple("Virtual", "vsz", 84.dp), Triple("Priority", "", 66.dp), Triple("Parent", "", 66.dp))
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Header("Details", "${list.size} processes · ${list.count { it.state == "R" }} running")
        PField(q, { q = it }, "Filter by name, PID or user", Modifier.fillMaxWidth().padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Name", Modifier.width(150.dp).clickable { sort = "name" }, color = if (sort == "name") Look.accent else T.sub, fontSize = 12.sp)
            Row(Modifier.weight(1f).horizontalScroll(hs)) { cols.forEach { (t, k, w) -> Text(t, Modifier.width(w).clickable { if (k.isNotEmpty()) sort = k }, color = if (sort == k) Look.accent else T.sub, fontSize = 12.sp) } }
        }
        if (list.isEmpty()) Center("The process list needs root")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(shown.take(400), key = { it.pid }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(p.name, Modifier.width(150.dp), color = T.text, fontSize = 12.sp, maxLines = 1, fontFamily = mono)
                    Row(Modifier.weight(1f).horizontalScroll(hs)) {
                        listOf("${p.pid}" to 60.dp, (if (p.state == "R") "Running" else if (p.state == "Z") "Zombie" else if (p.state == "T") "Stopped" else "Sleeping") to 78.dp, p.user to 92.dp, "%.0f".format(p.cpu) to 52.dp,
                            "${p.rssMb} MB" to 76.dp, "${p.vszMb} MB" to 84.dp, "${p.nice}" to 66.dp, "${p.ppid}" to 66.dp).forEach { (t, w) -> Text(t, Modifier.width(w), color = T.sub, fontSize = 12.sp, fontFamily = mono, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
fun DevicePage() {
    val ctx = LocalContext.current
    var secs by remember { mutableStateOf<List<Section>>(emptyList()) }
    LaunchedEffect(Unit) { while (true) { secs = withContext(Dispatchers.IO) { Device.sections(ctx) }; delay(3000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(if (T.material) 12.dp else 10.dp)) {
        Header("Device")
        secs.forEach { sc -> PCard { Text(sc.title, color = Look.accent, fontSize = if (T.material) 16.sp else 15.sp, fontWeight = if (T.material) FontWeight.Medium else FontWeight.SemiBold); InfoRows(sc.rows) } }
        PCard { Text("CPU", color = Look.accent, fontSize = if (T.material) 16.sp else 15.sp, fontWeight = if (T.material) FontWeight.Medium else FontWeight.SemiBold); InfoRows(remember { Extra.cpuRows() }) }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- services

@Composable
fun ServicesPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    var running by remember { mutableStateOf<List<Svc>>(emptyList()) }
    var declared by remember { mutableStateOf<List<Svc>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf(if (s.root) "run" else "all") }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s.root) { while (s.root) { running = withContext(Dispatchers.IO) { Extra.services(ctx) }; delay(6000) } }
    LaunchedEffect(Unit) { declared = withContext(Dispatchers.IO) { Extra.declaredServices(ctx) } }
    val runKeys = remember(running) { running.map { it.pkg + "/" + it.cls }.toSet() }
    val base = if (tab == "run") running else if (tab == "off") declared.filter { !it.enabled } else declared
    val list = base.filter { q.isBlank() || it.label.contains(q, true) || it.cls.contains(q, true) || it.pkg.contains(q, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Header("Services", "${running.size} running · ${declared.size} declared · ${declared.count { !it.enabled }} disabled")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(tab == "run", "Running") { tab = "run" }; PChip(tab == "all", "All declared") { tab = "all" }; PChip(tab == "off", "Disabled") { tab = "off" }
        }
        PField(q, { q = it }, "Search services", Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (tab == "run" && !s.root) Center("Listing running services needs root.\nThe other two tabs work without it.")
        else if (list.isEmpty()) Center("Nothing here yet")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(list.take(400), key = { it.pkg + "/" + it.cls }) { v ->
                val live = (v.pkg + "/" + v.cls) in runKeys || (v.cls.startsWith(".") && (v.pkg + "/" + v.pkg + v.cls) in runKeys) || tab == "run"
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(v.pkg, 28.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text(v.cls.substringAfterLast('.') + if (live) " · running" else if (!v.enabled) " · disabled" else "", color = if (live) Look.accent else T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                    if (tab == "run") PButton("Stop") { scope.launch { withContext(Dispatchers.IO) { Extra.stopService(v) }; running = withContext(Dispatchers.IO) { Extra.services(ctx) } } }
                    else PSwitch(v.enabled, { on -> if (s.root) scope.launch { withContext(Dispatchers.IO) { Extra.setServiceEnabled(v, on) }; declared = withContext(Dispatchers.IO) { Extra.declaredServices(ctx) } } }, enabled = s.root)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings: icon categories

private class Cat(val id: String, val icon: String, val title: String, val sub: String)

@Composable
fun SettingsPage() {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf<String?>(null) }
    BackHandler(cat != null) { cat = null }
    val cats = listOf(
        Cat("look", "palette", "Appearance", "Theme: ${T.name}"),
        Cat("general", "table", "General", "Start page, pages shown, units, text size"),
        Cat("monitor", "performance", "Monitor and alerts", if (Opt.ovOn || Opt.notifOn) "On" else "Floating numbers, notification, temperature / battery / memory alerts"),
        Cat("report", "info", "Report", "Share all device information"),
        Cat("speed", "speed", "Refresh rate", "${Cfg.interval / 1000f} s"),
        Cat("proc", "processes", "Processes", if (Cfg.confirmKill) "Ask before ending" else "End without asking"),
        Cat("root", "cpu", "Permissions", if (Shell.root == true) "Root available" else "No root (limited data)"),
        Cat("about", "info", "About", "Version, privacy, licences"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(if (T.material) 12.dp else 8.dp)) {
        Header(if (cat == null) "Settings" else cats.first { it.id == cat }.title, onBack = if (cat != null) ({ cat = null }) else null)
        when (cat) {
            null -> cats.forEach { c ->
                PCard(Modifier.clickable { cat = c.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon(c.icon, Look.accent, 28.dp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(c.title, color = T.text, fontSize = 16.sp, fontWeight = if (T.material) FontWeight.Medium else null); Text(c.sub, color = T.sub, fontSize = 12.sp) }
                        Text("›", color = T.sub, fontSize = 22.sp)
                    }
                }
            }
            "look" -> {
                if (T.material) MaterialSection(ctx) else {
                Text("Appearance mode", color = T.sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, n) ->
                        PChip(Look.mode == id, n) { Cfg.setMode(ctx, id) }
                    }
                }
                if (T.tabs && T.id != "win10") Text("The 7 / XP / 95 themes stay light, as they were in their day; dark mode works in Windows 11, 10 and Material.", color = T.sub, fontSize = 11.sp)
                if (T.id == "win11") {
                    Text("Window material (Windows 11 only)", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Effects.list.forEach { (id, n) -> PChip(Look.effect == id, n) { Cfg.setEffect(ctx, id) } }
                    }
                    Text("Mica: a quiet backdrop tinted only slightly by the wallpaper. Mica Alt is a touch deeper, Acrylic is more see-through with a bright edge, Glass is light.", color = T.sub, fontSize = 11.sp)
                }
                }
                Text("Theme", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Themes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { th -> Column(Modifier.weight(1f).clickable { Cfg.setTheme(ctx, th.id) }.then(if (th.id == Look.theme.id) Modifier.border(2.dp, Look.accent, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, T.line, RoundedCornerShape(10.dp))).padding(8.dp)) {
                            ThemePreview(th)
                            Text(th.name, color = T.text, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            themeCredit(th.id)?.let { Text(it, color = T.sub, fontSize = 10.sp) }
                            ThemeWallpaperRow(th.id)
                        } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (T.material || !T.tabs || T.id == "win10") {
                    Text("Accent colour", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).background(T.accent, androidx.compose.foundation.shape.CircleShape).then(if (Look.accentIdx == -1) Modifier.border(3.dp, T.text, androidx.compose.foundation.shape.CircleShape) else Modifier).clickable { Cfg.setAccent(ctx, -1) })
                        Accents.forEachIndexed { i, c ->
                            Box(Modifier.size(34.dp).background(c, androidx.compose.foundation.shape.CircleShape).then(if (Look.accentIdx == i) Modifier.border(3.dp, T.text, androidx.compose.foundation.shape.CircleShape) else Modifier).clickable { Cfg.setAccent(ctx, i) })
                        }
                    }
                }
            }
            "general" -> GeneralSettings()
            "monitor" -> MonitorSettings()
            "report" -> ReportSettings()
            "speed" -> PCard {
                Text("How often to read data? Faster = smoother graphs, a little more battery.", color = T.sub, fontSize = 13.sp)
                listOf(500L to "0,5 sn", 1000L to "1 sn", 2000L to "2 sn", 5000L to "5 sn").forEach { (v, n) ->
                    Row(Modifier.fillMaxWidth().clickable { Cfg.setInterval(ctx, v) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(Cfg.interval == v) { Cfg.setInterval(ctx, v) }
                        Text(n, color = T.text, fontSize = 15.sp)
                    }
                }
            }
            "proc" -> PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Ask before ending", color = T.text, fontSize = 15.sp); Text("Prevents ending a process by accident", color = T.sub, fontSize = 12.sp) }
                    PSwitch(Cfg.confirmKill, { Cfg.setConfirm(ctx, it) })
                }
            }
            "root" -> PCard {
                Text(if (Shell.root == true) "Root is available: all processes, kernel data, services and startup apps are unlocked." else "No root: overall memory, network, battery and device info work; process, service and kernel details are limited.", color = T.text, fontSize = 14.sp)
                val scope = rememberCoroutineScope()
                PButton("Try root again") { scope.launch { withContext(Dispatchers.IO) { Shell.detectRoot() } } }
            }
            else -> AboutPage()
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Settings > Appearance for the Material looks: Material You, mode or AMOLED black, accent and corner size. */
@Composable
private fun MaterialSection(ctx: android.content.Context) {
    val canDynamic = android.os.Build.VERSION.SDK_INT >= 31
    val dynamicOn = T.id == "material" && Look.dynamic && canDynamic
    Text("Material", color = Look.accent, fontSize = 15.sp)
    if (T.id == "material") Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Material You", color = T.text, fontSize = 15.sp)
            Text(if (canDynamic) "Colours from your wallpaper" else "Needs Android 12 or newer", color = T.sub, fontSize = 12.sp)
        }
        PSwitch(Look.dynamic && canDynamic, { Cfg.setDynamic(ctx, it) }, enabled = canDynamic)
    }
    Text("Mode", color = T.sub, fontSize = 12.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, n) ->
            PChip(T.id == "material" && Look.mode == id, n) { Cfg.setTheme(ctx, "material"); Cfg.setMode(ctx, id) }
        }
    }
    Text("Accent colour", color = T.sub, fontSize = 12.sp)
    val own = Themes.first { it.id == T.id }.accent
    Row(Modifier.alpha(if (dynamicOn) .38f else 1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (-1 until Accents.size).forEach { i ->
            Box(Modifier.size(34.dp).background(if (i < 0) own else Accents[i], androidx.compose.foundation.shape.CircleShape)
                .then(if (Look.accentIdx == i) Modifier.border(3.dp, T.text, androidx.compose.foundation.shape.CircleShape) else Modifier)
                .clickable(enabled = !dynamicOn) { Cfg.setAccent(ctx, i) })
        }
    }
    Text("Corner size", color = T.sub, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (id, n) -> PChip(Look.corner == id, n) { Cfg.setCorner(ctx, id) } }
    }
}

/** Tiny picture of what a theme looks like: its window, nav, card edge and graph style. */
@Composable
private fun ThemePreview(th: PTheme) {
    val winBg = th.wall ?: androidx.compose.ui.graphics.SolidColor(th.bg)
    val cardShape = RoundedCornerShape(th.radius.coerceAtMost(8.dp))
    Box(Modifier.fillMaxWidth().height(78.dp).background(winBg).border(1.dp, th.line)) {
        Column {
            if (th.title != null) Box(Modifier.fillMaxWidth().height(12.dp).background(th.title)) else if (th.tabs) Box(Modifier.fillMaxWidth().height(10.dp).background(th.bg).border(1.dp, th.line))
            Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.width(12.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { i ->
                        val on = i == 1
                        Box(Modifier.size(if (th.material) 12.dp else 9.dp, 9.dp).background(
                            if (on) th.accent.copy(alpha = if (th.material) .3f else 1f) else th.sub.copy(alpha = .4f),
                            if (th.material) RoundedCornerShape(5.dp) else RoundedCornerShape(th.radius.coerceAtMost(2.dp))))
                    }
                }
                Box(Modifier.fillMaxSize()
                    .background(if (th.glassA > 0f) th.panel.copy(alpha = th.glassA) else if (th.classicGraph) Color.Black else th.panel, cardShape)
                    .then(if (th.bevel) Modifier.bevel(false) else if (th.classicGraph || th.tabs) Modifier.border(1.dp, th.line, cardShape) else if (!th.material) Modifier.border(1.dp, th.line, cardShape) else Modifier)) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                        val p = androidx.compose.ui.graphics.Path()
                        val ys = listOf(.7f, .5f, .6f, .3f, .45f, .2f, .35f)
                        ys.forEachIndexed { i, y -> val x = size.width * i / (ys.size - 1); if (i == 0) p.moveTo(x, size.height * y) else p.lineTo(x, size.height * y) }
                        val col = if (th.classicGraph) Color(0xFF00FF00) else th.accent
                        if (!th.classicGraph) {
                            val fill = androidx.compose.ui.graphics.Path().apply { addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                            drawPath(fill, col.copy(alpha = .22f))
                        }
                        drawPath(p, col, style = androidx.compose.ui.graphics.drawscope.Stroke(if (th.classicGraph) 2f else 3f))
                    }
                }
            }
        }
    }
}
