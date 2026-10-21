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
        dock = list(p.getProperty("dock")).take(5)
        dockShown = p.getProperty("dockShown") != "false"
        drawerColumns = p.getProperty("drawerColumns")?.toIntOrNull() ?: 5
        sort = p.getProperty("sort") ?: "name"
        hidden = list(p.getProperty("hidden")).toSet()
        clock = p.getProperty("clock") == "true"
        homeApps = p.getProperty("homeApps") == "true"
        windowed = p.getProperty("windowed") == "true"
        lockPortrait = p.getProperty("lockPortrait") == "true"
        fullScreen = p.getProperty("fullScreen") == "true"
        usage = p.stringPropertyNames().filter { it.startsWith(USE) }
            .associate { it.removePrefix(USE) to (p.getProperty(it).toLongOrNull() ?: 0L) }
        widgets = p.getProperty("widgets").orEmpty().split(';').filter { it.isNotBlank() }
        walls = p.stringPropertyNames().filter { it.startsWith(WALL) }
            .associate { it.removePrefix(WALL) to (p.getProperty(it).toIntOrNull() ?: 0) }
    }

    fun save(c: Context) {
        val p = Properties()
        p.setProperty("look", look); p.setProperty("vertical", vertical.toString())
        p.setProperty("columns", columns.toString()); p.setProperty("iconDp", iconDp.toString())
        p.setProperty("dock", dock.joinToString(",")); p.setProperty("dockShown", dockShown.toString())
        p.setProperty("drawerColumns", drawerColumns.toString()); p.setProperty("sort", sort)
        p.setProperty("hidden", hidden.joinToString(",")); p.setProperty("clock", clock.toString()); p.setProperty("homeApps", homeApps.toString()); p.setProperty("windowed", windowed.toString())
        p.setProperty("lockPortrait", lockPortrait.toString()); p.setProperty("fullScreen", fullScreen.toString())
        usage.forEach { (pkg, t) -> p.setProperty(USE + pkg, t.toString()) }
        walls.forEach { (lookId, i) -> p.setProperty(WALL + lookId, i.toString()) }
        p.setProperty("widgets", widgets.joinToString(";"))
        val f = file(c); val tmp = File(f.path + ".tmp")
        runCatching { tmp.outputStream().use { p.store(it, null) }; tmp.renameTo(f) }
    }

    /** Records a launch; only when the sort is by last opened (the user chose it, so nothing is recorded otherwise). */
    fun touch(c: Context, pkg: String) {
        if (sort != "opened") return
        usage = usage + (pkg to System.currentTimeMillis())
        save(c)
    }

    /** Back to the first-run settings. The look goes too: the Pulse look is restored by the next start. */
    fun reset(c: Context) {
        look = "material"; vertical = false; columns = 5; iconDp = 48; dock = emptyList(); dockShown = true
        drawerColumns = 5; sort = "name"; hidden = emptySet(); clock = false; homeApps = false; windowed = false; lockPortrait = false; fullScreen = false
        usage = emptyMap(); walls = emptyMap()
        save(c)
    }

    /** Puts the launcher's look on the shared Look before the first frame (AppLookGuard.leave restores Pulse's). */
    fun apply() {
        Look.theme = Themes.firstOrNull { it.id == look } ?: Themes.first { it.id == "material" }
    }
}
