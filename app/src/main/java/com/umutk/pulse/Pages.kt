package com.umutk.pulse

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

private val iconCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

@Composable
fun AppIcon(name: String, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val ctx = LocalContext.current
    val pkg = name.substringBefore(':')
    val bmp = remember(pkg) {
        iconCache.getOrPut(pkg) { try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap() } catch (e: Exception) { null } }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size)) else PIcon("processes", T.sub, size * 0.8f, Modifier.padding(2.dp))
}

fun labelOf(ctx: android.content.Context, name: String): String {
    val pkg = name.substringBefore(':')
    return try { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager).toString() + name.substringAfter(':', "").let { if (it.isEmpty()) "" else " ($it)" } } catch (e: Exception) { name }
}

@Composable
fun PButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val t = T
    if (t.tabs) {
        Box(modifier.background(t.bg).then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(t.radius))).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (danger) Color(0xFFB00020) else t.text, fontSize = 13.sp)
        }
    } else {
        Button(onClick, modifier, shape = RoundedCornerShape(t.radius.coerceAtMost(12.dp)), colors = ButtonDefaults.buttonColors(containerColor = if (danger) Color(0xFFC42B1C) else Look.accent, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Header(text: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = T.sub, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- app history

@Composable
fun HistoryPage() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Extra.usageGranted(ctx)) }
    var rows by remember { mutableStateOf<List<UsageRow>>(emptyList()) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = Extra.usageGranted(ctx) }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(granted) { while (granted) { rows = withContext(Dispatchers.IO) { Extra.usageToday(ctx) }; delay(10000) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Header("App history", "today")
        if (!granted) {
            PCard {
                Text("Showing how long each app was open needs the \"usage access\" permission.", color = T.text, fontSize = 14.sp)
                PButton("Allow") { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } else if (rows.isEmpty()) Center("Nothing recorded today")
        else {
            val top = rows.firstOrNull()?.ms?.coerceAtLeast(1) ?: 1
            LazyColumn(Modifier.fillMaxSize()) {
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

// ---------------------------------------------------------------- startup apps

@Composable
fun StartupPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    var list by remember { mutableStateOf<List<Boot>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { list = withContext(Dispatchers.IO) { Startup.list(ctx) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
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
                    Switch(b.enabled, { on -> if (s.root) scope.launch { withContext(Dispatchers.IO) { Startup.set(b, on) }; list = withContext(Dispatchers.IO) { Startup.list(ctx) } } }, enabled = s.root,
                        colors = SwitchDefaults.colors(checkedTrackColor = Look.accent))
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
    LaunchedEffect(Unit) { while (true) { list = withContext(Dispatchers.IO) { Sampler.processes() }; delay(3000) } }
    val shown = when (sort) { "cpu" -> list.sortedByDescending { it.cpu }; "mem" -> list.sortedByDescending { it.rssMb }; "name" -> list.sortedBy { it.name.lowercase() }; else -> list.sortedBy { it.pid } }
    val mono = FontFamily.Monospace
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Header("Details", "${list.size} processes")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            @Composable fun head(t: String, k: String, m: Modifier) = Text(t, m.clickable { sort = k }, color = if (sort == k) Look.accent else T.sub, fontSize = 12.sp)
            head("Name", "name", Modifier.weight(1f)); head("PID", "pid", Modifier.width(58.dp)); head("CPU", "cpu", Modifier.width(48.dp)); head("Memory", "mem", Modifier.width(66.dp))
        }
        if (list.isEmpty()) Center("The process list needs root")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(shown.take(300), key = { it.pid }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(p.name, Modifier.weight(1f), color = T.text, fontSize = 12.sp, maxLines = 1, fontFamily = mono)
                    Text("${p.pid}", Modifier.width(58.dp), color = T.sub, fontSize = 12.sp, fontFamily = mono)
                    Text("%.0f".format(p.cpu), Modifier.width(48.dp), color = T.sub, fontSize = 12.sp, fontFamily = mono)
                    Text("${p.rssMb} MB", Modifier.width(66.dp), color = T.sub, fontSize = 12.sp, fontFamily = mono)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Device")
        secs.forEach { sc -> PCard { Text(sc.title, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); InfoRows(sc.rows) } }
        PCard { Text("CPU", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); InfoRows(remember { Extra.cpuRows() }) }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- services

@Composable
fun ServicesPage() {
    val ctx = LocalContext.current
    val s by Sampler.snap.collectAsState()
    var list by remember { mutableStateOf<List<Svc>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s.root) { while (s.root) { list = withContext(Dispatchers.IO) { Extra.services(ctx) }; delay(6000) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Header("Services", if (list.isNotEmpty()) "${list.size} running" else "")
        if (!s.root) Center("Listing running services needs root.")
        else LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.pkg + it.cls }) { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(v.pkg, 28.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text(v.cls.substringAfterLast('.'), color = T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                    TextButton({ scope.launch { withContext(Dispatchers.IO) { Extra.stopService(v) }; list = withContext(Dispatchers.IO) { Extra.services(ctx) } } }) { Text("Stop", color = Look.accent, fontSize = 13.sp) }
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
        Cat("about", "info", "About", "Pulse 0.6"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Header(if (cat == null) "Settings" else cats.first { it.id == cat }.title)
        when (cat) {
            null -> cats.forEach { c ->
                PCard(Modifier.clickable { cat = c.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon(c.icon, Look.accent, 28.dp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(c.title, color = T.text, fontSize = 16.sp); Text(c.sub, color = T.sub, fontSize = 12.sp) }
                        Text("›", color = T.sub, fontSize = 22.sp)
                    }
                }
            }
            "look" -> {
                Text("Appearance mode", color = T.sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, n) ->
                        FilterChip(Look.mode == id, { Cfg.setMode(ctx, id) }, { Text(n) })
                    }
                }
                if (T.tabs && T.id != "win10") Text("The 7 / XP / 95 themes stay light, as they were in their day; dark mode works in Windows 11, 10 and Material.", color = T.sub, fontSize = 11.sp)
                if (T.id == "win11") {
                    Text("Window material (Windows 11 only)", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Effects.list.forEach { (id, n) -> FilterChip(Look.effect == id, { Cfg.setEffect(ctx, id) }, { Text(n) }) }
                    }
                    Text("Mica: a quiet backdrop tinted only slightly by the wallpaper. Mica Alt is a touch deeper, Acrylic is more see-through with a bright edge, Glass is light.", color = T.sub, fontSize = 11.sp)
                }
                Text("Theme", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Themes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { th -> Column(Modifier.weight(1f).clickable { Cfg.setTheme(ctx, th.id) }.then(if (th.id == Look.theme.id) Modifier.border(2.dp, Look.accent, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, T.line, RoundedCornerShape(10.dp))).padding(8.dp)) {
                            ThemePreview(th)
                            Text(th.name, color = T.text, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if ((!T.tabs || T.id == "win10") && T.id != "material") {
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
                        RadioButton(Cfg.interval == v, { Cfg.setInterval(ctx, v) }, colors = RadioButtonDefaults.colors(selectedColor = Look.accent))
                        Text(n, color = T.text, fontSize = 15.sp)
                    }
                }
            }
            "proc" -> PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Ask before ending", color = T.text, fontSize = 15.sp); Text("Prevents ending a process by accident", color = T.sub, fontSize = 12.sp) }
                    Switch(Cfg.confirmKill, { Cfg.setConfirm(ctx, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Look.accent))
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

/** Tiny picture of what a theme looks like. */
@Composable
private fun ThemePreview(th: PTheme) {
    Box(Modifier.fillMaxWidth().height(70.dp).background(th.bg).border(1.dp, th.line)) {
        Column {
            if (th.title != null) Box(Modifier.fillMaxWidth().height(12.dp).background(th.title)) else Box(Modifier.fillMaxWidth().height(4.dp).background(th.accent))
            Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.width(10.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { Box(Modifier.size(8.dp).background(if (it == 1) th.accent else th.sub.copy(alpha = .5f), RoundedCornerShape(2.dp))) }
                }
                Box(Modifier.fillMaxSize().background(if (th.classicGraph) Color.Black else th.panel, RoundedCornerShape(th.radius.coerceAtMost(6.dp)))) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val p = androidx.compose.ui.graphics.Path()
                        val ys = listOf(.7f, .5f, .6f, .3f, .45f, .2f, .35f)
                        ys.forEachIndexed { i, y -> val x = size.width * i / (ys.size - 1); if (i == 0) p.moveTo(x, size.height * y) else p.lineTo(x, size.height * y) }
                        drawPath(p, if (th.classicGraph) Color(0xFF00FF00) else th.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                    }
                }
            }
        }
    }
}
