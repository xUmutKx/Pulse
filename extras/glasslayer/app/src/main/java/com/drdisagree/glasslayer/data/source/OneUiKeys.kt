package com.drdisagree.glasslayer.data.source

/**
 * The One UI edition (Pulse): the apps glass is put on beside System UI, each with its own switch, the inspector that writes down the
 * views of the screens so hooks can be written for Samsung's own classes, and the frame rate of the glass while nothing moves.
 */
object OneUiKeys {
    /** The apps beside System UI: package and name. Each is on by default; a switched-off one is left alone (no hook is put in it). */
    val APPS = listOf(
        "com.android.settings" to "Settings",
        "com.google.android.packageinstaller" to "Package installer",
        "com.android.packageinstaller" to "Package installer (AOSP)",
        "com.sec.android.app.myfiles" to "My Files",
        "com.sec.android.app.clockpackage" to "Clock",
    )
    val PACKAGES: Set<String> = APPS.map { it.first }.toSet()

    fun appKey(pkg: String) = "app_$pkg"

    /** Off by default: when on, every window that opens (System UI's panels, an app's screens) has its views written down and sent to the app. */
    const val INSPECTOR = "oneui_inspector"

    /** The glass frame rate while nothing on the screen moves (no touch, no panel moving): 0 = the same as when moving. Default 4. */
    const val IDLE_FPS = "idle_fps"
    const val DEFAULT_IDLE_FPS = 4

    const val ACTION_INSPECT = "com.umutk.pulse.glasslayer.action.INSPECT"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_TEXT = "text"
}
