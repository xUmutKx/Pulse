package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import java.io.File
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntSize
import com.umutk.pulse.features.Backdrop
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.umutk.pulse.features.Downloads
import com.umutk.pulse.features.Installer
import com.umutk.pulse.features.InstallState
import com.umutk.pulse.features.InstallWindow

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
        Look.windowTheme = p.getString("windowTheme", "win") ?: "win"
        Look.dynamic = p.getBoolean("dynamic", true); Look.corner = p.getString("corner", "medium") ?: "medium"
        interval = p.getLong("interval", 1000L); Sampler.interval = interval
        confirmKill = p.getBoolean("confirm", true)
    }
    fun setTheme(c: Context, id: String) { Look.theme = Themes.first { it.id == id }; sp(c).edit().putString("theme", id).apply() }
    fun setMode(c: Context, v: String) { Look.mode = v; sp(c).edit().putString("mode", v).apply() }
    fun setEffect(c: Context, v: String) { Look.effect = v; sp(c).edit().putString("effect", v).apply() }
    fun setWindowTheme(c: Context, v: String) { Look.windowTheme = v; sp(c).edit().putString("windowTheme", v).apply() }
    fun setAccent(c: Context, i: Int) { Look.accentIdx = i; sp(c).edit().putInt("accent2", i).apply() }
    fun setInterval(c: Context, v: Long) { interval = v; Sampler.interval = v; sp(c).edit().putLong("interval", v).apply() }
    fun setConfirm(c: Context, v: Boolean) { confirmKill = v; sp(c).edit().putBoolean("confirm", v).apply() }
    fun setDynamic(c: Context, v: Boolean) { Look.dynamic = v; sp(c).edit().putBoolean("dynamic", v).apply() }
    fun setCorner(c: Context, v: String) { Look.corner = v; sp(c).edit().putString("corner", v).apply() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this); Downloads.load(this); Installer.load(this); SplashPic.load(this); com.umutk.pulse.features.GlassPrefs.load(this); Thread { com.umutk.pulse.features.Backdrop.load(this) }.start(); com.umutk.pulse.features.initThemeWall(this); Shell.watchShizuku()
        // installer copies from an earlier run are dead weight: a fresh start clears them (a restored instance keeps its window)
        if (savedInstanceState == null) File(cacheDir, "installer").deleteRecursively()
        Watchdog.sync(this)
        // coming back to a process Android restarted: the saved page and scroll are restored, so the opening screen is not played again
        if (savedInstanceState != null) SplashGate.splashed = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Copies an APK from another app to the cache off the main thread, then the install window shows it. */
    private fun handleApk(uri: Uri) {
        Thread {
            val f = File(cacheDir, "open/open.apk").apply { parentFile?.mkdirs() }
            try { contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: throw java.io.IOException("no content") }
            catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Could not read the APK: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }; return@Thread }
            runOnUiThread { InstallState.file = f }
        }.start()
    }

    /** A link shared from another app (browser "Share") starts a download right away. */
    private fun handleShare(i: Intent?) {
        i?.getStringExtra("start")?.let { com.umutk.pulse.widgets.WidgetOpen.page = it }
        if (i?.getBooleanExtra("downloads", false) == true) { Downloads.wanted = true; return }
        if (i?.type == "application/vnd.android.package-archive") {
            val uri = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) handleApk(uri)
            return
        }
        if (i?.action != Intent.ACTION_SEND || i.type != "text/plain") return
        val url = Regex("https?://\\S+").find(i.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.value ?: return
        Downloads.add(this, url)
        Downloads.wanted = true
    }
}

/** The Material looks follow the system dark mode, the wallpaper colours (Android 12+, when Material You is on), the chosen accent and corner size. */
@Composable
fun effective(t: PTheme): PTheme {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (Look.mode) { "dark" -> true; "light" -> false; else -> sysDark }
    if (!t.material) return if (dark) darkTwin(t) ?: t else t
    val amoled = t.id == "amoled"
    val dynamic = if (!amoled && Look.dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null
    val accent = dynamic?.primary ?: Accents.getOrNull(Look.accentIdx) ?: t.accent
    val r = Look.cornerDp
    return when {
        dynamic != null -> PTheme(t.id, t.name, dark, if (dark) Color(0xFF050505) else dynamic.background, if (dark) Color(0xFF111111) else dynamic.surfaceContainer,
            dynamic.onBackground, dynamic.onSurfaceVariant, accent, dynamic.outlineVariant, r, material = true, glassA = t.glassA)
        amoled -> PTheme(t.id, t.name, true, t.bg, t.panel, t.text, t.sub, accent, t.line, r, material = true, glassA = t.glassA)
        // Material You off: the fixed Material 3 palette, light or dark
        dark -> PTheme(t.id, t.name, true, Color(0xFF141218), Color(0xFF211F26), Color(0xFFE6E1E5), Color(0xFFCAC4D0), accent, Color(0xFF49454F), r, material = true, glassA = t.glassA)
        else -> PTheme(t.id, t.name, false, t.bg, t.panel, t.text, t.sub, accent, t.line, r, material = true, glassA = t.glassA)
    }
}

/** The opening screen plays once per start of the app: not on a look change, not when the app comes back from the background. */
object SplashGate { var splashed by mutableStateOf(false) }

class Pg(val id: String, val label: String, val icon: String)

val Pages = listOf(
    Pg("processes", "Processes", "processes"),
    Pg("performance", "Performance", "performance"),
    Pg("history", "App history", "history"),
    Pg("startup", "Startup apps", "startup"),
    Pg("details", "Details", "details"),
    Pg("services", "Services", "services"),
    Pg("apps", "Apps", "apps"),
    Pg("downloads", "Downloads", "download"),
    Pg("files", "Files", "folder"),
    Pg("device", "Device", "phone"),
    Pg("health", "Health", "heart"),
    Pg("terminal", "Terminal", "run"),
)
private val visible get() = Pages.filter { it.id !in Opt.hidden }
private val SettingsPg = Pg("settings", "Settings", "settings")

@Composable
private fun Root() = LookHost { th ->
    // Windows 11 with a picture chosen: the app background is see-through and the picture shows behind everything
    // Windows 11 with the whole-app scope: the picture is the app background, blurred and tinted blue; the panels sit on top
    val pic = if (th.id == "win11" && com.umutk.pulse.features.Backdrop.scope == "app") com.umutk.pulse.features.Backdrop.image else null
    Surface(Modifier.fillMaxSize(), color = if (pic != null) androidx.compose.ui.graphics.Color.Transparent else th.bg) {
        if (pic != null) {
            androidx.compose.foundation.Image(pic, null, Modifier.fillMaxSize().blur(com.umutk.pulse.features.Backdrop.blur.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = com.umutk.pulse.features.Backdrop.alignment())
            Box(Modifier.fillMaxSize().background(com.umutk.pulse.micaTint().copy(alpha = .35f)))
        }
        val wctx = androidx.compose.ui.platform.LocalContext.current
        var welcomed by remember { mutableStateOf(wctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getBoolean("welcomed", false)) }
        // the open page lives here, not in App: App is remounted when the theme changes dark/light, and that must not close the page
        var appPage by rememberSaveable { mutableStateOf(if (Opt.startPage in Pages.map { it.id }) Opt.startPage else "processes") }
        var appSub by rememberSaveable { mutableStateOf("") }
        if (!SplashGate.splashed) Splash(th) { SplashGate.splashed = true }
        else if (!welcomed) Welcome { welcomed = true }
        else key(th.dark) { App(appPage, { appPage = it }, appSub, { appSub = it }) }
    }
}

/** Resolves the look and provides it to the content: MainActivity and InstallActivity both use it. */
@Composable
fun LookHost(transparent: Boolean = false, content: @Composable (PTheme) -> Unit) {
    val base = Look.theme
    val th = effective(base)
    // the rest of the UI reads Look.theme through T, so the resolved Material look is swapped in while composing
    Look.resolved = if (th !== base) th else null
    // pop-up windows take their own look: the app look, Material, or AMOLED black
    val wl = when (Look.windowTheme) { "material" -> monoWindow(effective(Themes.first { it.id == "material" })); "amoled" -> amoledWindow(); else -> th }
    val acc = Look.accent
    val cs = if (th.dark) darkColorScheme(primary = acc, onPrimary = onAccent(acc), background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    else lightColorScheme(primary = acc, onPrimary = onAccent(acc), background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect { WindowCompat.getInsetsController((view.context as android.app.Activity).window, view).apply { isAppearanceLightStatusBars = !th.dark; isAppearanceLightNavigationBars = !th.dark } }
    SideEffect { val w = (view.context as android.app.Activity).window; if (Opt.keepOn) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * Opt.fontScale), LocalWindowLook provides wl) {
        MaterialTheme(colorScheme = cs) {
            // liquid glass: the wallpaper (or the theme's backdrop) is blurred behind the glass panels
            // the installer window (transparent): nothing is drawn behind it, the app behind shows through
            if (transparent) Box(Modifier.fillMaxSize()) { content(th) }
            else GlassRoot(wallpaperKey = th.id to Backdrop.image, wallpaper = {
                val img = Backdrop.image
                if (img != null) drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                else drawRect(th.wall ?: SolidColor(th.bg))
            }) { content(th) }
        }
    }
}

@Composable
private fun App(page: String, onPage: (String) -> Unit, sub: String, onSub: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val go: (String) -> Unit = { onSub(""); onPage(it) }
    LaunchedEffect(Downloads.wanted) { if (Downloads.wanted) { go("downloads"); Downloads.wanted = false } }
    LaunchedEffect(com.umutk.pulse.widgets.WidgetOpen.page) {
        val p = com.umutk.pulse.widgets.WidgetOpen.page
        if (p.isNotEmpty()) { if (p == "toolkit" || p.startsWith("toolkit:")) { com.umutk.pulse.features.ToolkitOpen.tool = p.substringAfter(":", ""); onSub("toolkit") } else if (p == SettingsPg.id || Pages.any { it.id == p }) go(p) else if (p == "installer_settings") onSub(p); com.umutk.pulse.widgets.WidgetOpen.page = "" }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            Shell.detectRoot()
            while (true) { Sampler.sample(ctx); delay(Sampler.interval) }
        }
    }
    val body: @Composable () -> Unit = {
        if (sub.isNotEmpty()) SubScreen(sub, { onSub("") }) else when (page) {
            "processes" -> ProcessesPage(); "performance" -> PerformancePage(); "history" -> HistoryPage(); "startup" -> StartupPage()
            "details" -> DetailsPage(); "services" -> ServicesPage(); "apps" -> AppsPage(); "downloads" -> com.umutk.pulse.features.DownloadsPage(); "files" -> com.umutk.pulse.features.FilesPage(); "installer" -> com.umutk.pulse.features.InstallerPage(); "device" -> DeviceModule(); "toolkit" -> com.umutk.pulse.features.ToolkitPage(); "health" -> com.umutk.pulse.features.HealthScreen { go("processes") }; "ram" -> com.umutk.pulse.features.RamPage(); "terminal" -> com.umutk.pulse.features.TerminalPage(); else -> SettingsPage()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalOpenTool provides { onSub(it) }) {
        val menus = rememberMenus(go)
        if (T.id == "win10") Win10Shell(page, go, menus, body) else if (T.tabs) ClassicShell(page, go, menus, body) else ModernShell(page, go, menus, body)
        menus.dialogs()
    }
    InstallState.file?.let { f -> InstallWindow(f) { InstallState.file = null } }
}

// ---------------------------------------------------------------- Windows 11 / 10, Material, AMOLED: a sidebar on the left

@Composable
private fun ModernShell(page: String, go: (String) -> Unit, menus: MenuHost, body: @Composable () -> Unit) {
    val t = T
    if (t.material) { MaterialRail(page, go, menus, body); return }
    var wide by rememberSaveable(t.id) { mutableStateOf(false) }
    val s by Sampler.snap.collectAsState()
    val fx = if (t.id == "win11") Look.effect else "none"
    Box(Modifier.fillMaxSize().backdrop(fx).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxSize()) {
        Spacer(Modifier.width(52.dp))
        val corner = if (t.material) t.radius else if (t.radius > 0.dp) 8.dp else 0.dp
        Column(Modifier.weight(1f).fillMaxHeight().layer(fx, RoundedCornerShape(topStart = corner, bottomStart = corner)).then(if (!t.material && t.radius == 0.dp) Modifier.border(1.dp, t.line) else Modifier).padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (s.root) Text("root", color = Look.accent, fontSize = 12.sp)
                MenuOverflow(menus, t.sub)
            }
            Box(Modifier.weight(1f)) { body() }
        }
        }
        // the menu: labelled by default like the real Task Manager, drawn over the content so the page keeps its width; the hamburger folds it to icons
        Column(Modifier.width(if (wide) 210.dp else 52.dp).fillMaxHeight().then(if (wide) Modifier.background(t.bg.copy(alpha = .97f)).border(1.dp, t.line.copy(alpha = .5f)) else Modifier).padding(vertical = 8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
            NavItem("menu", "Pulse", false, wide) { wide = !wide }
            Spacer(Modifier.height(6.dp))
            visible.forEach { NavItem(it.icon, pageName(it.id, it.label), page == it.id, wide) { _ -> go(it.id); wide = false } }
            Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 12.dp))
            NavItem(SettingsPg.icon, pageName("settings", SettingsPg.label), page == "settings", wide) { go("settings"); wide = false }
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

/** Material look: the M3 navigation rail on the left, and the page under a 64 dp small top app bar. */
@Composable
private fun MaterialRail(page: String, go: (String) -> Unit, menus: MenuHost, body: @Composable () -> Unit) {
    val t = T
    val s by Sampler.snap.collectAsState()
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        NavigationRail(containerColor = t.bg) {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                (visible + SettingsPg).forEach { p ->
                    val on = page == p.id
                    NavigationRailItem(selected = on, onClick = { go(p.id) },
                        icon = { PIcon(p.icon, if (on) Look.accent else t.sub, 22.dp) },
                        label = { Text(pageName(p.id, p.label), maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Look.accent, selectedTextColor = Look.accent))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (s.root) Text("root", color = Look.accent, fontSize = 12.sp)
                MenuOverflow(menus, t.sub)
            }
            Box(Modifier.weight(1f)) { body() }
        }
    }
}

// ---------------------------------------------------------------- Windows 7 / XP / 95: each with its own window, nav pane and status bar

@Composable
private fun ClassicShell(page: String, go: (String) -> Unit, menus: MenuHost, body: @Composable () -> Unit) {
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
            MenuBar(menus, t.text, 13.sp)
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
private fun Win10Shell(page: String, go: (String) -> Unit, menus: MenuHost, body: @Composable () -> Unit) {
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
            MenuBar(menus, t.text, 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(stripBg).horizontalScroll(rememberScrollState()).padding(start = 6.dp, top = 4.dp)) {
            all.forEach { p ->
                val on = page == p.id
                Box(Modifier.then(if (on) Modifier.background(t.panel).border(1.dp, t.line) else Modifier).clickable { go(p.id) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    FitText(p.label, t.text, 12.sp)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().border(1.dp, t.line).padding(top = 8.dp)) { body() }
        Row(Modifier.fillMaxWidth().background(stripBg).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Processes: ${s.procs}", color = t.sub, fontSize = 11.sp); Text("CPU: ${s.cpu}%", color = t.sub, fontSize = 11.sp); Text("Memory: $memPct%", color = t.sub, fontSize = 11.sp)
        }
    }
}
