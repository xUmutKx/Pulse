package com.umutk.pulse

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pulse's own performance record (a setting, off by default): every Pulse process, while it is on, writes a line every ten seconds (how much processor it used and which of its
 * threads used it, the memory, the heat, the slow frames) and, whenever its main thread is stuck for more than a moment, what that thread was doing. It is for finding why
 * the phone lags: Settings > Report > Crash and freeze log > Performance. The first start of the update that brought it switches it on once where it was not (to see this case); where it is
 * already on nothing changes, and a fresh install keeps it off.
 */
object PerfLog {
    private const val PREFS = "perf_log"
    @Volatile var on = false
        private set
    @Volatile private var running = false
    private var sampler: Thread? = null
    private var proc = "main"
    private var isMain = true

    fun init(app: Application) {
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean("seeded208", false)) {
            val pi = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
            val updated = pi != null && pi.lastUpdateTime - pi.firstInstallTime > 60_000L
            val ed = sp.edit().putBoolean("seeded208", true)
            if (updated && !sp.contains("on")) ed.putBoolean("on", true)
            ed.apply()
        }
        val pn = if (android.os.Build.VERSION.SDK_INT >= 28) Application.getProcessName() ?: app.packageName else app.packageName
        isMain = pn == app.packageName
        proc = if (isMain) "main" else pn.substringAfter(':')
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { visible++; if (running && visible == 1) postFrames() }
            override fun onActivityStopped(a: Activity) { visible-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        on = sp.getBoolean("on", false)
        if (on) start(app)
    }

    /** Switches it on or off (this process now, the others the next time they start). */
    fun set(ctx: Context, v: Boolean) {
        val app = ctx.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", v).apply()
        on = v
        if (v) start(app) else running = false
    }

    // ---- frames: how long between two frames while the app is on the screen --------------------------------------------------------------------------------
    @Volatile private var visible = 0
    private var lastFrame = 0L
    private var slow = 0
    private var worst = 0L
    private var frames = 0
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(ns: Long) {
            val now = ns / 1_000_000L
            if (lastFrame != 0L) { val d = now - lastFrame; frames++; if (d > 50) { slow++; if (d > worst) worst = d } }
            lastFrame = now
            if (running && visible > 0) Choreographer.getInstance().postFrameCallback(this) else lastFrame = 0L
        }
    }
    private fun postFrames() { lastFrame = 0L; Handler(Looper.getMainLooper()).post { Choreographer.getInstance().postFrameCallback(frameCb) } }

    // ---- the sampler -------------------------------------------------------------------------------------------------------------------------------------------
    private fun start(app: Context) {
        if (running && sampler?.isAlive == true) return
        running = true
        if (visible > 0) postFrames()
        val main = Handler(Looper.getMainLooper())
        sampler = Thread({ runCatching { loop(app.applicationContext, main) } }, "pulse-perf").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        write(app, "# started  Pulse ${runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrDefault("?")}  Android ${android.os.Build.VERSION.RELEASE}  ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }

    /** thread id -> (name, processor ticks used so far, 1/100 s) from /proc. */
    private fun threads(): Map<Int, Pair<String, Long>> {
        val out = HashMap<Int, Pair<String, Long>>()
        File("/proc/self/task").listFiles()?.forEach { d ->
            val tid = d.name.toIntOrNull() ?: return@forEach
            runCatching {
                val st = File(d, "stat").readText()
                val name = st.substringAfter('(').substringBeforeLast(')')
                val f = st.substringAfterLast(')').trim().split(' ')
                // after the name: state is f[0], utime f[11], stime f[12]
                out[tid] = name to ((f.getOrNull(11)?.toLongOrNull() ?: 0L) + (f.getOrNull(12)?.toLongOrNull() ?: 0L))
            }
        }
        return out
    }

    private fun loop(app: Context, main: Handler) {
        var prev = threads()
        var prevAt = SystemClock.elapsedRealtime()
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        while (running) {
            // the main thread's heartbeat: a message to it that should come back in a moment
            val ack = AtomicBoolean(false)
            val t0 = SystemClock.uptimeMillis()
            main.post { ack.set(true) }
            try { Thread.sleep(250) } catch (e: InterruptedException) { return }
            if (!ack.get()) {
                var nextAt = 600L
                var reported = false
                while (!ack.get() && running) {
                    try { Thread.sleep(100) } catch (e: InterruptedException) { return }
                    val blocked = SystemClock.uptimeMillis() - t0
                    if (blocked >= nextAt) {
                        reported = true; nextAt += 2500L
                        val st = runCatching { Looper.getMainLooper().thread.stackTrace.take(30).joinToString("\n") { "    at $it" } }.getOrDefault("")
                        write(app, "${stamp()}  [$proc] MAIN THREAD STUCK for ${blocked} ms, it is in:\n$st")
                    }
                }
                if (reported) write(app, "${stamp()}  [$proc] main thread free again after ${SystemClock.uptimeMillis() - t0} ms")
            }
            val now = SystemClock.elapsedRealtime()
            if (now - prevAt >= 10_000L) {
                val cur = threads()
                val dt = (now - prevAt) / 1000.0
                val deltas = cur.mapNotNull { (tid, v) -> val d = v.second - (prev[tid]?.second ?: v.second); if (d > 0) v.first to d else null }
                val total = deltas.sumOf { it.second }
                val byName = deltas.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(4)
                val rt = Runtime.getRuntime()
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else -1
                val gc = runCatching { Debug.getRuntimeStat("art.gc.gc-count") }.getOrNull() ?: "?"
                val dl = if (isMain) runCatching { com.umutk.pulse.features.Downloads.tasks.value.count { it.state.value == com.umutk.pulse.features.DlState.RUNNING } }.getOrDefault(-1) else -1
                write(app, "${stamp()}  [$proc] cpu ${(total / dt).toInt()}%  top: " + byName.joinToString(", ") { "${it.key} ${(it.value / dt).toInt()}%" } +
                    "  |  threads ${cur.size}  heap ${(rt.totalMemory() - rt.freeMemory()) shr 20}/${rt.maxMemory() shr 20} MB  native ${Debug.getNativeHeapAllocatedSize() shr 20} MB  gc $gc" +
                    "  |  free ${mi.availMem shr 20} MB${if (mi.lowMemory) " LOW" else ""}  thermal $thermal" +
                    "  |  frames $frames slow $slow worst ${worst} ms" + (if (dl >= 0) "  |  downloads running $dl" else ""))
                slow = 0; worst = 0L; frames = 0
                prev = cur; prevAt = now
            }
        }
    }

    private fun stamp() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun file(app: Context): File {
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Crash/perf-log.txt")
        val ok = runCatching { pub.parentFile?.mkdirs(); pub.appendText(""); true }.getOrDefault(false)
        return if (ok) pub else File(app.filesDir, "perf-log.txt")
    }

    @Synchronized
    private fun write(app: Context, line: String) {
        runCatching {
            val f = file(app)
            // a log past 400 KB loses its older half
            if (f.length() > 400_000) { val all = f.readText(); f.writeText(all.substring(all.length / 2).substringAfter('\n', "")) }
            f.appendText(line + "\n")
        }
    }

    fun read(app: Context): String {
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Crash/perf-log.txt")
        val own = File(app.filesDir, "perf-log.txt")
        return listOf(pub, own).filter { runCatching { it.isFile }.getOrDefault(false) }.joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }
    }

    fun clear(app: Context) {
        runCatching { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Crash/perf-log.txt").writeText("") }
        runCatching { File(app.filesDir, "perf-log.txt").writeText("") }
    }
}

/** The Performance tab of the crash and freeze log page: the switch, and the record (newest first). */
@Composable
fun PerfLogPage() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(PerfLog.on) }
    var text by remember { mutableStateOf("") }
    var rev by remember { mutableStateOf(0) }
    LaunchedEffect(rev, on) { text = withContext(Dispatchers.IO) { PerfLog.read(ctx.applicationContext) } }
    val lines = remember(text) { text.lines().filter { it.isNotBlank() }.takeLast(400).reversed() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { on = !on; PerfLog.set(ctx, on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Performance log", "Performans kaydı"), color = T.text, fontSize = 15.sp)
                Text(tr("Every 10 seconds: processor (and which thread), memory, heat, slow frames; and what the app was doing when it got stuck", "10 saniyede bir: işlemci (hangi iş parçacığı), bellek, ısı, yavaş kareler; takıldığında uygulamanın ne yaptığı"), color = T.sub, fontSize = 12.sp)
            }
            PSwitch(on, { on = it; PerfLog.set(ctx, it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Refresh", "Yenile")) { rev++ }
            PButton(tr("Share", "Paylaş")) {
                runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.takeLast(90_000)), "Pulse performance log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PButton(tr("Clear", "Temizle"), danger = true) { PerfLog.clear(ctx.applicationContext); rev++ }
        }
        if (lines.isEmpty()) Text(if (on) tr("Nothing yet: use the app for a while, then refresh", "Henüz yok: bir süre kullan, sonra yenile") else tr("The log is off", "Kayıt kapalı"), color = T.sub, fontSize = 13.sp)
        else PCard(Modifier.fillMaxWidth()) {
            Text(lines.joinToString("\n"), color = T.sub, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
