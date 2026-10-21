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

    /** Saves Pictures/Screenshots/Pulse_<time>.png; [done] gets the path, or null when it failed, on the main thread. */
    fun take(c: Context, done: (String?) -> Unit) {
        val app = c.applicationContext
        Thread {
            val dir = "/sdcard/Pictures/Screenshots"
            val path = "$dir/Pulse_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val cmd = "mkdir -p '$dir'; screencap -p '$path' && chmod 666 '$path' && echo __OK"
            val out = if (Shell.root == true) Shell.runRoot(cmd) else Shell.run(cmd)
            val ok = out.contains("__OK") && File(path).length() > 0
            if (ok) MediaScannerConnection.scanFile(app, arrayOf(path), arrayOf("image/png"), null)
            Handler(Looper.getMainLooper()).post { done(if (ok) path else null) }
        }.start()
    }
}
