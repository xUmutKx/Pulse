package com.umutk.pulse.applock

import android.util.Log
import com.umutk.pulse.Shell
import rikka.shizuku.Shizuku

/**
 * Fast mode: one persistent root/Shizuku shell streams the activity-manager event log. "Resumed activity" lines arrive
 * before the window is drawn, so the lock can be up before the app is seen. A single blocking reader thread, restarted with
 * a growing pause when the process dies, no polling.
 */
class FastWatch(private val onResumed: (pkg: String, cls: String) -> Unit) {
    @Volatile private var run = false
    @Volatile private var proc: Process? = null
    private var thread: Thread? = null

    fun start() {
        if (run) return
        run = true
        thread = Thread({ loop() }, "AppLockFast").apply { isDaemon = true; start() }
    }

    fun stop() {
        run = false
        runCatching { proc?.destroy() }
        thread?.interrupt()
        thread = null
    }

    private fun open(): Process? {
        val cmd = "exec logcat -b events -T 1 -v brief -s wm_set_resumed_activity wm_resume_activity am_set_resumed_activity 2>&1"
        return if (Shell.shizukuOk()) Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        else if (Shell.root == true) ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        else null
    }

    private fun loop() {
        var pause = 1000L
        while (run) {
            val t0 = System.nanoTime()
            try {
                val p = open() ?: break
                proc = p
                p.inputStream.bufferedReader().useLines { lines ->
                    for (l in lines) { if (!run) break; parse(l) }
                }
                p.destroy()
            } catch (e: Exception) { if (run) Log.e("AppLock", "fast watch died", e) }
            if (!run) break
            if (System.nanoTime() - t0 > 10_000_000_000L) pause = 1000L
            try { Thread.sleep(pause) } catch (_: InterruptedException) { break }
            pause = (pause * 2).coerceAtMost(15000L)
        }
    }

    // wm_set_resumed_activity: [user,pkg/.Cls,reason]   wm_resume_activity: [user,token,task,pkg/.Cls]
    private fun parse(line: String) {
        val m = RX.find(line.substringAfter("): ", "")) ?: return
        val pkg = m.groupValues[1]
        val c = m.groupValues[2]
        onResumed(pkg, if (c.startsWith(".")) pkg + c else c)
    }

    private companion object { val RX = Regex("""([A-Za-z][\w]*(?:\.[\w]+)+)/([\w.$]+)""") }
}
