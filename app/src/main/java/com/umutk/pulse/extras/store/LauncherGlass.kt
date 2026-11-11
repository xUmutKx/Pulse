package com.umutk.pulse.extras.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icons of the extra apps and of Pulse itself, as REAL launcher icons. Each extra app has its line icon (its plain launcher alias)
 * and one Material glass twin for each of six colours ("Glass" + the colour + the alias name: the tool is found by the END of the name, so it is
 * the same one). While an app has an icon at all, exactly one of them is switched on. Pulse's own icon is the same with IconDefault and
 * IconGlass<Colour>. The look of each app is its own choice (the icon chooser's Apply); until it is made, a tablet (smallest width 600 dp) gets blue
 * glass and a phone the line icons. Components change one by one with DONT_KILL_APP, off the main thread, and only when their state differs.
 */
object LauncherGlass {
    /** Pulse's own icon: the key of its look. */
    const val MAIN = "pulse"
    const val LINE = "line"

    /** A glass colour: its id (the stored look), the end of the alias names ("" for the first) and its colour. */
    class Tone(val id: String, val suffix: String, val argb: Int)

    val TONES = listOf(
        Tone("blue", "", 0xFF3F7BFF.toInt()), Tone("purple", "Purple", 0xFFB388FF.toInt()), Tone("pink", "Pink", 0xFFFF80AB.toInt()),
        Tone("cyan", "Cyan", 0xFF80D8FF.toInt()), Tone("green", "Green", 0xFFB9F6CA.toInt()), Tone("amber", "Amber", 0xFFFFD180.toInt()),
    )

    /** The extra apps' launcher aliases that have glass twins. */
    val ALIASES = listOf(
        "FilesLauncher", "CalendarLauncher", "ContactsLauncher", "ArtCraftLauncher", "PhotosLauncher", "IslandLauncher", "MusicLauncher", "VideoLauncher",
        "WhiteNoiseLauncher", "VoiceLauncher", "NotesLauncher", "PaintLauncher", "BrowserLauncher", "OfficeLauncher", "CalculatorLauncher", "ClockLauncher",
        "EbookLauncher", "TempMailLauncher", "KeyboardLauncher", "AiLauncher", "WeatherLauncher", "MessagesLauncher", "ScreenRecLauncher",
        "NotifHistLauncher", "ExtraStoreLauncher", "LocalSendLauncher", "PaletteLauncher", "GameHubLauncher", "BlackOutLauncher",
    )

    /** Pulse's other icons (the Windows looks): they are off while a default or glass icon is applied. */
    private val MAIN_OTHERS = listOf("IconWin95", "IconXp", "IconWin7", "IconWin10", "IconWin11")

    private fun sp(c: Context) = c.getSharedPreferences("launcher_glass", Context.MODE_PRIVATE)
    private fun isTablet(c: Context) = c.resources.configuration.smallestScreenWidthDp >= 600

    private fun glassOf(alias: String, t: Tone) = if (alias == MAIN) "IconGlass${t.suffix}" else "Glass${t.suffix}$alias"
    private fun lineOf(alias: String) = if (alias == MAIN) "IconDefault" else alias

    /** Every component an app's icon can be: the line one and the six glass ones. */
    private fun variants(alias: String): List<String> = listOf(lineOf(alias)) + TONES.map { glassOf(alias, it) }

    private fun nameFor(alias: String, look: String): String = TONES.firstOrNull { it.id == look }?.let { glassOf(alias, it) } ?: lineOf(alias)

    fun supported(alias: String) = alias == MAIN || alias in ALIASES

    /** The look of an app's home-screen icon: the person's choice ([LINE] or a colour id), else blue glass on a tablet and the line icon on a phone. */
    fun lookOf(c: Context, alias: String): String =
        sp(c).getString("look_$alias", null)?.takeIf { it == LINE || TONES.any { t -> t.id == it } } ?: if (isTablet(c)) "blue" else LINE

    private fun cn(c: Context, a: String) = ComponentName(c, "com.umutk.pulse.$a")

    /** On when switched on, or never touched and on in the manifest (only Pulse's default icon is; every other alias starts off). */
    private fun enabled(c: Context, a: String): Boolean = runCatching {
        val st = c.packageManager.getComponentEnabledSetting(cn(c, a))
        st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && a == "IconDefault")
    }.getOrDefault(false)

    private fun set(c: Context, a: String, on: Boolean) {
        if (enabled(c, a) == on) return
        runCatching {
            c.packageManager.setComponentEnabledSetting(
                cn(c, a), if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** Switches [want] on first and the others of the app off after it, so the app always has an icon. */
    private fun only(c: Context, alias: String, want: String) {
        set(c, want, true)
        variants(alias).filter { it != want }.forEach { set(c, it, false) }
    }

    /** Whether the app has an icon on the home screen: its line icon or one of its glass ones. */
    fun shown(c: Context, alias: String): Boolean = if (alias in ALIASES) variants(alias).any { enabled(c, it) } else enabled(c, alias)

    /** Puts the app's icon on the home screen or takes it off; on, it is in the app's own look. */
    fun setShown(c: Context, alias: String, show: Boolean) {
        if (alias !in ALIASES) { set(c, alias, show); return }
        if (show) only(c, alias, nameFor(alias, lookOf(c, alias))) else variants(alias).forEach { set(c, it, false) }
    }

    /** Whether Pulse's own icon is a glass one. */
    fun mainGlassShown(c: Context): Boolean = TONES.any { enabled(c, glassOf(MAIN, it)) }

    /** Pulse's own default icon follows its look; another look of it (Windows and so on) that is on is left alone. */
    fun applyMain(c: Context) {
        if (variants(MAIN).none { enabled(c, it) }) return
        only(c, MAIN, nameFor(MAIN, lookOf(c, MAIN)))
    }

    /** Every icon on the home screen takes its look. */
    fun applyAll(c: Context) {
        ALIASES.forEach { if (shown(c, it)) setShown(c, it, true) }
        applyMain(c)
    }

    /** The icon chooser's Apply: the app's icon is on the home screen in [look] ([LINE] or a colour id), for real (not a shortcut). */
    fun apply(c: Context, alias: String, look: String) {
        if (!supported(alias)) return
        sp(c).edit().putString("look_$alias", look).apply()
        Thread {
            only(c, alias, nameFor(alias, look))
            if (alias == MAIN) MAIN_OTHERS.forEach { set(c, it, false) }
        }.start()
    }

    /** At the start of the app, once: a tablet's icons become blue glass (a phone keeps its line icons). */
    fun startOnce(c: Context) {
        if (sp(c).getBoolean("applied", false)) return
        sp(c).edit().putBoolean("applied", true).apply()
        Thread { applyAll(c) }.start()
    }

    /** After a pick of Pulse's own icon in the list of looks: the default one follows its look, any other look leaves the glass ones off. */
    fun afterMainPick(c: Context, picked: String) {
        if (picked == "IconDefault") applyMain(c) else TONES.forEach { set(c, glassOf(MAIN, it), false) }
    }
}
