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
        // the main process also reads, at every start, why the Pulse processes died since last time (a crash while the app was closed, a native crash, a hang, a kill for memory)
        if (processName(app) == app.packageName) Thread { runCatching { recordExits(app) } }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun processName(app: Context): String =
        if (android.os.Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName() ?: app.packageName else app.packageName

    /** Download/Pulse/crash-log.txt: every crash and every unusual end of a Pulse process, one after the other (crash.txt keeps only the last crash). */
    private fun logFile(app: Context): File {
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/crash-log.txt")
        val ok = runCatching { pub.parentFile?.mkdirs(); pub.appendText(""); true }.getOrDefault(false)
        return if (ok) pub else File(app.filesDir, "crash-log.txt")
    }

    @Synchronized
    private fun appendLog(app: Context, text: String) {
        val f = logFile(app)
        // a log that grows past 300 KB loses its older half
        if (f.length() > 300_000) runCatching { val all = f.readText(); f.writeText("=====" + all.substring(all.length / 2).substringAfter("\n=====", "")) }
        f.appendText(text)
    }

    private val REASONS = mapOf(0 to "UNKNOWN", 1 to "EXIT_SELF", 2 to "SIGNALED", 3 to "LOW_MEMORY", 4 to "CRASH", 5 to "CRASH_NATIVE", 6 to "ANR", 7 to "INITIALIZATION_FAILURE",
        8 to "PERMISSION_CHANGE", 9 to "EXCESSIVE_RESOURCE_USAGE", 10 to "USER_REQUESTED", 11 to "USER_STOPPED", 12 to "DEPENDENCY_DIED", 13 to "OTHER", 14 to "FREEZER", 15 to "PACKAGE_STATE_CHANGE", 16 to "PACKAGE_UPDATED")

    /** Android keeps the last reasons a process of the app ended (API 30+). Whatever is new since the last start is written to the log, with the process name and the system's own words. */
    private fun recordExits(app: Context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        val sp = app.getSharedPreferences("crash_saver", Context.MODE_PRIVATE)
        val last = sp.getLong("lastExit", 0L)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val list = am.getHistoricalProcessExitReasons(app.packageName, 0, 25).filter { it.timestamp > last }.sortedBy { it.timestamp }
        if (list.isEmpty()) return
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val out = StringBuilder()
        for (e in list) {
            val reason = REASONS[e.reason] ?: e.reason.toString()
            // the ordinary ends (the user swiped the app away, the app ended itself) are one short line; the rest get everything the system says
            val plain = e.reason == 1 || e.reason == 10 || e.reason == 11
            out.append("===== ").append(df.format(Date(e.timestamp))).append("  ").append(reason).append("  ").append(e.processName).append('\n')
            out.append("status=").append(e.status).append(" importance=").append(e.importance).append(" pss=").append(e.pss).append("K rss=").append(e.rss).append("K\n")
            if (!e.description.isNullOrBlank()) out.append("description: ").append(e.description).append('\n')
            if (!plain && (e.reason == 6 || e.reason == 5 || e.reason == 2)) {
                // a hang's trace; a native crash has a binary report, of which only the readable part is kept
                val trace = runCatching { e.traceInputStream?.use { it.readBytes().take(6000).toByteArray().toString(Charsets.ISO_8859_1) } }.getOrNull()
                if (!trace.isNullOrBlank()) out.append(trace.filter { it == '\n' || it == '\t' || it in ' '..'~' }.take(4000)).append('\n')
            }
            out.append('\n')
        }
        out.insert(0, "# Pulse ${runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrDefault("?")}  Android ${android.os.Build.VERSION.RELEASE}  ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        appendLog(app, out.toString())
        sp.edit().putLong("lastExit", list.maxOf { it.timestamp }).apply()
    }

    private fun write(app: Context, t: Thread, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
            append("Pulse ").append(runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrDefault("?")).append('\n')
            append("process: ").append(processName(app)).append("  thread: ").append(t.name).append('\n').append('\n')
            append(sw.toString())
        }
        runCatching { appendLog(app, "===== " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "  JAVA CRASH  " + processName(app) + "  thread " + t.name + "\n" + sw.toString().lines().take(40).joinToString("\n") + "\n\n") }
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/crash.txt")
        val ok = runCatching { pub.parentFile?.mkdirs(); pub.writeText(text); true }.getOrDefault(false)
        if (!ok) File(app.filesDir, "crash.txt").writeText(text)
    }
}
