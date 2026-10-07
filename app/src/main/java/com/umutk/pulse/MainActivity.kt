package com.umutk.pulse

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Saved choices. */
object Cfg {
    var interval by mutableLongStateOf(1000L)
    var confirmKill by mutableStateOf(true)

    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = sp(c)
        Look.theme = Themes.firstOrNull { it.id == p.getString("theme", "win11") } ?: Themes[0]
        Look.accentIdx = p.getInt("accent2", -1)
        Look.mode = p.getString("mode", "system") ?: "system"
        Look.effect = p.getString("effect", "mica") ?: "mica"
        interval = p.getLong("interval", 1000L); Sampler.interval = interval
        confirmKill = p.getBoolean("confirm", true)
    }
    fun setTheme(c: Context, id: String) { Look.theme = Themes.first { it.id == id }; sp(c).edit().putString("theme", id).apply() }
    fun setMode(c: Context, v: String) { Look.mode = v; sp(c).edit().putString("mode", v).apply() }
    fun setEffect(c: Context, v: String) { Look.effect = v; sp(c).edit().putString("effect", v).apply() }
    fun setAccent(c: Context, i: Int) { Look.accentIdx = i; sp(c).edit().putInt("accent2", i).apply() }
    fun setInterval(c: Context, v: Long) { interval = v; Sampler.interval = v; sp(c).edit().putLong("interval", v).apply() }
    fun setConfirm(c: Context, v: Boolean) { confirmKill = v; sp(c).edit().putBoolean("confirm", v).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
    }
}

/** The Material themes follow the system dark mode and, on Android 12+, the wallpaper colours. */
@Composable
private fun effective(t: PTheme): PTheme {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (Look.mode) { "dark" -> true; "light" -> false; else -> sysDark }
    if (t.id != "material") return if (dark) darkTwin(t) ?: t else t
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cs = if (android.os.Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else (if (dark) darkColorScheme() else lightColorScheme())
    return PTheme(t.id, t.name, dark, if (dark) Color(0xFF050505) else cs.background, if (dark) Color(0xFF111111) else cs.surfaceContainer, cs.onBackground, cs.onSurfaceVariant, cs.primary, cs.outlineVariant, t.radius, material = true)
}

class Pg(val id: String, val label: String, val icon: String)

val Pages = listOf(
    Pg("processes", "Processes", "processes"),
    Pg("performance", "Performance", "performance"),
    Pg("history", "App history", "history"),
    Pg("startup", "Startup apps", "startup"),
    Pg("details", "Details", "details"),
    Pg("services", "Services", "services"),
    Pg("apps", "Apps", "users"),
    Pg("device", "Device", "phone"),
)
private val visible get() = Pages.filter { it.id !in Opt.hidden }
private val SettingsPg = Pg("settings", "Settings", "settings")

@Composable
private fun Root() {
    val base = Look.theme
    val th = effective(base)
    // the rest of the UI reads Look.theme through T, so the resolved Material look is swapped in while composing
    val cs = if (th.dark) darkColorScheme(primary = Look.accent, background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    else lightColorScheme(primary = Look.accent, background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect { WindowCompat.getInsetsController((view.context as android.app.Activity).window, view).apply { isAppearanceLightStatusBars = !th.dark; isAppearanceLightNavigationBars = !th.dark } }
    Look.resolved = if (th !== base) th else null
    SideEffect { val w = (view.context as android.app.Activity).window; if (Opt.keepOn) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * Opt.fontScale)) {
    MaterialTheme(colorScheme = cs) {
        Surface(Modifier.fillMaxSize(), color = th.bg) { key(th.dark) { App() } }
    }
    }
}

@Composable
private fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var page by rememberSaveable { mutableStateOf(if (Opt.startPage in Pages.map { it.id }) Opt.startPage else "processes") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Shell.detectRoot()
            while (true) { Sampler.sample(ctx); delay(Sampler.interval) }
        }
    }
    val body: @Composable () -> Unit = {
        when (page) {
            "processes" -> ProcessesPage(); "performance" -> PerformancePage(); "history" -> HistoryPage(); "startup" -> StartupPage()
            "details" -> DetailsPage(); "services" -> ServicesPage(); "apps" -> AppsPage(); "device" -> DeviceModule(); else -> SettingsPage()
        }
    }
    if (T.id == "win10") Win10Shell(page, { page = it }, body) else if (T.tabs) ClassicShell(page, { page = it }, body) else ModernShell(page, { page = it }, body)
}

// ---------------------------------------------------------------- Windows 11 / 10, Material, AMOLED: a sidebar on the left

@Composable
private fun ModernShell(page: String, go: (String) -> Unit, body: @Composable () -> Unit) {
    val t = T
    var wide by rememberSaveable(t.id) { mutableStateOf(true) }
    val s by Sampler.snap.collectAsState()
    val fx = if (t.id == "win11") Look.effect else "none"
    Box(Modifier.fillMaxSize().backdrop(fx).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxSize()) {
        Spacer(Modifier.width(52.dp))
        val corner = if (t.material) 24.dp else if (t.radius > 0.dp) 8.dp else 0.dp
        Column(Modifier.weight(1f).fillMaxHeight().layer(fx, RoundedCornerShape(topStart = corner, bottomStart = corner)).then(if (!t.material && t.radius == 0.dp) Modifier.border(1.dp, t.line) else Modifier).padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (s.root) Text("root", color = Look.accent, fontSize = 12.sp)
            }
            Box(Modifier.weight(1f)) { body() }
        }
        }
        // the menu: labelled by default like the real Task Manager, drawn over the content so the page keeps its width; the hamburger folds it to icons
        Column(Modifier.width(if (wide) 210.dp else 52.dp).fillMaxHeight().then(if (wide) Modifier.background(t.bg.copy(alpha = .97f)).border(1.dp, t.line.copy(alpha = .5f)) else Modifier).padding(vertical = 8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
            NavItem("menu", "Pulse", false, wide) { wide = !wide }
            Spacer(Modifier.height(6.dp))
            visible.forEach { NavItem(it.icon, it.label, page == it.id, wide) { _ -> go(it.id); wide = false } }
            Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 12.dp))
            NavItem(SettingsPg.icon, SettingsPg.label, page == "settings", wide) { go("settings"); wide = false }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, on: Boolean, wide: Boolean, onClick: (Unit) -> Unit) {
    val t = T
    val tint = if (on && t.material) Look.accent else t.text
    Row(Modifier.padding(horizontal = 6.dp, vertical = 1.dp).fillMaxWidth().height(40.dp)
        .background(if (on) (if (t.material) Look.accent.copy(alpha = .18f) else if (t.dark) Color.White.copy(alpha = .07f) else Color.Black.copy(alpha = .055f)) else Color.Transparent, RoundedCornerShape(if (t.material) 22.dp else t.radius.coerceAtMost(6.dp)))
        .clickable { onClick(Unit) }, verticalAlignment = Alignment.CenterVertically) {
        // Windows 11's little selection bar on the left edge
        Box(Modifier.width(3.dp).height(if (on) 18.dp else 0.dp).background(Look.accent, RoundedCornerShape(2.dp)))
        Box(Modifier.width(if (wide) 36.dp else 42.dp), contentAlignment = Alignment.Center) { PIcon(icon, tint, 21.dp) }
        if (wide) Text(label, color = tint, fontSize = 14.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Windows 7 / XP / 95: each with its own window, nav pane and status bar

@Composable
private fun ClassicShell(page: String, go: (String) -> Unit, body: @Composable () -> Unit) {
    val t = T
    val s by Sampler.snap.collectAsState()
    val all = visible + SettingsPg
    val xp = t.id == "xp"; val w7 = t.id == "win7"; val w95 = t.id == "win95"
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    Column(Modifier.fillMaxSize().background(if (w7) Color(0xFFB9D1EA) else t.bg).statusBarsPadding().navigationBarsPadding().padding(if (w7) 4.dp else if (xp) 3.dp else 2.dp)) {
        // title bar
        Row(Modifier.fillMaxWidth().height(if (xp) 32.dp else 28.dp)
            .background(t.title ?: androidx.compose.ui.graphics.SolidColor(t.accent), if (xp) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp) else if (w7) RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp) else RoundedCornerShape(0.dp))
            .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("performance", t.titleText, 16.dp); Spacer(Modifier.width(6.dp))
            Text(t.titleName + " — Pulse", color = t.titleText, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            listOf("–", "□", "×").forEach { g ->
                val red = g == "×"
                Box(Modifier.padding(start = 3.dp).size(if (w7) 26.dp else 20.dp, 18.dp)
                    .background(if (xp) (if (red) Color(0xFFD9542B) else Color(0xFF3F7BEA)) else if (w7) (if (red) Color(0xFFC75050) else Color(0x66FFFFFF)) else t.bg, RoundedCornerShape(if (xp) 4.dp else if (w7) 3.dp else 0.dp))
                    .then(if (w95) Modifier.bevel(true) else Modifier.border(1.dp, if (xp) Color.White else Color(0xFF6B7E96), RoundedCornerShape(if (xp) 4.dp else 3.dp))), contentAlignment = Alignment.Center) {
                    Text(g, color = if (xp || (w7 && red)) Color.White else Color.Black, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
        // menu bar: Windows 95 and XP have one, 7 hides it
        if (!w7) Row(Modifier.fillMaxWidth().background(t.bg).padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("File", "Options", "View", "Help").forEach { Text(it, color = t.text, fontSize = 13.sp) }
        }
        Row(Modifier.weight(1f).fillMaxWidth().background(if (w7) Color.White else t.bg)) {
            // navigation pane, one design per Windows version
            val paneBg: androidx.compose.ui.graphics.Brush = when {
                xp -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF7BA2E7), Color(0xFF6375D6)))
                w7 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE3EDF9)))
                else -> androidx.compose.ui.graphics.SolidColor(t.bg)
            }
            Column(Modifier.width(if (w95) 126.dp else 150.dp).fillMaxHeight().background(paneBg).then(if (w7) Modifier.border(1.dp, Color(0xFFB4C8E0)) else Modifier).padding(if (xp) 8.dp else 4.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (w95) 3.dp else 2.dp)) {
                if (xp) Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White, Color(0xFFC6D3F7))), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("Tasks", color = Color(0xFF215DC6), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                all.forEach { p ->
                    val on = page == p.id
                    when {
                        xp -> Row(Modifier.fillMaxWidth().background(if (on) Color(0xFFFFFFFF) else Color(0xFFD6DFF7)).clickable { go(p.id) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color(0xFF215DC6), 16.dp); Spacer(Modifier.width(6.dp))
                            Text(p.label, color = if (on) Color(0xFF000000) else Color(0xFF215DC6), fontSize = 12.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, textDecoration = if (on) null else androidx.compose.ui.text.style.TextDecoration.Underline, maxLines = 2)
                        }
                        w7 -> Row(Modifier.fillMaxWidth().then(if (on) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFEAF3FD), Color(0xFFC9DDF3))), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFF7DA2CE), RoundedCornerShape(3.dp)) else Modifier)
                            .clickable { go(p.id) }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color(0xFF1E5AA8), 17.dp); Spacer(Modifier.width(7.dp))
                            Text(p.label, color = Color(0xFF1E395B), fontSize = 12.sp, maxLines = 2)
                        }
                        else -> Row(Modifier.fillMaxWidth().background(t.bg).then(Modifier.bevel(!on)).clickable { go(p.id) }.padding(horizontal = 6.dp, vertical = 7.dp).offset(x = if (on) 1.dp else 0.dp, y = if (on) 1.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color.Black, 15.dp); Spacer(Modifier.width(5.dp))
                            Text(p.label, color = Color.Black, fontSize = 11.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, maxLines = 2)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(if (w7) 0.dp else 3.dp).background(t.panel).then(if (w95) Modifier.bevel(false) else if (xp) Modifier.border(1.dp, Color(0xFF7F9DB9)) else Modifier).padding(top = 8.dp)) { body() }
        }
        // status bar
        Row(Modifier.fillMaxWidth().background(if (w7) Color(0xFFE9EFF7) else t.bg).padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Processes: ${s.procs}", "CPU: ${s.cpu}%", "Memory: $memPct%").forEach { txt ->
                Text(txt, color = t.text, fontSize = 12.sp, modifier = Modifier.weight(1f).then(if (w95) Modifier.bevel(false).padding(horizontal = 4.dp, vertical = 2.dp) else Modifier.padding(horizontal = 4.dp)))
            }
        }
    }
}


// ---------------------------------------------------------------- Windows 10: white window, menu bar and a strip of tabs, as Task Manager had them

@Composable
private fun Win10Shell(page: String, go: (String) -> Unit, body: @Composable () -> Unit) {
    val t = T
    val s by Sampler.snap.collectAsState()
    val all = visible + SettingsPg
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val stripBg = if (t.dark) Color(0xFF121212) else Color(0xFFF1F1F1)
    Column(Modifier.fillMaxSize().background(t.bg).statusBarsPadding().navigationBarsPadding()) {
        // title bar: icon, name, the three caption buttons
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(10.dp)); PIcon("performance", Look.accent, 15.dp); Spacer(Modifier.width(8.dp))
            Text("Task Manager", color = t.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            listOf("\u2500", "\u25A1", "\u2715").forEach { g -> Box(Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Text(g, color = t.text, fontSize = 12.sp) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("File", "Options", "View").forEach { Text(it, color = t.text, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().background(stripBg).horizontalScroll(rememberScrollState()).padding(start = 6.dp, top = 4.dp)) {
            all.forEach { p ->
                val on = page == p.id
                Box(Modifier.then(if (on) Modifier.background(t.panel).border(1.dp, t.line) else Modifier).clickable { go(p.id) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(p.label, color = t.text, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, t.line).padding(top = 8.dp)) { body() }
        Row(Modifier.fillMaxWidth().background(stripBg).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Processes: ${s.procs}", color = t.sub, fontSize = 11.sp); Text("CPU: ${s.cpu}%", color = t.sub, fontSize = 11.sp); Text("Memory: $memPct%", color = t.sub, fontSize = 11.sp)
        }
    }
}
