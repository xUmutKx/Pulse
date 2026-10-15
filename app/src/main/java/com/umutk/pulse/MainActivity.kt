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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
        Look.blurDp = p.getFloat("blurDp2", 8f); Look.blurTint = p.getFloat("blurTint2", .2f)
        Look.dynamic = p.getBoolean("dynamic", true); Look.corner = p.getString("corner", "medium") ?: "medium"
        interval = p.getLong("interval", 1000L); Sampler.interval = interval
        confirmKill = p.getBoolean("confirm", true)
    }
    fun setTheme(c: Context, id: String) { Look.theme = Themes.first { it.id == id }; sp(c).edit().putString("theme", id).apply(); com.umutk.pulse.widgets.WidgetData.refreshNow(c) }
    fun setMode(c: Context, v: String) { Look.mode = v; sp(c).edit().putString("mode", v).apply() }
    fun setEffect(c: Context, v: String) { Look.effect = v; sp(c).edit().putString("effect", v).apply() }
    fun setBlur(c: Context) { sp(c).edit().putFloat("blurDp2", Look.blurDp).putFloat("blurTint2", Look.blurTint).apply() }
    fun setWindowTheme(c: Context, v: String) { Look.windowTheme = v; sp(c).edit().putString("windowTheme", v).apply() }
    fun setAccent(c: Context, i: Int) { Look.accentIdx = i; sp(c).edit().putInt("accent2", i).apply(); com.umutk.pulse.widgets.WidgetData.refreshNow(c) }
    fun setInterval(c: Context, v: Long) { interval = v; Sampler.interval = v; sp(c).edit().putLong("interval", v).apply() }
    fun setConfirm(c: Context, v: Boolean) { confirmKill = v; sp(c).edit().putBoolean("confirm", v).apply() }
    fun setDynamic(c: Context, v: Boolean) { Look.dynamic = v; sp(c).edit().putBoolean("dynamic", v).apply(); com.umutk.pulse.widgets.WidgetData.refreshNow(c) }
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
        // a fresh start plays the opening screen again, even when the process was still alive (back out, then open)
        SplashGate.splashed = savedInstanceState != null
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        // Folder tree and File types read the same walk of the storage: start it at launch so they open with the numbers ready
        if (android.os.Environment.isExternalStorageManager()) com.umutk.pulse.features.StorageScan.ensure(android.os.Environment.getExternalStorageDirectory())
        Shell.refreshShizuku()
        if (Shell.root != true) Thread { Shell.detectRoot() }.start()
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
        // a long-pressed Quick Settings tile: open the page of that tile instead of the app info screen
        if (i?.action == android.service.quicksettings.TileService.ACTION_QS_TILE_PREFERENCES)
            IntentCompat.getParcelableExtra(i, Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName::class.java)?.let { com.umutk.pulse.widgets.WidgetOpen.page = tilePage(it.className) }
        // Pulse chosen as the file manager: a folder VIEW opens Files full screen
        if (i?.action == Intent.ACTION_VIEW && (i.type == "resource/folder" || i.type == "vnd.android.document/directory")) {
            com.umutk.pulse.features.FilesLaunch.full = true; com.umutk.pulse.widgets.WidgetOpen.page = "files"; return
        }
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
    if (!t.material) return if (dark) darkTwin(t) ?: t else if (t.id == "win11") lightTwin(t) else t
    val amoled = t.id == "amoled"
    val dynamic = if (!amoled && Look.dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null
    // AMOLED has one fixed accent, white: no wallpaper colour and no swatch
    val accent = if (amoled) Color.White else dynamic?.primary ?: MaterialAccents.getOrNull(Look.accentIdx) ?: t.accent
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
    Pg("recycle", "Recycle bin", "recycle"),
    Pg("device", "Device", "phone"),
    Pg("health", "Health", "heart"),
    Pg("terminal", "Terminal", "run"),
)
private val visible get() = Pages.filter { it.id !in Opt.hidden && (it.id != "recycle" || Opt.recycle) }
private val SettingsPg = Pg("settings", "Settings", "settings")

@Composable
private fun Root() = LookHost { th ->
    // Windows 11 with a picture chosen: the app background is see-through and the picture shows behind everything
    // Windows 11 with the whole-app scope: the picture is the app background, blurred and tinted blue; the panels sit on top
    val pic = if (th.id == "win11" && com.umutk.pulse.features.Backdrop.mode != "off") com.umutk.pulse.features.Backdrop.image else null
    Surface(Modifier.fillMaxSize(), color = if (pic != null) androidx.compose.ui.graphics.Color.Transparent else th.bg) {
        if (pic != null) {
            // drawn with the crop from the background editor (position and zoom)
            // the blurred copy is rendered once (off the main thread) when the choice changes; here it is only drawn
            val shown = com.umutk.pulse.features.Backdrop.blurred ?: pic
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { with(com.umutk.pulse.features.Backdrop) { drawBackdrop(shown) } }
            Box(Modifier.fillMaxSize().background(com.umutk.pulse.micaTint().copy(alpha = .12f)))
        }
        val wctx = androidx.compose.ui.platform.LocalContext.current
        var welcomed by remember { mutableStateOf(wctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getBoolean("welcomed", false)) }
        // the open page lives here, not in App: App is remounted when the theme changes dark/light, and that must not close the page
        var appPage by rememberSaveable { mutableStateOf(if (Opt.startPage in Pages.map { it.id }) Opt.startPage else "processes") }
        var appSub by rememberSaveable { mutableStateOf("") }
        // the look's sign-in screen only when the setting is on, otherwise the Pulse splash
        if (!SplashGate.splashed) { if (Opt.signIn && th.id in loginLooks) LoginScreen(th) { SplashGate.splashed = true } else Splash(th) { SplashGate.splashed = true } }
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
            else Box(Modifier.fillMaxSize()) {
                GlassRoot(wallpaperKey = th.id to Backdrop.image, wallpaper = {
                    val img = Backdrop.image
                    if (img != null) with(com.umutk.pulse.features.Backdrop) { drawBackdrop(img) }
                    else drawRect(th.wall ?: SolidColor(th.bg))
                }) { content(th) }
                // the optional old-monitor filter, drawn over everything; it takes no touches
                if (Opt.crt) CrtOverlay()
            }
        }
    }
}

/** A page's own "one level up" (a Settings category, a tool inside Toolkit, Licences): the shared back arrow runs the newest
 *  one before leaving the page, so a page never draws a second arrow of its own. */
object InnerBack { val steps = androidx.compose.runtime.mutableStateListOf<() -> Unit>() }

/** Registers [onBack] with the shared back arrow and the back gesture while [enabled]. */
@Composable
fun InnerBackStep(enabled: Boolean, onBack: () -> Unit) {
    val cb by androidx.compose.runtime.rememberUpdatedState(onBack)
    if (enabled) androidx.compose.runtime.DisposableEffect(Unit) {
        val f = { cb() }
        InnerBack.steps.add(f)
        onDispose { InnerBack.steps.remove(f) }
    }
    androidx.activity.compose.BackHandler(enabled) { cb() }
}

@Composable
private fun App(page: String, onPage: (String) -> Unit, sub: String, onSub: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // the pages visited, so the back arrow and the back gesture return to the previous one
    var trail by remember { mutableStateOf(listOf<String>()) }
    val start = if (Opt.startPage in Pages.map { it.id }) Opt.startPage else "processes"
    val go: (String) -> Unit = { p -> onSub(""); if (p != page) trail = (trail + page).takeLast(20); onPage(p) }
    val back: () -> Unit = {
        val inner = InnerBack.steps.lastOrNull()
        if (inner != null) inner()
        else if (sub.isNotEmpty()) onSub("")
        else {
            val prev = trail.lastOrNull { it != page }
            trail = if (prev == null) emptyList() else trail.subList(0, trail.lastIndexOf(prev))
            onPage(prev ?: start)
        }
    }
    androidx.activity.compose.BackHandler(sub.isEmpty() && page != start) { back() }
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
    // tools that draw no back arrow of their own get the shared one; so does every page but the start page
    val bar = (sub.isEmpty() && page != start) || sub in setOf("toolkit", "installer", "installer_settings", "ram", "dns", "fonts", "guide") || InnerBack.steps.isNotEmpty()
    val body: @Composable () -> Unit = { Column(Modifier.fillMaxSize()) {
        if (bar) Box(Modifier.padding(start = 4.dp).size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
        Box(Modifier.weight(1f)) {
        if (sub.isNotEmpty()) SubScreen(sub, { onSub("") }) else when (page) {
            "processes" -> ProcessesPage(); "performance" -> PerformancePage(); "history" -> HistoryPage(); "startup" -> StartupPage()
            "details" -> DetailsPage(); "services" -> ServicesPage(); "apps" -> AppsPage(); "applock" -> com.umutk.pulse.features.AppLockPage(); "widgets" -> com.umutk.pulse.widgets.WidgetsPage(); "downloads" -> com.umutk.pulse.features.DownloadsPage(); "files" -> com.umutk.pulse.features.FilesPage(); "recycle" -> com.umutk.pulse.features.RecycleBinPage(); "installer" -> com.umutk.pulse.features.InstallerPage(); "device" -> DeviceModule(); "toolkit" -> com.umutk.pulse.features.ToolkitPage(); "health" -> com.umutk.pulse.features.HealthScreen { go("processes") }; "ram" -> com.umutk.pulse.features.RamPage(); "terminal" -> com.umutk.pulse.features.TerminalPage(); else -> SettingsPage()
        }
        }
    } }
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
                MenuOverflow(menus, t.sub)
            }
            Box(Modifier.weight(1f)) { body() }
        }
        }
        // the menu: labelled by default like the real Task Manager, drawn over the content so the page keeps its width; the hamburger folds it to icons
        Column(Modifier.width(if (wide) 210.dp else 52.dp).fillMaxHeight().then(if (wide) Modifier.background(t.bg.copy(alpha = if (t.id == "win11") .55f else .97f)).border(1.dp, t.line.copy(alpha = .5f)) else Modifier).padding(vertical = 8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
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
                        icon = { PNavIcon(p.icon, if (on) Look.accent else t.sub, 22.dp) },
                        label = { Text(pageName(p.id, p.label), maxLines = 1) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = Look.accent, selectedTextColor = Look.accent))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                MenuOverflow(menus, t.sub)
            }
            Box(Modifier.weight(1f)) { body() }
        }
    }
}

// ---------------------------------------------------------------- Windows 7 / XP / 95: each with its own window, nav pane and status bar

/** A title-bar button in the look of its Windows version: Windows 95 a grey bevel, Windows 7 glossy Aero glass (close red), XP a Luna blue or red-orange square. */
@Composable
private fun ClassicCaption(glyph: String, id: String, modifier: Modifier) {
    val red = glyph == "×"
    val look = when (id) {
        "win95" -> Modifier.background(Color(0xFFC0C0C0)).bevel(true)
        // Windows 7: a hard gloss line at half height; the group around the three clips the outer corners
        "win7" -> Modifier.background(
            if (red) Brush.verticalGradient(0f to Color(0xFFEBA899), .5f to Color(0xFFD5715B), .5f to Color(0xFFC1432A), 1f to Color(0xFFE0745A))
            else Brush.verticalGradient(0f to Color(0xD9CFE0F2), .5f to Color(0xC8A9C2E0), .5f to Color(0xC86F8FBA), 1f to Color(0xD99CBBE0)))
        else -> Modifier.background(Brush.linearGradient(
            if (red) listOf(Color(0xFFF4A98C), Color(0xFFE2552A), Color(0xFFC3381A)) else listOf(Color(0xFF9CC1FF), Color(0xFF3A75F0), Color(0xFF2152D8))), RoundedCornerShape(3.dp))
            .border(1.dp, Color.White, RoundedCornerShape(3.dp))
    }
    Box(modifier.then(look).drawBehind {
        val px = 1.dp.toPx()
        val sw = when (id) { "win95" -> 1.5f * px; "xp" -> 2f * px; else -> 1.6f * px }
        val g = size.minDimension * if (id == "win95") .3f else .26f
        // Windows 7: the joined group gets a thin dark line between the buttons
        if (id == "win7" && !red) drawLine(Color(0xC0203A5C), Offset(size.width - px / 2, 0f), Offset(size.width - px / 2, size.height), px)
        fun glyphAt(c: Color, d: Float) {
            val x = center.x + d; val y = center.y + d
            when (glyph) {
                "–" -> drawRect(c, Offset(x - g, y + g - sw), Size(g * 1.4f, sw))
                "□" -> { drawRect(c, Offset(x - g, y - g), Size(2 * g, 2 * g), style = Stroke(px)); drawRect(c, Offset(x - g, y - g), Size(2 * g, sw)) }
                else -> { drawLine(c, Offset(x - g, y - g), Offset(x + g, y + g), sw); drawLine(c, Offset(x + g, y - g), Offset(x - g, y + g), sw) }
            }
        }
        if (id == "win7") glyphAt(Color(0xB0102030), px)
        glyphAt(if (id == "win95") Color.Black else Color.White, 0f)
    })
}

@Composable
private fun ClassicShell(page: String, go: (String) -> Unit, menus: MenuHost, body: @Composable () -> Unit) {
    val t = T
    if (win95Authentic) { Win95Shell(page, go, menus, visible + SettingsPg, body); return } // real 95 window; off keeps the shell below
    val s by Sampler.snap.collectAsState()
    val all = visible + SettingsPg
    val xp = t.id == "xp"; val w7 = t.id == "win7"; val w95 = t.id == "win95"
    val a7 = w7 && Opt.authentic // authentic Win7: wallpaper behind light Aero glass, menu bar, tabs, Task Manager status bar
    val axp = xp && Opt.authentic // authentic XP: Bliss behind a Luna window, menu bar, tab page, sunken status panes
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val top8 = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    val top6 = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
    // the window frame: Windows 7 Aero glass (see-through sky blue, a diagonal sheen, dark outer and white inner edge), XP the Luna blue frame, 95 a raised grey bevel
    val chrome = when {
        w7 -> Modifier.clip(top6).background(if (a7) Brush.verticalGradient(listOf(Color(0xA0BCD8F2), Color(0x8094BCE4), Color(0x90A9CAEC)))
            else Brush.verticalGradient(listOf(Color(0xC88DB7E2), Color(0xB06FA0D4), Color(0xC07FADDC))))
            .drawBehind {
                drawRect(Brush.linearGradient(0f to Color.Transparent, .38f to Color.Transparent, .44f to Color.White.copy(alpha = .22f), .5f to Color.Transparent,
                    .56f to Color.White.copy(alpha = .12f), .6f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.width * .8f)))
            }
            .border(1.dp, Color(0xB0000000), top6).padding(1.dp).border(1.dp, Color.White.copy(alpha = .6f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).padding(1.dp)
        // authentic: inset so Bliss shows round the window, Luna frame darker at the edges
        axp -> Modifier.padding(horizontal = 6.dp, vertical = 10.dp).background(Brush.horizontalGradient(listOf(Color(0xFF0831D9), Color(0xFF0054E3), Color(0xFF0054E3), Color(0xFF0831D9))), top8)
        xp -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0054E3), Color(0xFF0831D9))), top8)
        w95 -> Modifier.background(Color(0xFFC0C0C0)).bevel(true).padding(3.dp)
        else -> Modifier.background(t.bg)
    }
    val side = when { w7 -> 6.dp; xp -> 4.dp; else -> 0.dp }
    // the navigation pane: icons only, or wide with the menus and page names; the toggle at its top switches
    var navWide by rememberSaveable(t.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    // Aero: the glass shows the blurred desktop wallpaper behind the window
    if (a7) com.umutk.pulse.features.ThemeWallPicture("win7", Modifier.matchParentSize().blur(14.dp))
    if (axp) com.umutk.pulse.features.ThemeWallPicture("xp", Modifier.matchParentSize()) // Bliss
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().then(chrome)) {
        // title bar
        val titleBg = when {
            // Windows 7: the bar is the frame's glass, with the gloss over its top half
            w7 -> Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.Transparent), endY = size.height / 2), size = Size(size.width, size.height / 2)) }
            // XP Luna: a lighter line along the top, then the deep glossy blue
            xp -> Modifier.background(Brush.verticalGradient(0f to Color(0xFF5AA6FF), .08f to Color(0xFF1E6FF5), .2f to Color(0xFF0058EE), .7f to Color(0xFF0050E0), .9f to Color(0xFF0060F8), 1f to Color(0xFF0046C8)), top8)
            // Windows 95: solid navy (the gradient came with 98)
            else -> Modifier.background(Color(0xFF000080))
        }
        val bold = androidx.compose.ui.text.font.FontWeight.Bold
        val titleStyle = when {
            w7 -> androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 13.sp, shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset.Zero, 12f))
            // authentic: Trebuchet MS is not on Android; condensed sans bold is the closest
            axp -> androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = bold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, letterSpacing = (-0.2).sp, shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF0F1089), Offset(2f, 2f), 1f))
            xp -> androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = bold, shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF0A1A5C), Offset(1.5f, 1.5f), 1.5f))
            else -> androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = bold, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)
        }
        Row(Modifier.fillMaxWidth().height(if (w95) 22.dp else 30.dp).then(titleBg).padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("performance", if (w7) Color(0xFF1E5AA8) else Color.White, if (w95) 14.dp else 16.dp); Spacer(Modifier.width(6.dp))
            Text(if (a7 || axp) t.titleName else t.titleName + " — Pulse", style = titleStyle, modifier = Modifier.weight(1f), maxLines = 1)
            // Windows 7: one joined group hanging from the top edge; XP: separate squares; 95: close set 2 px apart
            val cap7 = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
            Row(if (w7) Modifier.align(Alignment.Top).clip(cap7).border(1.dp, Color(0xD0203A5C), cap7) else Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (xp) 2.dp else 0.dp)) {
                listOf("–", "□", "×").forEach { g ->
                    if (w95 && g == "×") Spacer(Modifier.width(2.dp))
                    if (axp) Box(Modifier.size(21.dp).drawBehind { xpCaption(g == "×", g == "□") }) // the Luna bitmaps, as on the dialogs
                    else ClassicCaption(g, t.id, when { w7 -> Modifier.size(if (g == "×") 40.dp else 24.dp, 19.dp); xp -> Modifier.size(21.dp); else -> Modifier.size(16.dp, 14.dp) })
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = side, end = side, bottom = side, top = if (w95) 2.dp else 0.dp)) {
        // XP menu bar on the beige window body
        if (axp) Row(Modifier.fillMaxWidth().height(32.dp).background(Color(0xFFECE9D8)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) { MenuBar(menus, Color.Black, 13.sp) }
        if (a7) Aero7Client(page, all, go, menus, Modifier.weight(1f), body)
        else Row(Modifier.weight(1f).fillMaxWidth().background(if (w7) Color.White else t.bg)) {
            // navigation pane, one design per Windows version
            val paneBg: androidx.compose.ui.graphics.Brush = when {
                xp -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF7BA2E7), Color(0xFF6375D6)))
                w7 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE3EDF9)))
                else -> androidx.compose.ui.graphics.SolidColor(t.bg)
            }
            Column(Modifier.width(if (navWide) 172.dp else 44.dp).fillMaxHeight().background(paneBg).then(if (w7) Modifier.border(1.dp, Color(0xFFB4C8E0)) else Modifier).padding(if (xp && !axp) 8.dp else 4.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (w95) 3.dp else if (xp) 0.dp else 2.dp)) {
                // collapse toggle, then the menus (File, Options, View, Help) as a list; wide shows the words
                // XP: the toggle is the task pane's group header, white to pale blue with rounded top corners
                val head = if (xp) Color(0xFF215DC6) else t.text
                Row(Modifier.fillMaxWidth().then(if (xp) Modifier.background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFC6D3F7))), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)) else Modifier)
                    .clickable { navWide = !navWide }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // the hamburger, as in the modern pane; expanded it shows the word, and the menus sit below it
                    PIcon("menu", head, 20.dp)
                    if (navWide) Text("Pulse", color = head, fontSize = 13.sp, fontWeight = if (xp) androidx.compose.ui.text.font.FontWeight.Bold else null, modifier = Modifier.padding(start = 8.dp))
                }
                if (navWide && !axp) PaneMenus(menus, t.text, true) // authentic XP has the menu bar
                all.forEach { p ->
                    val on = page == p.id
                    when {
                        // authentic: 40 dp rows, the 24 dp icon fills the 36 dp cell, centred when narrow
                        axp -> Row(Modifier.fillMaxWidth().height(40.dp).background(if (on) Color.White else Color(0xFFD6DFF7)).then(if (on) Modifier.border(1.dp, Color(0xFF7F9DB9)) else Modifier).clickable { go(p.id) }.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (navWide) Arrangement.Start else Arrangement.Center) {
                            PIcon(p.icon, Color(0xFF215DC6), 24.dp); if (navWide) Text(p.label, color = if (on) Color.Black else Color(0xFF215DC6), fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                        }
                        xp -> Row(Modifier.fillMaxWidth().background(if (on) Color(0xFFFFFFFF) else Color(0xFFD6DFF7)).clickable { go(p.id) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color(0xFF215DC6), 16.dp); if (navWide) Text(p.label, color = t.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                        }
                        w7 -> Row(Modifier.fillMaxWidth().then(if (on) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFEAF3FD), Color(0xFFC9DDF3))), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFF7DA2CE), RoundedCornerShape(3.dp)) else Modifier)
                            .clickable { go(p.id) }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color(0xFF1E5AA8), 17.dp); if (navWide) Text(p.label, color = t.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                        }
                        else -> Row(Modifier.fillMaxWidth().background(t.bg).then(Modifier.bevel(!on)).clickable { go(p.id) }.padding(horizontal = 6.dp, vertical = 7.dp).offset(x = if (on) 1.dp else 0.dp, y = if (on) 1.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon(p.icon, Color.Black, 15.dp); if (navWide) Text(p.label, color = t.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            // authentic XP: a tab control, the current page as the white front tab with the orange hot line on top
            if (axp) Column(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, top = 6.dp, end = 3.dp, bottom = 3.dp)) {
                val tabShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                Column(Modifier.padding(start = 2.dp).offset(y = 1.dp).zIndex(1f).width(IntrinsicSize.Max).clip(tabShape).background(Color(0xFFFCFCFE)).border(1.dp, Color(0xFF919B9C), tabShape)) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.verticalGradient(listOf(Color(0xFFFFC83C), Color(0xFFE68B2C)))))
                    Text(all.firstOrNull { it.id == page }?.let { pageName(it.id, it.label) } ?: "", color = Color.Black, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFCFCFE)).border(1.dp, Color(0xFF919B9C)).padding(top = 8.dp)) { body() }
            }
            else Box(Modifier.weight(1f).fillMaxHeight().padding(if (w7) 0.dp else 3.dp).background(t.panel).then(if (w95) Modifier.bevel(false) else if (xp) Modifier.border(1.dp, Color(0xFF7F9DB9)) else Modifier).padding(top = 8.dp)) { body() }
        }
        // status bar
        // Win7 Task Manager status bar: three panes split by etched lines on the light grey face
        if (a7) Row(Modifier.fillMaxWidth().background(Color(0xFFF0F0F0)).padding(horizontal = 2.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Processes: ${s.procs}", "CPU Usage: ${s.cpu}%", "Physical Memory: $memPct%").forEachIndexed { i, txt ->
                if (i > 0) Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFA0A0A0)))
                FitText(txt, Color.Black, 12.sp, Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        // XP status bar: three sunken panes on the beige face
        else if (axp) Row(Modifier.fillMaxWidth().background(Color(0xFFECE9D8)).padding(horizontal = 2.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("Processes: ${s.procs}", "CPU Usage: ${s.cpu}%", "Commit Charge: $memPct%").forEach { txt ->
                FitText(txt, Color.Black, 12.sp, Modifier.weight(1f).border(1.dp, Color(0xFFACA899)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        else Row(Modifier.fillMaxWidth().background(if (w7) Brush.verticalGradient(listOf(Color(0xFFF4F4F4), Color(0xFFDADADA))) else SolidColor(t.bg)).padding(horizontal = 4.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Processes: ${s.procs}", "CPU: ${s.cpu}%", "Memory: $memPct%").forEach { txt ->
                Text(txt, color = t.text, fontSize = 12.sp, modifier = Modifier.weight(1f).then(if (w95) Modifier.bevel(false).padding(horizontal = 4.dp, vertical = 2.dp) else Modifier.padding(horizontal = 4.dp)))
            }
        }
        }
    }
    }
}

/** Authentic Windows 7 Task Manager client: menu bar, the tab control and the white page joined to the selected tab. */
@Composable
private fun Aero7Client(page: String, all: List<Pg>, go: (String) -> Unit, menus: MenuHost, modifier: Modifier, body: @Composable () -> Unit) {
    val edge = Color(0xFF898C95)
    Column(modifier.fillMaxWidth().background(Color(0xFFF0F0F0))) {
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE5ECF6)))).padding(horizontal = 8.dp, vertical = 6.dp)) {
            MenuBar(menus, Color.Black, 13.sp)
        }
        // the strip's bottom line is the page's top edge; the white selected tab paints over it so the two join
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp).drawBehind { val px = 1.dp.toPx(); drawLine(edge, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px) }
            .horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), verticalAlignment = Alignment.Bottom) {
            all.forEach { p ->
                val on = page == p.id
                val tab = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                Box(Modifier.height(if (on) 40.dp else 37.dp).clip(tab)
                    .background(if (on) SolidColor(Color.White) else Brush.verticalGradient(0f to Color(0xFFF2F2F2), .5f to Color(0xFFEBEBEB), .5f to Color(0xFFDDDDDD), 1f to Color(0xFFCFCFCF)))
                    .drawBehind {
                        val px = 1.dp.toPx()
                        // left, top and right edge; the stroke runs past the bottom so only unselected tabs get a bottom line
                        drawRoundRect(edge, Offset(px / 2, px / 2), Size(size.width - px, size.height + 4 * px), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(px))
                        if (!on) drawLine(edge, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
                    }
                    .clickable { go(p.id) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(pageName(p.id, p.label), color = Color.Black, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp).background(Color.White).drawBehind {
            val px = 1.dp.toPx(); val r = size.width - px / 2; val b = size.height - px / 2
            drawLine(edge, Offset(px / 2, 0f), Offset(px / 2, b), px); drawLine(edge, Offset(r, 0f), Offset(r, b), px); drawLine(edge, Offset(0f, b), Offset(size.width, b), px)
        }.padding(top = 8.dp, start = 1.dp, end = 1.dp, bottom = 1.dp)) { body() }
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


/** An old CRT screen over the app: scan lines, a faint RGB dot mask and darker corners. Settings > General, off by default. */
@Composable
private fun CrtOverlay() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val line = 3.dp.toPx()
        var y = 0f
        while (y < size.height) { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = .16f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, line / 3f)); y += line }
        // the shadow mask: thin red, green and blue columns
        val dot = 1.dp.toPx()
        val rgb = listOf(androidx.compose.ui.graphics.Color(0x14FF0000), androidx.compose.ui.graphics.Color(0x1400FF00), androidx.compose.ui.graphics.Color(0x140000FF))
        var x = 0f; var i = 0
        while (x < size.width) { drawRect(rgb[i % 3], androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(dot, size.height)); x += dot; i++ }
        drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)),
            center = center, radius = size.maxDimension * .75f))
    }
}
