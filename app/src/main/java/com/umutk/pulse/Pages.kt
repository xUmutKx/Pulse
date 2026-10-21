package com.umutk.pulse

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Crop
import com.umutk.pulse.features.themeWallpaper
import com.umutk.pulse.features.builtinWall
import android.content.Intent
import com.umutk.pulse.features.ThemeWallPicture
import com.umutk.pulse.features.setThemeWallPos
import com.umutk.pulse.features.themeWallPos
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.provider.Settings
import androidx.activity.compose.BackHandler
import com.umutk.pulse.features.DonutChart
import com.umutk.pulse.features.AccessNeededCard
import com.umutk.pulse.features.accessMissing
import com.umutk.pulse.features.ownProcesses
import com.umutk.pulse.features.NoteCount
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.umutk.pulse.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import rikka.shizuku.Shizuku
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun PButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, compact: Boolean = false, onClick: () -> Unit) {
    val t = T
    if (oem) {
        val inDlg = InOemDialog.current
        val acc = Look.accent.copy(alpha = 1f)
        val fg = if (inDlg) LocalContentColor.current else if (danger) Color(0xFFC42B1C) else if (oneUi) t.text else onAccent(acc)
        val bg = if (inDlg) Color.Transparent else if (oneUi) t.text.copy(alpha = .08f) else if (danger) Color(0xFFC42B1C).copy(alpha = .12f) else acc
        Row(modifier.heightIn(min = 40.dp).background(bg, CircleShape).clickable(onClick = onClick).padding(horizontal = if (compact) 10.dp else 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (!inDlg && colorOs && danger) Color(0xFFC42B1C) else fg, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    } else if (win95Authentic) {
        // 95 push button: 75x23 grey bevel that sinks while held; the outer padding keeps a ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 9.dp), propagateMinConstraints = true) {
            Box(Modifier.defaultMinSize(75.dp, 23.dp).background(Color(0xFFC0C0C0)).bevel(!down).padding(horizontal = 8.dp, vertical = 3.dp).offset(if (down) 1.dp else 0.dp, if (down) 1.dp else 0.dp), contentAlignment = Alignment.Center) {
                Text(text, color = Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (win7Authentic) {
        // Aero push button: grey gloss, blue glow while held; the outer padding keeps a ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 7.dp), propagateMinConstraints = true) {
            Row(Modifier.defaultMinSize(75.dp, 26.dp).aero7(down).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else Color.Black); Spacer(Modifier.width(6.dp)) }
                Text(text, color = if (danger) Color(0xFFB00020) else Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (xpAuthentic) {
        // Luna push button: white to beige, navy edge, orange rim while held; ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 7.dp), propagateMinConstraints = true) {
            Row(Modifier.defaultMinSize(75.dp, 26.dp).luna(down).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else Color.Black); Spacer(Modifier.width(6.dp)) }
                Text(text, color = if (danger) Color(0xFFB00020) else Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (t.tabs) {
        // Windows 10 gets a hint of rounding, far less than Material
        val r = if (t.id == "win10") 4.dp else t.radius
        Box(modifier.clip(RoundedCornerShape(r)).background(t.bg).then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(r))).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else t.text); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (danger) Color(0xFFB00020) else t.text, fontSize = 13.sp)
        }
    } else {
        // single outline, no accent fill: the same for every look
        val shape = if (t.material) CircleShape else RoundedCornerShape(if (t.id == "win11") 6.dp else t.radius.coerceAtMost(12.dp))
        val fg = if (danger) Color(0xFFC42B1C) else t.text
        Box(modifier.border(1.dp, if (danger) fg else if (t.id == "amoled") Color.White else t.line, shape).clickable(onClick = onClick).padding(horizontal = if (compact) 8.dp else if (t.material) 24.dp else 14.dp, vertical = if (t.material) 10.dp else 6.dp), contentAlignment = Alignment.Center) {
            Text(text, color = fg, fontSize = if (compact) 12.sp else if (t.material) 14.sp else 13.sp, fontWeight = if (t.material) FontWeight.Medium else null, maxLines = 1)
        }
    }
}

@Composable
private fun Header(text: String, trailing: String = "", onBack: (() -> Unit)? = null) {
    if (colorOs) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Box(Modifier.weight(1f)) { OemTitle(text) }
            if (trailing.isNotEmpty()) Text(trailing, color = T.sub, fontSize = 12.sp)
        }
        return
    }
    if (oneUi) { OemTitle(text) { if (trailing.isNotEmpty()) Text(trailing, color = T.sub, fontSize = 12.sp) }; return }
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        if (onBack != null) Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
        // 95 had no big page titles: plain small system text
        if (win95Authentic) Text(text, color = Color.Black, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
        // Win7: the Aero "main instruction" heading, dark blue and light
        else if (win7Authentic) Text(text, color = Color(0xFF003399), fontSize = 17.sp, modifier = Modifier.weight(1f))
        else Text(text, color = T.text, fontSize = 22.sp, fontWeight = if (T.material) FontWeight.Normal else FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
    var screenOnly by remember { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = Extra.usageGranted(ctx); notesOn = NoteCount.enabled(ctx) }
        }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(granted, screenOnly) {
        while (granted) {
            rows = withContext(Dispatchers.IO) { if (screenOnly) Extra.usageScreenOn(ctx) else Extra.usageToday(ctx) }
            unlocks = withContext(Dispatchers.IO) { Extra.unlocksToday(ctx) }
            delay(10000)
        }
    }
    LaunchedEffect(Unit) { while (true) { notes = NoteCount.today(ctx); delay(5000) } }
    val total = rows.sumOf { it.ms }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Header("App history", "today") }
            PChip(!screenOnly, tr("Total", "Toplam")) { screenOnly = false }
            Spacer(Modifier.width(6.dp))
            PChip(screenOnly, tr("Screen on", "Ekran açık")) { screenOnly = true }
        }
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
                val slices = rows.take(15).map { it.label to it.ms } +
                    (rows.drop(15).sumOf { it.ms }.takeIf { it > 0 }?.let { listOf("Others" to it) }.orEmpty())
                // the legend under the ring lists every slice, so the whole block scrolls instead of being cut at the ring's height
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    DonutChart(slices, Modifier.fillMaxWidth(), centerText = hms(total))
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
        if (accessMissing()) AccessNeededCard(tr("Startup apps", "Başlangıç uygulamaları"), tr("The list is complete. Switching apps on and off needs root or Shizuku.", "Liste eksiksiz. Uygulamaları açıp kapatmak için root veya Shizuku gerekir."), Modifier.padding(bottom = 6.dp))
        else Text(tr("Apps that start themselves when the phone boots. Switch one off and it stays quiet at boot.", "Telefon açılırken kendiliğinden başlayan uygulamalar. Birini kapatırsan açılışta sessiz kalır."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.pkg + it.receiver }) { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(b.pkg, 30.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                        Text(if (b.enabled) "Enabled" else "Disabled", color = if (b.enabled) Look.accent else T.sub, fontSize = 11.sp)
                    }
                    PSwitch(b.enabled, { on -> if (!accessMissing()) scope.launch { withContext(Dispatchers.IO) { Startup.set(b, on) }; list = withContext(Dispatchers.IO) { Startup.list(ctx) } } }, enabled = !accessMissing())
                }
            }
        }
    }
}

// ---------------------------------------------------------------- details (flat table) and device

@Composable
fun DetailsPage() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Proc>>(emptyList()) }
    var sort by remember { mutableStateOf("pid") }
    var q by remember { mutableStateOf("") }
    val hs = rememberScrollState()
    LaunchedEffect(Unit) { while (true) { list = withContext(Dispatchers.IO) { Sampler.processes().ifEmpty { ownProcesses(ctx) } }; delay(3000) } }
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
        AccessNeededCard(tr("Details", "Ayrıntılar"), tr("Showing this app's own processes only.", "Yalnızca bu uygulamanın kendi işlemleri gösteriliyor."), Modifier.padding(bottom = 6.dp))
        if (list.isEmpty()) Center(tr("No processes could be read", "İşlemler okunamadı"))
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
    val can = !accessMissing()
    // All declared opens first; Running and Disabled are one tap away.
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = picked ?: "all"
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(can) { while (true) { running = withContext(Dispatchers.IO) { Extra.services(ctx) }; delay(6000) } }
    LaunchedEffect(Unit) { declared = withContext(Dispatchers.IO) { Extra.declaredServices(ctx) } }
    val runKeys = remember(running) { running.map { it.pkg + "/" + it.cls }.toSet() }
    val base = if (tab == "run") running else if (tab == "off") declared.filter { !it.enabled } else declared
    val list = base.filter { q.isBlank() || it.label.contains(q, true) || it.cls.contains(q, true) || it.pkg.contains(q, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Header("Services")
        // the three counts share one line; the type shrinks until they fit the screen
        var countSize by remember { mutableStateOf(12f) }
        Text("${running.size} running  ·  ${declared.size} declared  ·  ${declared.count { !it.enabled }} disabled", color = T.sub, fontSize = countSize.sp, maxLines = 1, softWrap = false,
            onTextLayout = { if (it.didOverflowWidth && countSize > 8f) countSize -= .5f }, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(tab == "run", "Running") { picked = "run" }; PChip(tab == "all", "All declared") { picked = "all" }; PChip(tab == "off", "Disabled") { picked = "off" }
        }
        PField(q, { q = it }, "Search services", Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (tab == "run" && !can) AccessNeededCard(tr("Running services", "Çalışan hizmetler"), tr("Showing this app's own running services only. Other apps' services need root or Shizuku. The other two tabs show everything.", "Yalnızca bu uygulamanın çalışan hizmetleri gösteriliyor. Diğer uygulamaların hizmetleri için root veya Shizuku gerekir. Diğer iki sekme her şeyi gösterir."), Modifier.padding(bottom = 6.dp))
        if (list.isEmpty()) Center(tr("Nothing here yet", "Burada henüz bir şey yok"))
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
                    else PSwitch(v.enabled, { on -> if (can) scope.launch { withContext(Dispatchers.IO) { Extra.setServiceEnabled(v, on) }; declared = withContext(Dispatchers.IO) { Extra.declaredServices(ctx) } } }, enabled = can)
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
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    InnerBackStep(cat != null) { cat = null }
    val cats = listOf(
        Cat("look", "palette", "Appearance", "Theme: ${T.name}"),
        Cat("general", "table", "General", "Start page, pages shown, units, text size"),
        Cat("functions", "settings", tr("Functions", "İşlevler"), tr("Notification history, refresh rate, report, permissions", "Bildirim geçmişi, yenileme hızı, rapor, izinler")),
        Cat("monitor", "performance", "Monitor and alerts", if (Opt.ovOn || Opt.notifOn) "On" else "Floating numbers, notification, temperature / battery / memory alerts"),
        Cat("about", "info", "About", "Version, privacy, licences"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(if (T.material) 12.dp else 8.dp)) {
        Header(if (cat == null) "Settings" else cats.first { it.id == cat }.title)
        when (cat) {
            null -> {
                // search over the settings categories
                var q by remember { mutableStateOf("") }
                PField(q, { q = it }, tr("Search settings", "Ayarlarda ara"), Modifier.fillMaxWidth().padding(vertical = 6.dp))
                cats.filter { q.isBlank() || it.title.contains(q, true) || it.sub.contains(q, true) }.forEach { c ->
                PCard(Modifier.clickable { cat = c.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon(c.icon, Look.accent, 28.dp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(c.title, color = T.text, fontSize = 16.sp, fontWeight = if (T.material) FontWeight.Medium else null); Text(c.sub, color = T.sub, fontSize = 12.sp) }
                        Text("›", color = T.sub, fontSize = 22.sp)
                    }
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
                    Text(tr("Background", "Arka plan"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(Opt.amoledBg, tr("Black (AMOLED)", "Siyah (AMOLED)")) { Opt.amoledBg = true; Opt.save(ctx) }
                        PChip(!Opt.amoledBg, tr("Dark grey", "Koyu gri")) { Opt.amoledBg = false; Opt.save(ctx) }
                    }
                    Text("Window material (Windows 11 only)", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Effects.list.forEach { (id, n) -> PChip(Look.effect == id, n) { Cfg.setEffect(ctx, id) } }
                    }
                    Text(tr("Icons (Windows 11)", "İkonlar (Windows 11)"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PChip(!Look.winIconLine, "Fluent") { Cfg.setWinIconLine(ctx, false) }
                        PChip(Look.winIconLine, tr("Line", "Çizgi")) { Cfg.setWinIconLine(ctx, true) }
                    }
                    if (Look.effect == "blur") {
                        Text(tr("Blur amount", "Bulanıklık"), color = T.sub, fontSize = 12.sp)
                        PSlider(Look.blurDp / 30f, { Look.blurDp = it * 30f }, 0f..1f) { Cfg.setBlur(ctx) }
                        Text(tr("Tint (darkness)", "Koyuluk"), color = T.sub, fontSize = 12.sp)
                        PSlider(Look.blurTint, { Look.blurTint = it }, 0f..1f) { Cfg.setBlur(ctx) }
                    }
                    Text("Mica: a quiet backdrop tinted only slightly by the wallpaper. Mica Alt is a touch deeper, Acrylic is more see-through with a bright edge, Glass is the most see-through.", color = T.sub, fontSize = 11.sp)
                }
                }
                Text("Theme", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                @Composable fun ThemeRow(pair: List<PTheme>, center: Boolean = false) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start)) {
                        pair.forEach { th -> Column((if (center) Modifier.fillMaxWidth(.5f) else Modifier.weight(1f)).semantics { contentDescription = th.name }.clickable { Cfg.setTheme(ctx, th.id) }.then(if (th.id == Look.theme.id) Modifier.border(2.dp, Look.accent, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, T.line, RoundedCornerShape(10.dp))).padding(8.dp)) {
                            ThemePreview(th)
                            Text(th.name, color = T.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        } }
                        if (pair.size == 1 && !center) Spacer(Modifier.weight(1f))
                    }
                }
                fun byId(vararg ids: String) = ids.map { id -> Themes.first { it.id == id } }
                val folded = listOf("aurora", "sakura", "ocean", "ember", "frost")
                // Material You and AMOLED are plain appearance choices; the Windows looks have their own group, 95 centred on top
                ThemeRow(byId("material", "amoled"))
                Text("Windows", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                ThemeRow(byId("win11", "win10")); ThemeRow(byId("win7", "xp"))
                ThemeRow(byId("win95"), center = true)
                Row(Modifier.fillMaxWidth().clickable { ThemeFold.open = !ThemeFold.open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Other colors", "Diğer renkler"), color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (ThemeFold.open) 180f else 0f })
                }
                if (ThemeFold.open) Themes.filter { it.id in folded + listOf("oneui", "coloros") }.chunked(2).forEach { ThemeRow(it) }
                // the picture's position, only where the look really draws a picture: Windows 11 (the app background) and authentic Windows 7 / XP
                val bd = com.umutk.pulse.features.Backdrop
                var editWall by remember { mutableStateOf(false) }
                val wallLook = (T.id == "win7" || T.id == "xp") && Opt.authentic
                if ((T.id == "win11" && bd.image != null) || wallLook) PButton(tr("Edit picture", "Resmi düzenle"), icon = androidx.compose.material.icons.Icons.Outlined.Crop) { editWall = true }
                if (editWall) { if (wallLook) com.umutk.pulse.features.ThemeWallEditor(T.id) { editWall = false } else com.umutk.pulse.features.BackdropEditor { editWall = false } }
                // one picker for the selected look only, not under every card
                ThemeWallpaperRow(T.id)
                if (T.material || !T.tabs || T.id == "win10" || T.id == "win11") {
                    Text("Accent colour", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    // one accent row for every look; with Material You on, the wallpaper picks the colour and the row is greyed out
                    val lockedByYou = T.material && Look.dynamic && android.os.Build.VERSION.SDK_INT >= 31
                    Row(Modifier.alpha(if (lockedByYou) .38f else 1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).background(T.accent, androidx.compose.foundation.shape.CircleShape).then(if (Look.accentIdx == -1) Modifier.border(3.dp, T.text, androidx.compose.foundation.shape.CircleShape) else Modifier).clickable(enabled = !lockedByYou) { Cfg.setAccent(ctx, -1) })
                        accentsFor(T.material).forEachIndexed { i, c ->
                            Box(Modifier.size(34.dp).background(c, androidx.compose.foundation.shape.CircleShape).then(if (Look.accentIdx == i) Modifier.border(3.dp, T.text, androidx.compose.foundation.shape.CircleShape) else Modifier).clickable(enabled = !lockedByYou) { Cfg.setAccent(ctx, i) })
                        }
                    }
                }
                // the picture behind the Windows 11 windows: phone wallpaper, the bundled A-D packs, or your own
                if (T.id == "win11") BackdropSettings()
            }
            "general" -> GeneralSettings()
            "functions" -> FunctionsSettings()
            "monitor" -> MonitorSettings()
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
            val mid = if (T.id == "oneui" || T.id == "coloros") T.id else "material"
            PChip(T.id == mid && Look.mode == id, n) { Cfg.setTheme(ctx, mid); Cfg.setMode(ctx, id) }
        }
    }
    Text("Corner size", color = T.sub, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (id, n) -> PChip(Look.corner == id, n) { Cfg.setCorner(ctx, id) } }
    }
}

/** Tiny picture of what a theme looks like: its window, nav, card edge and graph style. */
@Composable
fun ThemePreview(thRaw: PTheme) {
    // the Material looks are shown with the colours they really get: the wallpaper's Material You scheme, the chosen accent and corners
    val eff = if (thRaw.material) effective(thRaw) else thRaw
    // preview only: AMOLED is all white on black; dark Material gets a visible purple-navy tone instead of near black
    val th = if (thRaw.id == "amoled") amoledWindow()
        else if (thRaw.id == "material" && eff.dark) PTheme("material", eff.name, true, androidx.compose.ui.graphics.Color(0xFF2A2640), androidx.compose.ui.graphics.Color(0xFF3B3658), eff.text, eff.sub, eff.accent, androidx.compose.ui.graphics.Color(0xFF4F4A70), eff.radius, material = true, glassA = eff.glassA)
        else eff
    if (th.id == "coloros") { ColorOsPreview(th); return }
    val winBg = th.wall ?: androidx.compose.ui.graphics.SolidColor(th.bg)
    val cardShape = RoundedCornerShape(th.radius.coerceAtMost(8.dp))
    if (th.id == "oneui") { Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(winBg)) { OneUiPreview(th) }; return }
    Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(winBg).border(1.dp, th.line)) {
        // the look's own wallpaper behind the little window, so the choice is seen before picking anything
        ThemeWallPicture(thRaw.id, Modifier.fillMaxSize())
        Column {
            if (th.id == "oneui") Spacer(Modifier.height(14.dp))
            if (th.title != null) Box(Modifier.fillMaxWidth().height(12.dp).background(th.title)) else if (th.tabs) Box(Modifier.fillMaxWidth().height(10.dp).background(th.bg).border(1.dp, th.line))
            Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.width(12.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) { i ->
                        val on = i == 1
                        Box(Modifier.size(if (th.material) 12.dp else 9.dp, 9.dp).background(
                            if (on) th.accent.copy(alpha = if (th.material) .3f else 1f) else th.sub.copy(alpha = .4f),
                            if (th.id == "coloros") androidx.compose.foundation.shape.CircleShape else if (th.material) RoundedCornerShape(5.dp) else RoundedCornerShape(th.radius.coerceAtMost(2.dp))))
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
                        // Material preview: the same smooth M3 line as the real graphs
                        if (th.material) { m3Line(ys.mapIndexed { i, y -> androidx.compose.ui.geometry.Offset(size.width * i / (ys.size - 1), size.height * y) }, col, size.height); return@Canvas }
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

/** Refresh rate: how often the numbers are read; used in About now. Folded away until opened. */
@Composable
fun RefreshSettings() {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    fun label(v: Long) = when (v) { 500L -> tr("0.5 s", "0,5 sn"); 1000L -> tr("1 s", "1 sn"); 2000L -> tr("2 s", "2 sn"); else -> tr("5 s", "5 sn") }
    PCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(label(Cfg.interval), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
        }
        if (open) {
            Text(tr("How often to read data? Faster = smoother graphs, a little more battery.", "Veriler ne sıklıkla okunsun? Daha hızlı = daha akıcı grafik, biraz daha fazla pil."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            listOf(500L, 1000L, 2000L, 5000L).forEach { v ->
                Row(Modifier.fillMaxWidth().clickable { Cfg.setInterval(ctx, v) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PRadio(Cfg.interval == v) { Cfg.setInterval(ctx, v) }
                    Text(label(v), color = T.text, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Permissions: root status and a retry; used in About now. */
@Composable
fun RootSettings() {
    val ctx = LocalContext.current
    PCard {
        Text(if (Shell.root == true) "Root is available: all processes, kernel data, services and startup apps are unlocked." else "No root: overall memory, network, battery and device info work; process, service and kernel details are limited.", color = T.text, fontSize = 14.sp)
        val scope = rememberCoroutineScope()
        PButton("Try root again") { scope.launch { withContext(Dispatchers.IO) { Shell.detectRoot() } } }
        Shell.shizuku
        val installed = Shell.shizukuInstalled(ctx)
        val sv = runCatching { if (Shizuku.pingBinder()) "v${Shizuku.getVersion()}" else "-" }.getOrDefault("-")
        Text("Shizuku: installed=$installed, binder=${Shell.shizuku > 0}, permission=${Shell.shizuku == 2}, version=$sv\n" + (if (Shell.shizukuErr.isNotBlank()) "error: ${Shell.shizukuErr}\n" else "") + "su: " + Shell.rootLog.ifBlank { "not checked yet" }, color = T.sub, fontSize = 11.sp)
        if (Shell.shizukuMsg.isNotBlank()) Text(Shell.shizukuMsg, color = T.sub, fontSize = 11.sp)
        PButton("Ask Shizuku") { Shell.askShizuku(ctx) }
    }
}
