package com.umutk.pulse.extras.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.Look
import com.umutk.pulse.Themes
import java.io.File
import java.util.Properties

/**
 * The launcher's own settings. Never written to Pulse's Cfg / Opt.
 * Kept in a plain file, not SharedPreferences: the launcher runs in its own process and keeps its SharedPreferences
 * cached, so a change saved from Pulse's process would never reach it (the look "did not change").
 */
object LauncherPrefs {
    var look by mutableStateOf("material")
    var vertical by mutableStateOf(false)
    var columns by mutableStateOf(5)
    var iconDp by mutableStateOf(48)
    /** Package names pinned to the dock, max 5. Empty = automatic (first 3 apps). */
    var dock by mutableStateOf<List<String>>(emptyList())
    var dockShown by mutableStateOf(true)
    var drawerColumns by mutableStateOf(5)
    /** "name" (A-Z), "installed" (newest first) or "opened" (last opened first; the only mode that records launches). */
    var sort by mutableStateOf("name")
    var hidden by mutableStateOf<Set<String>>(emptySet())
    var clock by mutableStateOf(false)
    /** The clock widget on the Windows desktops (on at first; the cross on it turns it off). */
    var desktopClock by mutableStateOf(true)
    /** The Windows 7 style notes gadget on the desktop (off at first). */
    var desktopNote by mutableStateOf(false)
    /** Off (the default): the home screen shows no app grid. Windows looks show the desktop shortcuts, the other looks the clock, widgets and dock; the apps are in the apps list. */
    var homeApps by mutableStateOf(false)
    /** Apps open in a window of their own on the Windows looks (the system's freeform windows); without that mode they open full screen as usual. */
    var windowed by mutableStateOf(false)
    var lockPortrait by mutableStateOf(false)
    var fullScreen by mutableStateOf(false)
    /** Package -> time of the last launch; only filled while [sort] is "opened". */
    var usage by mutableStateOf<Map<String, Long>>(emptyMap())
    /** Wallpaper picked per look: index into Pulse's stock pictures for that look (see [LauncherWallpaper]). */
    var walls by mutableStateOf<Map<String, Int>>(emptyMap())
    /** Widgets on the home screen, one "id|package|class" each (see LauncherWidgets). */
    var widgets by mutableStateOf<List<String>>(emptyList())
    private const val WALL = "wall."
    val DESKTOP_DEFAULT = setOf("pulse:pc", "pulse:recycle", "pulse:files", "pulse:settings", "pulse:winsettings", "pulse:store")

    /** The desktop shortcuts that show on the Windows desktops: This PC, Recycle Bin and File Explorer until others are switched on in the settings. */
    /** The round buttons at the top right of the home screen (apps, task manager, settings); off by default: the settings are in the long press menu. */
    var topChips by mutableStateOf(false)
    /** Windows Phone looks: true = coloured live tiles, false (default) = plain icons with the name under them. */
    var phoneTiles by mutableStateOf(true)
    // ---- more things to set (Launcher settings > Extras) ----
    /** Double tap on an empty part of the home locks the screen. */
    var gestureLock by mutableStateOf(false)
    /** Swipe down on the home opens the notification shade. */
    var swipeDownShade by mutableStateOf(false)
    /** Swipe up on the home opens the apps list. */
    var swipeUpApps by mutableStateOf(false)
    /** Shape of the app pictures: squircle (default), circle, rounded, square. */
    var iconShape by mutableStateOf("squircle")
    /** Wallpaper: darkening 0..70 percent, blur 0..24 dp, and a different stock picture each day. */
    var wallDim by mutableStateOf(0)
    var wallBlur by mutableStateOf(0)
    /** Sound effects: the look's start-up chime and a click when something opens. */
    var sounds by mutableStateOf(false)
    var wallDaily by mutableStateOf(false)
    /** The screen stays on while the home is shown. */
    var keepOn by mutableStateOf(false)
    /** A short tick on a long press. */
    var haptic by mutableStateOf(true)
    /** The apps list opens with the keyboard on its search field. */
    var searchFocus by mutableStateOf(false)
    /** The taskbar clock: 24-hour or 12-hour, and the date as dmy / mdy / ymd. */
    var clock24 by mutableStateOf(true)
    var dateFmt by mutableStateOf("dmy")
    /** The desktop clock card shows seconds. */
    var clockSeconds by mutableStateOf(false)
    /** The desktop gadgets (clock, notes, widgets) cannot be moved or removed by accident. */
    var lockLayout by mutableStateOf(false)
    /** Where each widget sits on the Windows desktops: "x,y,w,h" in dp, by widget id. */
    var widgetPos by mutableStateOf<Map<Int, String>>(emptyMap())
    var desktopOn by mutableStateOf(DESKTOP_DEFAULT)
    // ---- the home screen in the way Nova Launcher sets it up ----
    /** Rows of the home grid (the columns are [columns]). */
    var rows by mutableStateOf(4)
    /** How the pages move: "simple", "cube", "zoom", "stack" or "flip". */
    var pageEffect by mutableStateOf("simple")
    /** The dots under the home pages. */
    var pageDots by mutableStateOf(true)
    /** Icons the dock can hold, 3..7. */
    var dockCount by mutableStateOf(5)
    /** A folder's picture: "grid" (2x2), "line" (three in a row), "stack" (piled up). */
    var folderStyle by mutableStateOf("grid")
    /** Badges: "number" (the count) or "dot" (a plain dot). */
    var badgeStyle by mutableStateOf("number")
    /** The names under the home icons and folders. */
    var labels by mutableStateOf(true)
    /** The Office Assistant (Clippy) sits at the bottom right of the Windows 95 and XP desktops. */
    var clippy by mutableStateOf(true)
    /** The Windows looks show their boot screen and/or sign-in screen when the launcher starts. */
    var bootScreen by mutableStateOf(false)
    var loginScreen by mutableStateOf(false)
    /** Swipe down on the home opens the quick search (when the shade is not set for it). */
    var swipeDownSearch by mutableStateOf(true)
    /** Notification counts on the app pictures. */
    var badges by mutableStateOf(true)
    /** The package of the installed icon pack that draws the app pictures; empty = the apps' own. */
    var iconPack by mutableStateOf("")
    /** The order of the home grid: "app:<package>" and "f:<folder id>" entries; apps not listed follow in the usual order. */
    var homeOrder by mutableStateOf<List<String>>(emptyList())
    /** Folders of the home grid: id -> "name|package,package". */
    var folders by mutableStateOf<Map<String, String>>(emptyMap())
    /** Windows Phone tiles: the size an app was given ("s", "m" or "w"), and the layout for the rest ("auto", "medium", "small"). */
    var tileSizes by mutableStateOf<Map<String, String>>(emptyMap())
    var tileMode by mutableStateOf("auto")
    /** Where each desktop shortcut sits on the Windows desktops: "x,y" in dp, by shortcut id. */
    var iconPos by mutableStateOf<Map<String, String>>(emptyMap())

    fun wallIndex(look: String): Int = walls[look] ?: 0
    fun setWallIndex(look: String, i: Int) { walls = walls + (look to i) }

    private const val FILE = "launcher.properties"
    private const val USE = "use."

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun list(s: String?) = s.orEmpty().split(',').filter { it.isNotBlank() }

    fun load(c: Context) {
        val p = Properties()
        // the file is read on every load: nothing is cached between processes
        val f = file(c)
        if (f.exists()) runCatching { f.inputStream().use { p.load(it) } }
        // first start after the change: keep the settings saved in the old SharedPreferences file
        else c.getSharedPreferences("launcher", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v != null) p.setProperty(k, v.toString()) }
        look = p.getProperty("look") ?: "material"
        vertical = p.getProperty("vertical") == "true"
        columns = p.getProperty("columns")?.toIntOrNull() ?: 5
        iconDp = p.getProperty("iconDp")?.toIntOrNull() ?: 48
        dock = list(p.getProperty("dock")).take(7)
        dockShown = p.getProperty("dockShown") != "false"
        drawerColumns = p.getProperty("drawerColumns")?.toIntOrNull() ?: 5
        sort = p.getProperty("sort") ?: "name"
        hidden = list(p.getProperty("hidden")).toSet()
        topChips = p.getProperty("topChips") == "true"
        // "phoneTilesOn": the tiles are on unless switched off; an old "phoneTiles=false" (written while the tiles were off by default) does not count
        phoneTiles = p.getProperty("phoneTilesOn") != "false"
        tileMode = p.getProperty("tileMode") ?: "auto"
        tileSizes = p.stringPropertyNames().filter { it.startsWith("tile.") }.associate { it.removePrefix("tile.") to p.getProperty(it) }
        rows = p.getProperty("rows")?.toIntOrNull()?.coerceIn(3, 8) ?: 4; pageEffect = p.getProperty("pageEffect") ?: "simple"; pageDots = p.getProperty("pageDots") != "false"
        dockCount = p.getProperty("dockCount")?.toIntOrNull()?.coerceIn(3, 7) ?: 5; folderStyle = p.getProperty("folderStyle") ?: "grid"; badgeStyle = p.getProperty("badgeStyle") ?: "number"; labels = p.getProperty("labels") != "false"
        clippy = p.getProperty("clippy") != "false"; bootScreen = p.getProperty("bootScreen") == "true"; loginScreen = p.getProperty("loginScreen") == "true"; swipeDownSearch = p.getProperty("swipeDownSearch") != "false"; badges = p.getProperty("badges") != "false"; iconPack = p.getProperty("iconPack").orEmpty()
        homeOrder = p.getProperty("homeOrder").orEmpty().split(',').filter { it.isNotBlank() }
        folders = p.stringPropertyNames().filter { it.startsWith("folder.") }.associate { it.removePrefix("folder.") to p.getProperty(it) }
        iconPos = p.stringPropertyNames().filter { it.startsWith("ipos.") }.associate { it.removePrefix("ipos.") to p.getProperty(it) }
        gestureLock = p.getProperty("gestureLock") == "true"; swipeDownShade = p.getProperty("swipeDownShade") == "true"; swipeUpApps = p.getProperty("swipeUpApps") == "true"
        iconShape = p.getProperty("iconShape") ?: "squircle"; sounds = p.getProperty("sounds") == "true"; wallDim = p.getProperty("wallDim")?.toIntOrNull() ?: 0; wallBlur = p.getProperty("wallBlur")?.toIntOrNull() ?: 0
        wallDaily = p.getProperty("wallDaily") == "true"; keepOn = p.getProperty("keepOn") == "true"; haptic = p.getProperty("haptic") != "false"; searchFocus = p.getProperty("searchFocus") == "true"
        clock24 = p.getProperty("clock24") != "false"; dateFmt = p.getProperty("dateFmt") ?: "dmy"; clockSeconds = p.getProperty("clockSeconds") == "true"; lockLayout = p.getProperty("lockLayout") == "true"
        desktopOn = p.getProperty("desktop")?.let { list(it).toSet() } ?: DESKTOP_DEFAULT
        // once: the settings (or the Control Panel) and the Store join the desktop of a launcher set up before they existed
        if (p.getProperty("desktopV2") == null) desktopOn = desktopOn + setOf("pulse:settings", "pulse:winsettings", "pulse:store")
        clock = p.getProperty("clock") == "true"
        desktopClock = p.getProperty("desktopClock") != "false"
        desktopNote = p.getProperty("desktopNote") == "true"
        homeApps = p.getProperty("homeApps") == "true"
        windowed = p.getProperty("windowed") == "true"
        lockPortrait = p.getProperty("lockPortrait") == "true"
        fullScreen = p.getProperty("fullScreen") == "true"
        usage = p.stringPropertyNames().filter { it.startsWith(USE) }
            .associate { it.removePrefix(USE) to (p.getProperty(it).toLongOrNull() ?: 0L) }
        widgets = p.getProperty("widgets").orEmpty().split(';').filter { it.isNotBlank() }
        widgetPos = p.stringPropertyNames().filter { it.startsWith("wpos.") }.mapNotNull { k -> k.removePrefix("wpos.").toIntOrNull()?.let { it to p.getProperty(k) } }.toMap()
        walls = p.stringPropertyNames().filter { it.startsWith(WALL) }
            .associate { it.removePrefix(WALL) to (p.getProperty(it).toIntOrNull() ?: 0) }
    }

    fun save(c: Context) {
        val p = Properties()
        p.setProperty("look", look); p.setProperty("vertical", vertical.toString())
        p.setProperty("columns", columns.toString()); p.setProperty("iconDp", iconDp.toString())
        p.setProperty("dock", dock.joinToString(",")); p.setProperty("dockShown", dockShown.toString())
        p.setProperty("drawerColumns", drawerColumns.toString()); p.setProperty("sort", sort)
        p.setProperty("gestureLock", gestureLock.toString()); p.setProperty("swipeDownShade", swipeDownShade.toString()); p.setProperty("swipeUpApps", swipeUpApps.toString())
        p.setProperty("iconShape", iconShape); p.setProperty("wallDim", wallDim.toString()); p.setProperty("sounds", sounds.toString()); p.setProperty("wallBlur", wallBlur.toString()); p.setProperty("wallDaily", wallDaily.toString())
        p.setProperty("keepOn", keepOn.toString()); p.setProperty("haptic", haptic.toString()); p.setProperty("searchFocus", searchFocus.toString())
        p.setProperty("clock24", clock24.toString()); p.setProperty("dateFmt", dateFmt); p.setProperty("clockSeconds", clockSeconds.toString()); p.setProperty("lockLayout", lockLayout.toString())
        p.setProperty("phoneTilesOn", phoneTiles.toString()); p.setProperty("tileMode", tileMode); tileSizes.forEach { (k, v) -> p.setProperty("tile.$k", v) }; p.setProperty("topChips", topChips.toString()); p.setProperty("desktop", desktopOn.joinToString(",")); p.setProperty("desktopV2", "1"); p.setProperty("hidden", hidden.joinToString(",")); p.setProperty("clock", clock.toString()); p.setProperty("desktopClock", desktopClock.toString()); p.setProperty("desktopNote", desktopNote.toString()); p.setProperty("homeApps", homeApps.toString()); p.setProperty("windowed", windowed.toString())
        p.setProperty("lockPortrait", lockPortrait.toString()); p.setProperty("fullScreen", fullScreen.toString())
        p.setProperty("rows", rows.toString()); p.setProperty("pageEffect", pageEffect); p.setProperty("pageDots", pageDots.toString()); p.setProperty("dockCount", dockCount.toString())
        p.setProperty("folderStyle", folderStyle); p.setProperty("badgeStyle", badgeStyle); p.setProperty("labels", labels.toString())
        p.setProperty("clippy", clippy.toString()); p.setProperty("bootScreen", bootScreen.toString()); p.setProperty("loginScreen", loginScreen.toString()); p.setProperty("swipeDownSearch", swipeDownSearch.toString()); p.setProperty("badges", badges.toString()); p.setProperty("iconPack", iconPack)
        p.setProperty("homeOrder", homeOrder.joinToString(","))
        folders.forEach { (id, v) -> p.setProperty("folder.$id", v) }
        iconPos.forEach { (id, v) -> p.setProperty("ipos.$id", v) }
        usage.forEach { (pkg, t) -> p.setProperty(USE + pkg, t.toString()) }
        widgetPos.forEach { (id, v) -> p.setProperty("wpos.$id", v) }
        walls.forEach { (lookId, i) -> p.setProperty(WALL + lookId, i.toString()) }
        p.setProperty("widgets", widgets.joinToString(";"))
        val f = file(c); val tmp = File(f.path + ".tmp")
        runCatching { tmp.outputStream().use { p.store(it, null) }; tmp.renameTo(f) }
    }

    /** Records a launch (the quick search shows the apps opened last, and the sort by last opened uses it). Stays on the phone, in the launcher's own file. */
    fun touch(c: Context, pkg: String) {
        usage = usage + (pkg to System.currentTimeMillis())
        save(c)
    }

    /** Back to the first-run settings. The look goes too: the Pulse look is restored by the next start. */
    fun reset(c: Context) {
        look = "material"; vertical = false; columns = 5; iconDp = 48; dock = emptyList(); dockShown = true
        drawerColumns = 5; sort = "name"; hidden = emptySet(); desktopOn = DESKTOP_DEFAULT; topChips = false; phoneTiles = true; rows = 4; pageEffect = "simple"; pageDots = true; dockCount = 5; folderStyle = "grid"; badgeStyle = "number"; labels = true; tileMode = "auto"; tileSizes = emptyMap(); swipeDownSearch = true; badges = true; iconPack = ""; homeOrder = emptyList(); folders = emptyMap(); iconPos = emptyMap(); clock = false; desktopClock = true; desktopNote = false; homeApps = false; windowed = false; lockPortrait = false; fullScreen = false
        gestureLock = false; swipeDownShade = false; swipeUpApps = false; iconShape = "squircle"; wallDim = 0; wallBlur = 0; sounds = false; wallDaily = false; keepOn = false; haptic = true; searchFocus = false
        clock24 = true; dateFmt = "dmy"; clockSeconds = false; lockLayout = false
        usage = emptyMap(); walls = emptyMap()
        save(c)
    }

    /** Copies the launcher's settings file to Download/PulseLauncher.properties (the other way: [importSettings]). */
    fun exportSettings(c: Context): Boolean = runCatching {
        save(c)
        val out = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PulseLauncher.properties")
        file(c).copyTo(out, overwrite = true); true
    }.getOrDefault(false)

    fun importSettings(c: Context): Boolean = runCatching {
        val src = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PulseLauncher.properties")
        if (!src.isFile) return false
        src.copyTo(file(c), overwrite = true); load(c); true
    }.getOrDefault(false)

    /** Puts the launcher's look on the shared Look before the first frame (AppLookGuard.leave restores Pulse's). */
    fun apply() {
        Look.theme = Themes.firstOrNull { it.id == look } ?: Themes.first { it.id == "material" }
        // the Windows 11 desktop is dark (Mica), so the pages inside its windows are dark too
        if (look == "win11") Look.mode = "dark"
    }
}
