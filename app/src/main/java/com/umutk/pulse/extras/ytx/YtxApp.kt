package com.umutk.pulse.extras.ytx

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.umutk.pulse.tr
import java.io.File

/**
 * YTXTube, the YouTube and YouTube Music client (GPL v3, github.com/Koustubh12345/YTXTube; its source is in extras/ytxtube of this repository, without Shorts and without
 * its own updater), built by the workflow with Pulse's key and carried inside the app (about 3 MB) in the open-source build. It is its own app with its own icon: Pulse installs
 * it once, and from then on it opens from the home screen like any app.
 */
object YtxApp {
    const val PKG = "com.umutk.pulse.ytx"
    private const val ASSET = "extras/ytxtube.apk"

    /** True in the open-source build when the workflow put the client into the app. */
    fun bundled(c: Context): Boolean = com.umutk.pulse.BuildConfig.APK_INSTALL && runCatching { c.assets.open(ASSET).use { true } }.getOrDefault(false)

    fun installed(c: Context): Boolean = runCatching { c.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)

    /** Opens the app when it is installed; otherwise hands its APK to Pulse's installer window. */
    fun openOrInstall(c: Context) {
        if (installed(c)) {
            val i = c.packageManager.getLaunchIntentForPackage(PKG)
            if (i != null) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        }
        install(c)
    }

    fun install(c: Context) {
        runCatching {
            val dir = File(c.cacheDir, "installer").apply { mkdirs() }
            val f = File(dir, "ytxtube.apk")
            c.assets.open(ASSET).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            c.startActivity(com.umutk.pulse.features.Downloads.openIntent(c, f).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(c, tr("Could not open the installer", "Yükleyici açılamadı"), Toast.LENGTH_LONG).show() }
    }
}
