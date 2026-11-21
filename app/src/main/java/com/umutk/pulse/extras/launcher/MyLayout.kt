package com.umutk.pulse.extras.launcher

import android.content.Context

/**
 * A ready-made set-up of the apps list for one phone (the owner's): the folders, the apps that stay out of the list because they are in a folder or are tools, the
 * dock and the grid. Nothing is applied by itself: it runs only when it is asked for in Launcher settings > Home screen. Apps are found by their names, so one that
 * the phone does not have is skipped; hidden apps are still found by the folders, the dock and the home screen.
 */
object MyLayout {
    private val FOLDERS = listOf(
        "System" to listOf("Books", "Calendar", "Clock", "Technic Cl", "GAME", "Game Hub", "Notebook", "Notes", "Voice Recor"),
        "TC" to listOf("Biz", "e-Devlet", "Türk Telekom", "Tekirdağ Kart", "Keyps", "WhatsApp", "M", "MüzeKart", "TikTok Lite"),
        "Technic" to listOf("Root My Gal", "KernelSU", "Testers Coo", "Pulse"),
        "Ai" to listOf("Claude", "Gemini", "ChatGPT"),
    )
    private val HIDDEN = listOf(
        "Bare", "Beeper", "B Blaux", "Calculator", "Camera", "ColorBlendr", "Contacts", "Core Patch N", "Display Assistant", "Files", "Gallery", "Gmail", "Good Lock",
        "Google", "HMA-OSS", "Lens", "Messages", "microG Maps", "microG Settings", "Music Player", "My Files", "Nova Launcher Prime", "Nova Settings", "One UI X", "Phone",
        "Photos", "Play Store", "Proton VPN", "Pulse", "Punk'S", "Samsung Pass", "Secure Folder", "Sos Settings", "Shizuku-Next", "SIM Menu", "Sound Assistant", "Theme Park",
        "Video Player", "Wa Enhancer X", "Weather", "YTDLnis", "Ytx tube", "#Hex Next Beta",
    )
    private val DOCK = listOf("Phone", "Messages", "Camera")

    /** A listed name matches an app whose name is the same or starts with it (the lists carry names cut short by a "..."), without regard to case. */
    private fun find(apps: List<LaunchApp>, name: String): LaunchApp? {
        val n = name.lowercase()
        return apps.firstOrNull { it.label.lowercase() == n } ?: apps.firstOrNull { n.length >= 5 && it.label.lowercase().startsWith(n) }
    }

    /** Applies the set-up; returns how many apps it found in all. */
    fun apply(ctx: Context, apps: List<LaunchApp>): Int {
        val real = apps.filter { !it.pkg.startsWith("pulse:") && !it.pkg.startsWith("file:") }
        var found = 0
        // the folders (shown in the apps list, folders first)
        var folders = LauncherPrefs.folders
        var off = LauncherPrefs.folderOff
        FOLDERS.forEach { (name, list) ->
            val pkgs = list.mapNotNull { find(real, it)?.pkg }.distinct()
            if (pkgs.isEmpty()) return@forEach
            found += pkgs.size
            // an app in another folder moves here; an older folder of this name is replaced
            folders = folders.mapValues { (_, v) -> v.substringBefore('|') + "|" + v.substringAfter('|', "").split(',').filter { it.isNotBlank() && it !in pkgs }.joinToString(",") }
                .filterNot { (_, v) -> v.substringBefore('|') == name }
            val id = "my_" + name.lowercase()
            folders = folders + (id to (name + "|" + pkgs.joinToString(",")))
            off = off + id
        }
        LauncherPrefs.folders = folders; LauncherPrefs.folderOff = off.filter { it in folders }.toSet()
        // out of the apps list (the folders, the dock and the home screen still find them)
        LauncherPrefs.hidden = LauncherPrefs.hidden + HIDDEN.mapNotNull { find(real, it)?.pkg }
        // the dock and the grid
        val dock = DOCK.mapNotNull { find(real, it)?.pkg }
        if (dock.size == DOCK.size) LauncherPrefs.dock = dock
        LauncherPrefs.columns = 5; LauncherPrefs.drawerColumns = 5; LauncherPrefs.rows = 6
        // Pulse's own weather and music widgets go on the home screen (once; one already there stays as it is)
        runCatching {
            val host = LauncherWidgetHost.get(ctx)
            val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
            listOf("com.umutk.pulse.widgets.WeatherWidget", "com.umutk.pulse.widgets.music.MusicWidgetBig").forEach { cls ->
                if (LauncherPrefs.widgets.any { it.endsWith("|$cls") }) return@forEach
                val id = host.allocateAppWidgetId()
                if (mgr.bindAppWidgetIdIfAllowed(id, android.content.ComponentName(ctx.packageName, cls))) LauncherPrefs.widgets = LauncherPrefs.widgets + "$id|${ctx.packageName}|$cls"
                else host.deleteAppWidgetId(id)
            }
        }
        LauncherPrefs.save(ctx)
        return found
    }
}
