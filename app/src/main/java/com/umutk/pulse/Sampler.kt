package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class Proc(val pid: Int, val name: String, val rssMb: Int, val cpu: Float = 0f, val user: String = "", val state: String = "", val vszMb: Int = 0, val nice: Int = 0, val ppid: Int = 0)

data class Snap(
    val cpu: Int = 0,
    val cores: List<Core> = emptyList(),
    val cpuTemp: Float = 0f,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val down: Long = 0,
    val up: Long = 0,
    val batPct: Int = 0,
    val batMa: Int = 0,
    val batV: Float = 0f,
    val batTemp: Float = 0f,
    val charging: Boolean = false,
    val root: Boolean = false,
    val gpu: Int = -1,
    val ramCachedMb: Int = 0,
    val swapTotalMb: Int = 0,
    val swapUsedMb: Int = 0,
    val rxTotal: Long = 0,
    val txTotal: Long = 0,
    val procs: Int = 0,
    val threads: Int = 0,
)

data class Core(val usage: Int, val mhz: Int)

/** Runs a command as root when available, otherwise as the app user. */
object Shell {
    @Volatile var root: Boolean? = null

    fun run(cmd: String): String = try {
        val useRoot = root ?: false
        val p = ProcessBuilder(if (useRoot) listOf("su", "-c", cmd) else listOf("sh", "-c", cmd)).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "" }

    fun detectRoot(): Boolean {
        root = true
        val ok = run("id").contains("uid=0")
        root = ok
        return ok
    }
}

object Sampler {
    val snap = MutableStateFlow(Snap())
    /** 60-second series per key: cpu, ram, gpu, bat, ma, down, up, c0, c1, ... */
    val history = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    @Volatile var interval = 1000L
    @Volatile var lastSample = 0L
    private val series = HashMap<String, ArrayList<Float>>()
    private fun push(k: String, v: Float) { val l = series.getOrPut(k) { ArrayList() }; l.add(v); if (l.size > 60) l.removeAt(0) }

    /** 30 minutes, one point every 5 s: the long graphs of the guardians (cpu, ram, temp, btemp, bat, ma, w, down, up). */
    val longHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    private val longSeries = HashMap<String, ArrayList<Float>>()
    private var lastLong = 0L
    private fun pushLong(k: String, v: Float) { val l = longSeries.getOrPut(k) { ArrayList() }; l.add(v); if (l.size > 360) l.removeAt(0) }

    private var prevStat: List<LongArray>? = null
    private var prevRx = 0L
    private var prevTx = 0L
    private var prevAt = 0L

    private fun readFile(path: String): String = try { File(path).readText() } catch (e: Exception) { if (Shell.root == true) Shell.run("cat $path") else "" }

    private fun cpuTimes(): List<LongArray> =
        readFile("/proc/stat").lineSequence().filter { it.startsWith("cpu") }.map { l ->
            val f = l.trim().split(Regex("\\s+")).drop(1).map { it.toLongOrNull() ?: 0L }
            longArrayOf(f.take(8).sum() - f.getOrElse(3) { 0 } - f.getOrElse(4) { 0 }, f.take(8).sum())
        }.toList()

    private fun usage(a: LongArray, b: LongArray): Int {
        val total = b[1] - a[1]
        return if (total <= 0) 0 else (100 * (b[0] - a[0]) / total).toInt().coerceIn(0, 100)
    }

    private fun temp(): Float {
        var best = 0f
        for (i in 0 until 40) {
            val t = readFile("/sys/class/thermal/thermal_zone$i/temp").trim().toFloatOrNull() ?: continue
            val c = if (t > 1000) t / 1000 else t
            if (c in 20f..120f && c > best) best = c
        }
        return best
    }

    private fun gpu(): Int {
        val a = readFile("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").filter { it.isDigit() }
        if (a.isNotEmpty()) return a.toInt()
        val b = readFile("/sys/class/misc/mali0/device/utilisation").trim().toIntOrNull()
        return b ?: -1
    }

    fun sample(ctx: Context) {
        val now = System.currentTimeMillis()
        lastSample = now
        // CPU
        val cur = cpuTimes()
        val prev = prevStat
        var total = 0
        var cores = emptyList<Core>()
        if (prev != null && prev.size == cur.size && cur.size > 1) {
            total = usage(prev[0], cur[0])
            cores = (1 until cur.size).map { i ->
                val khz = readFile("/sys/devices/system/cpu/cpu${i - 1}/cpufreq/scaling_cur_freq").trim().toIntOrNull() ?: 0
                Core(usage(prev[i], cur[i]), khz / 1000)
            }
        }
        prevStat = cur
        // RAM
        val mem = readFile("/proc/meminfo").lineSequence().associate { l -> l.substringBefore(':') to (l.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L) }
        val totalMb = ((mem["MemTotal"] ?: 0) / 1024).toInt()
        val usedMb = totalMb - ((mem["MemAvailable"] ?: 0) / 1024).toInt()
        // Network
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dt = (now - prevAt).coerceAtLeast(1)
        val down = if (prevAt == 0L) 0 else (rx - prevRx) * 1000 / dt
        val up = if (prevAt == 0L) 0 else (tx - prevTx) * 1000 / dt
        prevRx = rx; prevTx = tx; prevAt = now
        // Battery
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = (bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0

        val cached = ((mem["Cached"] ?: 0) / 1024).toInt()
        val swapT = ((mem["SwapTotal"] ?: 0) / 1024).toInt()
        val swapU = swapT - ((mem["SwapFree"] ?: 0) / 1024).toInt()
        val g = gpu()
        val bpct = level * 100 / scale
        val bma = (if (kotlin.math.abs(ua) > 20000) ua / 1000 else ua)
        val pc = Procs.count
        snap.value = Snap(
            cpu = total, cores = cores, cpuTemp = temp(), ramUsedMb = usedMb, ramTotalMb = totalMb, down = down, up = up,
            batPct = level * 100 / scale,
            batMa = (if (kotlin.math.abs(ua) > 20000) ua / 1000 else ua),
            batV = (bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f,
            batTemp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            root = Shell.root == true,
            gpu = g, ramCachedMb = cached, swapTotalMb = swapT, swapUsedMb = swapU, rxTotal = rx, txTotal = tx, procs = pc.first, threads = pc.second,
        )
        push("cpu", total.toFloat()); push("ram", if (totalMb == 0) 0f else 100f * usedMb / totalMb); push("gpu", g.coerceAtLeast(0).toFloat())
        push("temp", snap.value.cpuTemp); push("bat", bpct.toFloat()); push("ma", kotlin.math.abs(bma).toFloat()); push("down", down.toFloat()); push("up", up.toFloat())
        cores.forEachIndexed { i, cr -> push("c$i", cr.usage.toFloat()) }
        history.value = series.mapValues { it.value.toList() }
        if (now - lastLong >= 5000) {
            lastLong = now
            val sn = snap.value
            pushLong("cpu", sn.cpu.toFloat()); pushLong("ram", if (totalMb == 0) 0f else 100f * usedMb / totalMb); pushLong("temp", sn.cpuTemp)
            pushLong("btemp", sn.batTemp); pushLong("bat", bpct.toFloat()); pushLong("ma", kotlin.math.abs(bma).toFloat()); pushLong("w", kotlin.math.abs(bma) * sn.batV / 1000f)
            pushLong("down", down.toFloat()); pushLong("up", up.toFloat())
            longHistory.value = longSeries.mapValues { it.value.toList() }
        }
    }

    /** Process and thread counts from /proc (everything the app user can see; root sees all). */
    object Procs {
        @Volatile var count = 0 to 0
    }

    fun processes(): List<Proc> =
        Shell.run("ps -A -o PID,RSS,%CPU,USER,S,VSZ,NI,PPID,NAME").lineSequence().drop(1).mapNotNull { l ->
            val f = l.trim().split(Regex("\\s+"), 9)
            if (f.size < 9) null else Proc(f[0].toIntOrNull() ?: return@mapNotNull null, f[8], (f[1].toIntOrNull() ?: 0) / 1024, f[2].toFloatOrNull() ?: 0f,
                f[3], f[4], (f[5].toIntOrNull() ?: 0) / 1024, f[6].toIntOrNull() ?: 0, f[7].toIntOrNull() ?: 0)
        }.filter { it.rssMb > 0 }.toList().also { Procs.count = it.size to 0 }

    fun kill(p: Proc) {
        Shell.run(if (p.name.contains('.')) "am force-stop ${p.name}; kill -9 ${p.pid}" else "kill -9 ${p.pid}")
    }
}

data class Boot(val pkg: String, val receiver: String, val label: String, val enabled: Boolean)

object Startup {
    fun list(ctx: Context): List<Boot> {
        val pm = ctx.packageManager
        val ri = pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), android.content.pm.PackageManager.GET_DISABLED_COMPONENTS)
        return ri.mapNotNull { r ->
            val ai = r.activityInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            val st = pm.getComponentEnabledSetting(android.content.ComponentName(ai.packageName, ai.name))
            val on = st != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            Boot(ai.packageName, ai.name, ai.applicationInfo.loadLabel(pm).toString(), on)
        }.sortedBy { it.label.lowercase() }
    }

    fun set(b: Boot, on: Boolean) {
        Shell.run("pm ${if (on) "enable" else "disable"} ${b.pkg}/${b.receiver}")
    }
}
