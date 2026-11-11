package com.umutk.pulse.extras.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icons of the extra apps and of Pulse itself. Each extra app has its line icon (its plain launcher alias) and a blue Material
 * glass twin ("Glass" + the alias name: the tool is found by the END of the name, so it stays the same). While an app has an icon at all, exactly
 * one of the two is switched on. The look is the person's choice (App icons page); until it is made, a tablet (smallest width 600 dp) gets glass
 * and a phone the line icons. Only a few components change at once, with DONT_KILL_APP, off the main thread.
 */
object LauncherGlass {
    /** The extra apps' launcher aliases that have a glass twin. */
    val ALIASES = listOf(
        "FilesLauncher", "CalendarLauncher", "ContactsLauncher", "ArtCraftLauncher", "PhotosLauncher", "IslandLauncher", "MusicLauncher", "VideoLauncher",
        "WhiteNoiseLauncher", "VoiceLauncher", "NotesLauncher", "PaintLauncher", "BrowserLauncher", "OfficeLauncher", "CalculatorLauncher", "ClockLauncher",
        "EbookLauncher", "TempMailLauncher", "KeyboardLauncher", "AiLauncher", "WeatherLauncher", "MessagesLauncher", "ScreenRecLauncher",
        "NotifHistLauncher", "ExtraStoreLauncher", "LocalSendLauncher", "PaletteLauncher", "GameHubLauncher", "BlackOutLauncher",
    )

    private fun sp(c: Context) = c.getSharedPreferences("launcher_glass", Context.MODE_PRIVATE)

    /** Whether the home-screen icons are glass: the person's choice, else glass on a tablet and the line icons on a phone. */
    fun glassOn(c: Context): Boolean =
        if (sp(c).contains("glass")) sp(c).getBoolean("glass", false) else c.resources.configuration.smallestScreenWidthDp >= 600

    private fun cn(c: Context, a: String) = ComponentName(c, "com.umutk.pulse.$a")
    /** On when switched on, or never touched and on in the manifest (only Pulse's default icon is; every other alias starts off). */
    private fun enabled(c: Context, a: String): Boolean = runCatching {
        val st = c.packageManager.getComponentEnabledSetting(cn(c, a))
        st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && a == "IconDefault")
    }.getOrDefault(false)
    private fun set(c: Context, a: String, on: Boolean) {
        runCatching {
            c.packageManager.setComponentEnabledSetting(
                cn(c, a), if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    fun glassName(alias: String) = "Glass$alias"

    /** Whether the app has an icon on the home screen: its line icon or its glass twin. */
    fun shown(c: Context, alias: String): Boolean = enabled(c, alias) || (alias in ALIASES && enabled(c, glassName(alias)))

    /** Puts the app's icon on the home screen or takes it off; on, it is the glass or the line one by the look. The new one is switched on before the old one goes. */
    fun setShown(c: Context, alias: String, show: Boolean) {
        if (alias !in ALIASES) { set(c, alias, show); return }
        val glass = glassOn(c)
        val want = if (glass) glassName(alias) else alias
        val other = if (glass) alias else glassName(alias)
        if (show) { set(c, want, true); set(c, other, false) } else { set(c, alias, false); set(c, glassName(alias), false) }
    }

    /** Whether Pulse's own icon is the glass one (the Windows and other looks of that icon are the person's choice and are left alone). */
    fun mainGlassShown(c: Context): Boolean = enabled(c, "IconGlass")

    /** Pulse's own default icon follows the look: only when it is the default or the glass one that is on. */
    fun applyMain(c: Context) {
        if (!(enabled(c, "IconGlass") || enabled(c, "IconDefault"))) return
        if (glassOn(c)) { set(c, "IconGlass", true); set(c, "IconDefault", false) } else { set(c, "IconDefault", true); set(c, "IconGlass", false) }
    }

    /** Every icon on the home screen takes the look. */
    fun applyAll(c: Context) {
        ALIASES.forEach { if (shown(c, it)) setShown(c, it, true) }
        applyMain(c)
    }

    /** The person's choice from the App icons page. */
    fun setGlass(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("glass", on).apply()
        Thread { applyAll(c) }.start()
    }

    /** At the start of the app, once: a tablet's icons become glass (a phone keeps its line icons). */
    fun startOnce(c: Context) {
        if (sp(c).getBoolean("applied", false)) return
        sp(c).edit().putBoolean("applied", true).apply()
        Thread { applyAll(c) }.start()
    }

    /** After a choice of Pulse's own icon in the list of looks: the default one follows the glass look, any other look leaves the glass one off. */
    fun afterMainPick(c: Context, picked: String) {
        if (picked == "IconDefault") applyMain(c) else set(c, "IconGlass", false)
    }
}
