package com.umutk.pulse.extras.screenrec

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import com.umutk.pulse.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshots through the system's own screencap, run as root (or Shizuku / adb shell). Unlike the screenshot key, screencap also reads
 * windows that are marked secure (FLAG_SECURE) on most phones, and it works while the screen is being recorded.
 */
object ScreenShot {
    /** Whether a screenshot can be taken at all: root or Shizuku is needed. */
    fun possible(): Boolean = Shell.root == true || Shell.shizukuOk()

    /** True when the picture is (almost) all black: the sign that a secure window was blanked. */
    private fun blank(path: String): Boolean {
        val bm = runCatching { android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }) }.getOrNull() ?: return true
        var lit = 0; val n = bm.width * bm.height
        val px = IntArray(n); bm.getPixels(px, 0, bm.width, 0, 0, bm.width, bm.height)
        for (c in px) if ((c shr 16 and 255) + (c shr 8 and 255) + (c and 255) > 24) lit++
        bm.recycle()
        return lit < n / 200
    }

    /** Saves Pictures/Screenshots/Pulse_<time>.png; [done] gets the path, or null when it failed, on the main thread.
     *  A secure window can come out black: the other ways root has are tried one after the other (each physical display, then the raw buffer). */
    fun take(c: Context, done: (String?) -> Unit) {
        val app = c.applicationContext
        Thread {
            val dir = "/sdcard/Pictures/Screenshots"
            val path = "$dir/Pulse_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            fun sh(cmd: String) = if (Shell.root == true) Shell.runRoot(cmd) else Shell.run(cmd)
            sh("mkdir -p '$dir'")
            var ok = false
            val tries = ArrayList<String>()
            tries += "screencap -p '$path'"
            // every physical display id the compositor lists
            sh("dumpsys SurfaceFlinger --display-id").lines().mapNotNull { Regex("Display (\\d+)").find(it)?.groupValues?.get(1) }.distinct()
                .forEach { id -> tries += "screencap -p -d $id '$path'" }
            // the raw framebuffer, wrapped into a PNG by screencap's own reader on older builds
            tries += "/system/bin/screencap '$path'"
            for (cmd in tries) {
                val out = sh("$cmd && chmod 666 '$path' && echo __OK")
                if (out.contains("__OK") && File(path).length() > 0 && !blank(path)) { ok = true; break }
            }
            // everything came out black or failed: keep the picture anyway when there is one (the window may just be black)
            val any = File(path).length() > 0
            if (any) MediaScannerConnection.scanFile(app, arrayOf(path), arrayOf("image/png"), null)
            Handler(Looper.getMainLooper()).post { done(if (ok) path else if (any) "blank:$path" else null) }
        }.start()
    }
}
