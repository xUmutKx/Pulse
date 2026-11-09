package com.umutk.pulse

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash of Pulse as a text file, so it can be read without a computer: Download/Pulse/crash.txt (or, when that
 * folder cannot be written, the app's own folder). The previous handler still runs, so the normal crash dialog and report stay.
 */
object CrashSaver {
    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun write(app: Context, t: Thread, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
            append("Pulse ").append(runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrDefault("?")).append('\n')
            append("thread: ").append(t.name).append('\n').append('\n')
            append(sw.toString())
        }
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/crash.txt")
        val ok = runCatching { pub.parentFile?.mkdirs(); pub.writeText(text); true }.getOrDefault(false)
        if (!ok) File(app.filesDir, "crash.txt").writeText(text)
    }
}
