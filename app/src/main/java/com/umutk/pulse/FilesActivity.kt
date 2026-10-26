package com.umutk.pulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.features.FilesLook
import com.umutk.pulse.features.FilesPrefs

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

    /** A zip (or another archive) opened from outside: a file path is used as it is, a content Uri is copied to the cache first. */
    private fun archiveFrom(i: android.content.Intent?) {
        val u = i?.data ?: return
        if (i.action != android.content.Intent.ACTION_VIEW) return
        val name = if (u.scheme == "file") u.lastPathSegment.orEmpty() else runCatching {
            contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: u.lastPathSegment.orEmpty().substringAfterLast('/')
        // switches the Home page to Browse at once; the copy that follows (content:// only) used to leave Home showing for about a second first
        com.umutk.pulse.features.FilesLaunch.opening = true
        Thread {
            val f = if (u.scheme == "file") u.path?.let { java.io.File(it) } else runCatching {
                val d = java.io.File(cacheDir, "opened").apply { mkdirs() }
                java.io.File(d, name.ifEmpty { "archive.zip" }).also { out -> contentResolver.openInputStream(u)?.use { ins -> out.outputStream().use { ins.copyTo(it) } } }
            }.getOrNull()
            runOnUiThread { if (f != null && f.isFile) com.umutk.pulse.features.FilesLaunch.archive = f else com.umutk.pulse.features.FilesLaunch.opening = false }
        }.start()
    }

    // opaque theme (manifest) by default: a translucent launch window can open "behind" on some launchers; only the glass looks switch to the see-through one
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        FilesLook.load(this)
        if (FilesLook.theme == "win11") setTheme(R.style.Theme_Pulse_Files_Glass)
        super.onCreate(savedInstanceState)
    }

    // the Files look (default Material) is put in place before the first composition, so the app look never shows first
    override fun beforeContent() {
        archiveFrom(intent)
        applyLook()
        FilesPrefs.load(this); glass = FilesLook.theme == "win11" && FilesPrefs.glass; FilesGlass.on = glass
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        paint()
        if (glass && android.os.Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(40)
    }

    // every start path re-applies the Files look before the first frame (Pulse's onCreate / viewers' Cfg.load reset the global Look)
    private fun refresh() { applyLook(); setGlass(FilesLook.theme == "win11" && FilesPrefs.glass); paint() }
    override fun onStart() { refresh(); super.onStart() }
    override fun onNewIntent(intent: android.content.Intent) { refresh(); archiveFrom(intent); super.onNewIntent(intent) }
    override fun onResume() { refresh(); super.onResume() }

    // the look is process-wide: give Pulse its own back as soon as Files is not on screen
    private fun restorePulse() { Cfg.load(this); Opt.load(this) }
    override fun onStop() { super.onStop(); restorePulse() }
    override fun onDestroy() { super.onDestroy(); restorePulse() }
}
