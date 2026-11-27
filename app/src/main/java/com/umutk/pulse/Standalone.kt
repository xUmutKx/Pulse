package com.umutk.pulse

/**
 * One of Pulse's apps built as an app of its own (-PpulseApp=<id>, see scripts/standalone_apps.txt): [id] is empty in Pulse itself.
 * Its only launcher icon is [ALIAS]: nothing in the app may switch that icon off or switch Pulse's own icons on.
 */
object Standalone {
    val id: String get() = BuildConfig.STANDALONE
    /** One app on its own (Keyboard, Music …): Pulse's launcher and icons are off. Not for [core]. */
    val on: Boolean get() = BuildConfig.STANDALONE.isNotEmpty() && BuildConfig.STANDALONE != "core"
    /** Pulse Core: the launcher, the settings, the toolkit and the task manager, with none of the extra apps. */
    val core: Boolean get() = BuildConfig.STANDALONE == "core"
    private val aliases = mapOf("keyboard" to "KeyboardLauncher", "music" to "MusicLauncher", "video" to "VideoLauncher", "ytx" to "YtxLauncher", "code" to "CodeLauncher",
        "books" to "EbookLauncher", "gamehub" to "GameHubLauncher", "clock" to "ClockLauncher", "office" to "OfficeLauncher")
    val ALIAS: String? get() = aliases[BuildConfig.STANDALONE]
    /** False for a component a standalone app must leave as it is: its own icon, and Pulse's icons (they stay off). */
    fun mayToggle(alias: String): Boolean = !on || (alias != ALIAS && !alias.startsWith("Icon"))
}
