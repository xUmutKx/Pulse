package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.view.RoundedCorner
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.umutk.pulse.AppLookGuard
import com.umutk.pulse.ExtraToolActivity
import com.umutk.pulse.FilesActivity
import com.umutk.pulse.MainActivity
import com.umutk.pulse.tr
import com.umutk.pulse.Cfg
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Pulse Launcher: a HOME app with its own look and layout. Runs in its own process, so Pulse's Look / Cfg / Opt stay untouched. */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // rounded corners are not part of the Compose insets, so the side inset is read from the window insets here
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets -> sideInsetPx = insetPx(insets); ViewCompat.onApplyWindowInsets(v, insets) }
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        setContent {
            val look = LauncherLooks.byId(LauncherPrefs.look)
            val apps by produceState(emptyList<LaunchApp>(), LauncherPrefs.iconPack) { value = withContext(Dispatchers.IO) { loadApps(this@LauncherActivity) } }
            LaunchedEffect(look.id, LauncherPrefs.sounds) { LauncherSounds.prepare(this@LauncherActivity, look.id) }
            BackHandler(enabled = LauncherSession.startOpen) { LauncherSession.startOpen = false }
            val edge = with(LocalDensity.current) { sideInsetPx.toDp() }
            LookHost(lookId = look.id) { th ->
                WindowsCursor(th.id in com.umutk.pulse.loginLooks) {
                key(look.id) { CompositionLocalProvider(LocalLauncherEdge provides edge) { LauncherRoot(look, apps) } }
                }
                // the Windows looks start like a PC: boot screen, then the sign-in screen (once per launcher process)
                if (!LauncherSession.booted && th.id in com.umutk.pulse.loginLooks) {
                    var stage by remember { mutableIntStateOf(if (LauncherPrefs.bootScreen) 0 else if (LauncherPrefs.loginScreen) 1 else 2) }
                    when (stage) {
                        0 -> com.umutk.pulse.Splash(th) { stage = if (LauncherPrefs.loginScreen) 1 else 2 }
                        1 -> com.umutk.pulse.LoginScreen(th) { stage = 2 }
                        else -> LaunchedEffect(Unit) { LauncherSession.booted = true; LauncherSounds.startup(this@LauncherActivity, th.id) }
                    }
                } else LaunchedEffect(Unit) { LauncherSession.booted = true }
            }
        }
    }

    private var sideInsetPx by mutableIntStateOf(0)

    /** Largest side intrusion in px: a rounded screen corner (API 31+, the minimum here) or a side display cutout. */
    private fun insetPx(insets: WindowInsetsCompat): Int {
        val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val platform = insets.toWindowInsets()
        val corner = listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT).maxOf { if (android.os.Build.VERSION.SDK_INT >= 31) platform?.getRoundedCorner(it)?.radius ?: 0 else 0 }
        return maxOf(cut.left, cut.right, corner)
    }

    // a home press while the launcher is already open: close the apps list and go back to the first page
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); LauncherSession.startOpen = false; LauncherSession.homeTick++ }

    /** Reads the saved settings again on every start / resume: the Pulse process may have changed them meanwhile. */
    private fun enter() {
        LauncherPrefs.load(this)
        AppLookGuard.enter({ LauncherPrefs.apply() }, { })
        requestedOrientation = if (LauncherPrefs.lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // shortcut: BEHAVIOR_SHOW_TRANSIENT_BY_SWIPE (2) written out, the constant is missing from this compile SDK's stubs
            systemBarsBehavior = 2
            if (LauncherPrefs.fullScreen) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
        }
    }
    override fun onStart() { enter(); super.onStart() }
    override fun onResume() { enter(); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); AppLookGuard.leave(this) }
}

private const val ICON_PX = 128
private const val PAGE_ROWS = 4

/** The looks that are a Windows desktop: their home screen holds the desktop shortcuts. */
private val WINDOWS_LOOKS = setOf("win11", "win10", "win10black", "win7", "win95", "xp")

/** The desktop shortcuts: This PC, Recycle Bin, File Explorer, Paint, the command prompt and the Task Manager. Drawn through the look's own icon set. */
internal fun desktopItems(): List<LaunchApp> = listOf(
    LaunchApp(tr("This PC", "Bu bilgisayar"), "pulse:pc", null, iconKey = "phone"),
    LaunchApp(tr("Recycle Bin", "Geri Dönüşüm Kutusu"), "pulse:recycle", null, iconKey = "recycle"),
    LaunchApp(tr("File Explorer", "Dosya Gezgini"), "pulse:files", null, iconKey = "folder"),
    LaunchApp(tr("Paint", "Paint"), "pulse:paint", null, iconKey = "palette"),
    LaunchApp(tr("Command Prompt", "Komut İstemi"), "pulse:cmd", null, iconKey = "run"),
    LaunchApp(tr("Task Manager", "Görev Yöneticisi"), "pulse:taskmgr", null, iconKey = "processes"),
    LaunchApp(tr("Control Panel", "Denetim Masası"), "pulse:settings", null, iconKey = "settings"),
    LaunchApp(tr("Settings", "Ayarlar"), "pulse:winsettings", null, iconKey = "settings"),
    LaunchApp(tr("Store", "Mağaza"), "pulse:store", null, iconKey = "download"),
    LaunchApp(tr("Control Center", "Denetim Merkezi"), "pulse:control", null, iconKey = "performance"),
    LaunchApp(tr("Performance", "Performans"), "pulse:perf", null, iconKey = "performance"),
    LaunchApp(tr("Services", "Hizmetler"), "pulse:services", null, iconKey = "services"),
    LaunchApp(tr("Startup apps", "Başlangıç uygulamaları"), "pulse:startup", null, iconKey = "startup"),
    LaunchApp(tr("Programs", "Programlar"), "pulse:apps", null, iconKey = "apps"),
    LaunchApp(tr("Games", "Oyunlar"), "pulse:games", null, iconKey = "gridView"),
    LaunchApp(tr("Minesweeper", "Mayın Tarlası"), "pulse:mines", null, iconKey = "gridView"),
    LaunchApp(tr("Solitaire", "Solitaire"), "pulse:solitaire", null, iconKey = "heart"),
    LaunchApp(tr("FreeCell", "FreeCell"), "pulse:freecell", null, iconKey = "heart"),
    // Pulse's own apps, off until they are switched on in the launcher settings
    LaunchApp(tr("Music player", "Müzik çalar"), "pulse:x:music", null, iconKey = "music"),
    LaunchApp(tr("Video", "Video"), "pulse:x:video", null, iconKey = "video"),
    LaunchApp(tr("Notes", "Notlar"), "pulse:x:notes", null, iconKey = "notes"),
    LaunchApp(tr("Weather", "Hava durumu"), "pulse:x:weather", null, iconKey = "weather"),
    LaunchApp(tr("Calculator", "Hesap makinesi"), "pulse:x:calculator", null, iconKey = "calc"),
    LaunchApp(tr("Voice recorder", "Ses kaydedici"), "pulse:x:voice", null, iconKey = "voice"),
    LaunchApp(tr("White noise", "Beyaz gürültü"), "pulse:x:whitenoise", null, iconKey = "volume"),
    LaunchApp(tr("Screen recorder", "Ekran kaydı"), "pulse:x:screenrec", null, iconKey = "record"),
    LaunchApp(tr("Notification history", "Bildirim geçmişi"), "pulse:x:notifhist", null, iconKey = "bell"),
    LaunchApp(tr("Messages", "Mesajlar"), "pulse:x:messages", null, iconKey = "chat"),
    LaunchApp(tr("Toolkit", "Araç kutusu"), "pulse:x:toolkit", null, iconKey = "settings"),
)

/**
 * The desktop: icons run down the left edge and start a new column when the screen is full, like a real Windows desktop. A long press picks an icon up and it
 * snaps to the grid where it is put down (unless the layout is locked); the places are kept per shortcut.
 */
@Composable
private fun DesktopIcons(look: LauncherLook, state: LauncherState, items: List<LaunchApp>, iconDp: Int, edge: androidx.compose.ui.unit.Dp, open: (LaunchApp) -> Unit) {
    val ctx = LocalContext.current
    val d = LocalDensity.current.density
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp)) {
        val cellW = 92f; val cellH = (iconDp + 48).toFloat()
        val perColumn = (maxHeight.value / cellH).toInt().coerceAtLeast(1)
        val cols = (maxWidth.value / cellW).toInt().coerceAtLeast(1)
        // the cell (column, row) of each shortcut: the one it was put down on, else the next free place down the left edge
        val cells = HashMap<String, Pair<Int, Int>>()
        items.forEach { a -> LauncherPrefs.iconPos[a.pkg]?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { cells[a.pkg] = it[0] to it[1] } }
        var next = 0
        items.forEach { a ->
            if (a.pkg !in cells) {
                while (cells.values.contains((next / perColumn) to (next % perColumn))) next++
                cells[a.pkg] = (next / perColumn) to (next % perColumn)
            }
        }
        items.forEach { app ->
            val (col, row) = cells.getValue(app.pkg)
            var drag by remember(app.pkg) { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
            val x = drag?.x ?: (col * cellW); val y = drag?.y ?: (row * cellH)
            Box(
                Modifier.offset(x.dp, y.dp).width(88.dp).zIndex(if (drag != null) 2f else 0f)
                    .pointerInput(app.pkg, LauncherPrefs.lockLayout) {
                        if (!LauncherPrefs.lockLayout) detectDragGesturesAfterLongPress(
                            onDragStart = { drag = androidx.compose.ui.geometry.Offset(col * cellW, row * cellH) },
                            onDrag = { c, dr -> c.consume(); drag = drag?.let { it + androidx.compose.ui.geometry.Offset(dr.x / d, dr.y / d) } },
                            onDragEnd = {
                                val p = drag; drag = null
                                if (p != null) {
                                    var nc = Math.round(p.x / cellW).coerceIn(0, cols - 1); var nr = Math.round(p.y / cellH).coerceIn(0, perColumn - 1)
                                    // a taken cell: the nearest free one down that column, then the next columns
                                    val taken = cells.filterKeys { it != app.pkg }.values.toSet()
                                    var guard = 0
                                    while ((nc to nr) in taken && guard++ < cols * perColumn) { nr++; if (nr >= perColumn) { nr = 0; nc = (nc + 1) % cols } }
                                    LauncherPrefs.iconPos = LauncherPrefs.iconPos + (app.pkg to "$nc,$nr"); LauncherPrefs.save(ctx)
                                }
                            },
                            onDragCancel = { drag = null },
                        )
                    },
            ) { look.IconCell(app, iconDp, Modifier.fillMaxWidth().appTap(state, app) { open(app) }) }
        }
    }
}

/**
 * Starts an app, in a window when asked: a window in the middle of the screen, four fifths wide and seven tenths high.
 * That needs the system's freeform windows (developer option or desktop mode); without them the bounds are ignored and the app opens full screen.
 */
private fun startApp(ctx: Context, intent: Intent, windowed: Boolean) {
    // the app comes up from slightly smaller and the home screen fades back, like a phone launcher
    if (!windowed) {
        val anim = runCatching { android.app.ActivityOptions.makeCustomAnimation(ctx, com.umutk.pulse.R.anim.launcher_open, com.umutk.pulse.R.anim.launcher_hold).toBundle() }.getOrNull()
        runCatching { ctx.startActivity(intent, anim) }.onFailure { ctx.startActivity(intent) }
        return
    }
    val dm = ctx.resources.displayMetrics
    val w = (dm.widthPixels * .8f).toInt(); val h = (dm.heightPixels * .7f).toInt()
    val left = (dm.widthPixels - w) / 2; val top = (dm.heightPixels - h) / 3
    if (android.os.Build.VERSION.SDK_INT < 24) { ctx.startActivity(intent); return }
    val options = android.app.ActivityOptions.makeBasic().setLaunchBounds(android.graphics.Rect(left, top, left + w, top + h))
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle()) }.onFailure { ctx.startActivity(intent) }
}

/** What a desktop shortcut opens: Pulse's own pages and apps. */
private fun openDesktopItem(ctx: Context, id: String, lookId: String = "") {
    LauncherSounds.click(lookId)
    // Windows looks show Pulse's own pages in a window of that Windows
    if (id.startsWith("pulse:x:")) {
        val route = id.removePrefix("pulse:x:")
        val title = desktopItems().firstOrNull { it.pkg == id }?.label ?: route
        if (lookId in WINDOWS_LOOKS) DesktopWindows.open(route, title)
        else runCatching { ctx.startActivity(Intent(ctx, ExtraToolActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return
    }
    if (lookId in WINDOWS_LOOKS) {
        val r = when (id) { "pulse:pc" -> "page:device" to tr("This PC", "Bu bilgisayar"); "pulse:recycle" -> "page:recycle" to tr("Recycle Bin", "Geri Dönüşüm Kutusu")
            "pulse:paint" -> "paint" to "Paint"; "pulse:taskmgr" -> "page:processes" to tr("Task Manager", "Görev Yöneticisi"); "pulse:cmd" -> "page:terminal" to tr("Command Prompt", "Komut İstemi")
            "pulse:settings" -> "controlpanel" to tr("Control Panel", "Denetim Masası"); "pulse:winsettings" -> "winsettings" to tr("Settings", "Ayarlar"); "pulse:perf" -> "page:performance" to tr("Performance", "Performans")
            "pulse:services" -> "page:services" to tr("Services", "Hizmetler"); "pulse:startup" -> "page:startup" to tr("Startup apps", "Başlangıç uygulamaları")
            "pulse:apps" -> "page:apps" to tr("Programs", "Programlar"); "pulse:files" -> "page:filestabs" to tr("File Explorer", "Dosya Gezgini")
            "pulse:mines" -> "game:minesweeper" to tr("Minesweeper", "Mayın Tarlası"); "pulse:solitaire" -> "game:solitaire" to tr("Solitaire", "Solitaire")
            "pulse:store" -> "store:launcher" to tr("Store", "Mağaza"); "pulse:control" -> "controlcenter" to tr("Control Center", "Denetim Merkezi")
            "pulse:freecell" -> "game:freecell" to tr("FreeCell", "FreeCell"); "pulse:games" -> "game:hub" to tr("Games", "Oyunlar"); else -> null }
        if (r != null) {
            // a window of the desktop itself: dragged, several at once, the launcher stays behind
            DesktopWindows.open(r.first, r.second)
            return
        }
    }
    val i = when (id) {
        "pulse:pc" -> Intent(ctx, MainActivity::class.java).putExtra("start", "device")
        "pulse:recycle" -> Intent(ctx, MainActivity::class.java).putExtra("start", "recycle")
        "pulse:files" -> Intent(ctx, FilesActivity::class.java)
        "pulse:paint" -> Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "paint")
        "pulse:taskmgr" -> Intent(ctx, MainActivity::class.java).putExtra("start", "processes")
        "pulse:settings" -> Intent(ctx, MainActivity::class.java).putExtra("start", "settings")
        "pulse:perf" -> Intent(ctx, MainActivity::class.java).putExtra("start", "performance")
        "pulse:services" -> Intent(ctx, MainActivity::class.java).putExtra("start", "services")
        "pulse:startup" -> Intent(ctx, MainActivity::class.java).putExtra("start", "startup")
        "pulse:apps" -> Intent(ctx, MainActivity::class.java).putExtra("start", "apps")
        "pulse:mines" -> Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "game:minesweeper")
        "pulse:solitaire" -> Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "game:solitaire")
        "pulse:freecell" -> Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "game:freecell")
        "pulse:games" -> Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "game:hub")
        else -> Intent(ctx, MainActivity::class.java).putExtra("start", "terminal")
    }
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Every launchable app except Pulse itself, one entry per package, sorted by label. */
internal fun loadApps(ctx: Context): List<LaunchApp> {
    val pm = ctx.packageManager
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pack = LauncherPrefs.iconPack
    val packMap = if (pack.isNotBlank()) IconPacks.filter(ctx, pack) else emptyMap()
    return pm.queryIntentActivities(main, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            LaunchApp(ri.loadLabel(pm).toString(), pkg,
                runCatching { (IconPacks.icon(ctx, pack, packMap, pkg, ri.activityInfo.name) ?: ri.loadIcon(pm)).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
                installed = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L),
                system = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                category = ri.activityInfo.applicationInfo.category)
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun LauncherRoot(look: LauncherLook, apps: List<LaunchApp>) {
    val ctx = LocalContext.current
    val edge = LocalLauncherEdge.current
    LaunchedEffect(Unit) { while (true) { LauncherBadges.read(ctx); delay(2500) } }
    val vertical = LauncherPrefs.vertical
    // turned sideways, the same few columns would sit far apart: the columns follow the width (about one per 100 dp)
    val screenCfg = androidx.compose.ui.platform.LocalConfiguration.current
    val columns = if (screenCfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) maxOf(LauncherPrefs.columns.coerceIn(2, 8), (screenCfg.screenWidthDp / 100).coerceIn(4, 10)) else LauncherPrefs.columns.coerceIn(2, 8)
    val iconDp = LauncherPrefs.iconDp.coerceIn(24, 64)
    val hidden = LauncherPrefs.hidden
    val sort = LauncherPrefs.sort
    val usage = LauncherPrefs.usage
    val visible = remember(apps, hidden, sort, usage) {
        val list = apps.filter { it.pkg !in hidden }
        when (sort) {
            "installed" -> list.sortedWith(compareByDescending<LaunchApp> { it.installed }.thenBy { it.label.lowercase() })
            "opened" -> list.sortedWith(compareByDescending<LaunchApp> { usage[it.pkg] ?: 0L }.thenBy { it.label.lowercase() })
            else -> list
        }
    }
    val autoDock = remember(visible) { visible.take(3).map { it.pkg } }
    val dock = dockApps(visible, LauncherPrefs.dock)
    var query by remember { mutableStateOf("") }
    LaunchedEffect(LauncherSession.startOpen) { if (!LauncherSession.startOpen) query = "" }
    // "keep the screen on at home"
    val hostActivity = LocalContext.current as? android.app.Activity
    androidx.compose.runtime.SideEffect { hostActivity?.window?.let { w -> if (LauncherPrefs.keepOn) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    val folderRows = LauncherPrefs.folders
    val drawerBase = remember(visible, folderRows) { LauncherFolders.fold(visible) }
    val drawer = remember(drawerBase, visible, query) {
        if (query.isBlank()) drawerBase else visible.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    var actionFor by remember { mutableStateOf<LaunchApp?>(null) }
    val open: (LaunchApp) -> Unit = { app ->
        // the look's click when anything opens (the desktop shortcuts played it already)
        if (!app.pkg.startsWith("pulse:")) { LauncherSounds.prepare(ctx, look.id); LauncherSounds.click(look.id) }
        if (isFolder(app)) FolderUi.open = app.pkg.removePrefix(FOLDER_PREFIX)
        else if (app.pkg.startsWith("file:")) openLink(ctx, app.pkg.removePrefix("file:"))
        else if (app.pkg.startsWith("pulse:")) openDesktopItem(ctx, app.pkg, look.id)
        else ctx.packageManager.getLaunchIntentForPackage(app.pkg)?.let { startApp(ctx, it, LauncherPrefs.windowed && look.id in WINDOWS_LOOKS); LauncherPrefs.touch(ctx, app.pkg) }
    }
    // what was put on the home screen by hand: files, pictures, folders, apps
    val linkItems = remember(LauncherPrefs.links, apps) { LauncherPrefs.links.mapNotNull { linkApp(it, apps) } }
    val state = LauncherState(drawer, vertical, LauncherPrefs.drawerColumns.coerceIn(2, 8), LauncherPrefs.drawerIconDp.takeIf { it > 0 }?.coerceIn(24, 72) ?: iconDp, dock, open) {
        // a file, picture or folder put on the home screen: a long press takes it off again (the file itself stays)
        if (it.pkg.startsWith("file:")) { LauncherPrefs.links = LauncherPrefs.links - it.pkg; LauncherPrefs.save(ctx); android.widget.Toast.makeText(ctx, tr("Removed from the home screen", "Ana ekrandan kaldırıldı"), android.widget.Toast.LENGTH_SHORT).show() }
        else if (!it.pkg.startsWith("pulse:")) actionFor = it
    }

    // the home screen is a desktop, not an app grid: the Windows looks show the desktop shortcuts, the others show no icons (the apps list holds every app); a setting brings the app grid back
    val desktop = look.id in WINDOWS_LOOKS
    // Windows 10 / 11 have the Settings app, the older ones the Control Panel: each look shows its own of the two
    val newWin = look.id in setOf("win11", "win10", "win10black")
    val shortcuts = remember(desktop, LauncherPrefs.desktopOn, newWin) { if (desktop) desktopItems().filter { it.pkg in LauncherPrefs.desktopOn && !(newWin && it.pkg == "pulse:settings") && !(!newWin && it.pkg == "pulse:winsettings") } else emptyList() }
    val home = if (LauncherPrefs.homeApps || look.id.startsWith("winphone")) (visible + linkItems).distinctBy { it.pkg } else if (desktop) shortcuts + linkItems else linkItems
    val grid = rememberLazyGridState()
    val pages = home.chunked(columns * PAGE_ROWS)
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    LaunchedEffect(LauncherSession.homeTick) {
        if (LauncherSession.homeTick > 0) { grid.scrollToItem(0); pager.scrollToPage(0) }
    }

    look.Background(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            look.SideDock(state, Modifier.fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (LauncherPrefs.clock) ClockHeader(Modifier.fillMaxWidth())
            if (!(desktop && !LauncherPrefs.homeApps)) LauncherWidgetStrip(Modifier.fillMaxWidth())
            if (look.taskbarTop && LauncherPrefs.dockShown) look.Taskbar(state, Modifier.fillMaxWidth().height(look.taskbarHeight))
            // a long press on an empty part of the home opens the Settings / Widgets sheet
            Box(Modifier.weight(1f).fillMaxWidth().homeLongPress { HomeSheetState.sheet = true }) {
                if (desktop && !LauncherPrefs.homeApps) {
                    LabelScale { DesktopIcons(look, state, shortcuts + linkItems, iconDp, edge, open) }
                    // the clock gadget of the desktop, in the look's own style, top right
                    MovableDesktopClock(look.id)
                    DesktopWidgetLayer()
                }
                else if (look.id == "winphone" && LauncherPrefs.phoneTiles) WinPhoneTiles(state, visible, edge)
                else if (vertical) {
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp), state = grid) {
                        items(home, key = { it.pkg }) { app ->
                            look.IconCell(app, iconDp, Modifier.fillMaxWidth().appTap(state, app) { open(app) })
                        }
                    }
                } else if (LauncherPrefs.homeApps || look.id.startsWith("winphone") || linkItems.isNotEmpty()) LabelScale { HomeGrid(look, state, (if (LauncherPrefs.homeApps || look.id.startsWith("winphone")) visible + linkItems else linkItems).distinctBy { it.pkg }, columns, iconDp, edge, open) }
            }
            if (!look.taskbarTop && LauncherPrefs.dockShown) look.Taskbar(state, Modifier.fillMaxWidth().height(look.taskbarHeight))
        }
        }
        // settings and (with the dock hidden) the apps list stay reachable from the home screen
        if (!LauncherSession.startOpen && (LauncherPrefs.topChips || !LauncherPrefs.dockShown)) Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp), contentAlignment = Alignment.TopEnd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!LauncherPrefs.dockShown) { HomeChip("apps") { LauncherSession.startOpen = true }; Spacer(Modifier.width(8.dp)) }
                if (LauncherPrefs.topChips && !desktop) { HomeChip("processes") { openDesktopItem(ctx, "pulse:taskmgr", look.id) }; Spacer(Modifier.width(8.dp)) }
                if (LauncherPrefs.topChips) HomeChip("settings") { ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)) }
            }
        }
        if (desktop) Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            DesktopWindowLayer(look.id, if (LauncherPrefs.dockShown && !look.taskbarTop) look.taskbarHeight else 0.dp)
        }
        // the Office Assistant keeps the old Windows desktops company, at the bottom right above the taskbar
        if (desktop && LauncherPrefs.clippy && look.id in setOf("win95", "xp")) {
            LaunchedEffect(Unit) { com.umutk.pulse.ClippyPrefs.load(ctx) }
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                com.umutk.pulse.ClippyView(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = (if (LauncherPrefs.dockShown && !look.taskbarTop) look.taskbarHeight else 0.dp) + 6.dp))
            }
        }
        if (desktop) ClockFlyout(look.id, if (LauncherPrefs.dockShown && !look.taskbarTop) look.taskbarHeight else 0.dp)
        // Windows 10 Mobile looks: the action center comes down from the top edge
        if (look.id.startsWith("winphone")) Box(Modifier.fillMaxSize()) {
            ActionCenterHandle(Modifier.align(Alignment.TopStart))
            ActionCenterPanel()
        }
        // Windows looks: the menu sits above the taskbar, which is inside the navigation-bar inset
        if (LauncherSession.startOpen && look.ownSearch) look.StartMenu(state, { LauncherSession.startOpen = false }, Modifier.fillMaxSize().navigationBarsPadding())
        else if (LauncherSession.startOpen) Column(Modifier.fillMaxSize().background(T.bg)) {
            Box(Modifier.statusBarsPadding()) { DrawerSearch(query) { query = it } }
            Box(Modifier.weight(1f).fillMaxWidth()) { look.StartMenu(state, { LauncherSession.startOpen = false }, Modifier.fillMaxSize()) }
        }
        // the Windows Phone glance page; "all apps" on it opens the apps list
        if (LauncherSession.feedOpen && look.id.startsWith("winphone")) WinPhoneFeed({ LauncherSession.feedOpen = false; LauncherSession.startOpen = true }) { LauncherSession.feedOpen = false }
        HomeSheet()
        FolderDialogs(visible, open) { actionFor = it }
        if (LauncherSession.searchOpen) QuickSearch(state) { LauncherSession.searchOpen = false }
        actionFor?.let { app -> AppActionDialog(app, autoDock) { actionFor = null } }
    }
}


/** The icon label size of the home screen (Settings > Home screen): only the text scale changes inside. */
@Composable
internal fun LabelScale(content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val k = LauncherPrefs.labelScale / 100f
    if (k == 1f) content()
    else androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * k)) { content() }
}


/** A thing put on the home screen: its name, and the picture of the look's own icon set (a folder, a picture, a document) or the app itself. */
internal fun linkApp(link: String, apps: List<LaunchApp>): LaunchApp? {
    if (link.startsWith("app:")) return apps.firstOrNull { it.pkg == link.removePrefix("app:") }
    val path = link.removePrefix("file:")
    val f = java.io.File(path)
    if (!f.exists()) return null
    val key = if (f.isDirectory) "folder" else if (f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")) "image" else "document"
    return LaunchApp(f.name.ifEmpty { path }, link, null, iconKey = key)
}

/** Opens a file with whatever handles it, a picture in the gallery, a folder in Pulse Files. */
internal fun openLink(ctx: Context, path: String) {
    val f = java.io.File(path)
    if (f.isDirectory) {
        com.umutk.pulse.features.ExNav.lastHere = f.path
        runCatching { ctx.startActivity(Intent().setClassName(ctx, "com.umutk.pulse.FilesActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    } else com.umutk.pulse.files.home.openFileWithSystem(ctx, f)
}
