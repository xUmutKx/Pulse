package com.umutk.pulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.features.FilesLook

/** True while the standalone Files app shows a see-through Windows look (MainActivity.Root then draws no opaque layer). */
object FilesGlass { var on by mutableStateOf(false) }

/** "Pulse Files": the same app opened straight on the Files page, with its own launcher entry and task (see MainActivity.handleShare). */
class FilesActivity : MainActivity() {
    private var glass = false

    // the look is process-wide (the photo / office viewers run Cfg.load and reset it): put the Files look back, now and on every resume
    fun applyLook() {
        FilesLook.load(this); FilesLook.applyGlobal()
        val id = FilesLook.theme
        Themes.firstOrNull { it.id == (if (id == "amoled") "material" else id) }?.let { if (Look.theme !== it) Look.theme = it; if (id == "amoled" && Look.mode != "dark") Look.mode = "dark" }
    }

    private fun paint() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (glass) window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        else paintWindow(Look.mode == "dark" || (Look.mode != "light" && night), FilesLook.theme == "amoled", Look.theme)
    }

    /** Win11, Win10 and Win7: the window is see-through (blurred behind on Android 12+); the other looks stay opaque. */
    fun setGlass(on: Boolean) {
        if (on == glass) return
        glass = on; FilesGlass.on = on; paint()
        if (android.os.Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(if (on && getSystemService(android.view.WindowManager::class.java).isCrossWindowBlurEnabled) 40 else 0)
    }

    // opaque theme (manifest) by default: a translucent launch window can open "behind" on some launchers; only the glass looks switch to the see-through one
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        FilesLook.load(this)
        if (FilesLook.theme == "win11") setTheme(R.style.Theme_Pulse_Files_Glass)
        super.onCreate(savedInstanceState)
    }

    // the Files look (default Material) is put in place before the first composition, so the app look never shows first
    override fun beforeContent() {
        applyLook()
        glass = FilesLook.theme == "win11"; FilesGlass.on = glass
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        paint()
        if (glass && android.os.Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(40)
    }

    // every start path re-applies the Files look before the first frame (Pulse's onCreate / viewers' Cfg.load reset the global Look)
    private fun refresh() { applyLook(); setGlass(FilesLook.theme == "win11"); paint() }
    override fun onStart() { refresh(); super.onStart() }
    override fun onNewIntent(intent: android.content.Intent) { refresh(); super.onNewIntent(intent) }
    override fun onResume() { refresh(); super.onResume() }

    // the look is process-wide: give Pulse its own back as soon as Files is not on screen
    private fun restorePulse() { Cfg.load(this); Opt.load(this) }
    override fun onStop() { super.onStop(); restorePulse() }
    override fun onDestroy() { super.onDestroy(); restorePulse() }
}
