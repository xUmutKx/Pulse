package com.umutk.pulse

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.net.NetworkInterface
import java.util.Calendar

data class UsageRow(val pkg: String, val label: String, val ms: Long, val last: Long)
data class Svc(val pkg: String, val cls: String, val label: String)

object Extra {
    private fun read(path: String): String = try { File(path).readText().trim() } catch (e: Exception) { "" }

    fun cpuRows(): List<Pair<String, String>> {
        val info = read("/proc/cpuinfo")
        val hw = info.lineSequence().firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim().orEmpty()
        val n = Runtime.getRuntime().availableProcessors()
        var max = 0; var min = Int.MAX_VALUE
        for (i in 0 until n) {
            read("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").toIntOrNull()?.let { if (it > max) max = it }
            read("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq").toIntOrNull()?.let { if (it < min) min = it }
        }
        val gov = read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        val rows = ArrayList<Pair<String, String>>()
        rows += "Architecture" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "")
        rows += "Logical processors" to "$n"
        if (max > 0) rows += "Max speed" to "%.2f GHz".format(max / 1e6)
        if (min != Int.MAX_VALUE) rows += "Min speed" to "%.2f GHz".format(min / 1e6)
        if (gov.isNotBlank()) rows += "Governor" to gov
        if (hw.isNotBlank()) rows += "Hardware" to hw
        return rows
    }

    fun cpuName(): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim() else ""
        val hw = read("/proc/cpuinfo").lineSequence().firstOrNull { it.startsWith("Hardware") }?.substringAfter(':')?.trim().orEmpty()
        return listOf(soc, hw, Build.HARDWARE).firstOrNull { it.isNotBlank() && it != "unknown unknown" } ?: "CPU"
    }

    fun gpuName(): String {
        val m = read("/sys/class/kgsl/kgsl-3d0/gpu_model")
        if (m.isNotBlank()) return m
        return "GPU"
    }

    fun netRows(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.interfaceAddresses.mapNotNull { a -> a.address.hostAddress?.takeIf { !it.contains(':') }?.let { ni.name to it } }
        }
    } catch (e: Exception) { emptyList() }

    fun uptime(): String {
        val s = SystemClock.elapsedRealtime() / 1000
        return "%d:%02d:%02d:%02d".format(s / 86400, s % 86400 / 3600, s % 3600 / 60, s % 60)
    }

    fun usageGranted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val m = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return m == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time per app today (needs the usage-access permission). */
    fun usageToday(ctx: Context): List<UsageRow> {
        if (!usageGranted(ctx)) return emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        val pm = ctx.packageManager
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (p, l) ->
                val label = try { pm.getApplicationInfo(p, 0).loadLabel(pm).toString() } catch (e: Exception) { p }
                UsageRow(p, label, l.sumOf { it.totalTimeInForeground }, l.maxOf { it.lastTimeUsed })
            }.sortedByDescending { it.ms }
    }

    /** Running services, read from dumpsys (root only). */
    fun services(ctx: Context): List<Svc> {
        if (Shell.root != true) return emptyList()
        val pm = ctx.packageManager
        val re = Regex("ServiceRecord\\{\\S+ u\\d+ ([^/\\s]+)/([^}\\s]+)\\}")
        return re.findAll(Shell.run("dumpsys activity services")).map { it.groupValues[1] to it.groupValues[2] }.distinct().map { (p, c) ->
            val label = try { pm.getApplicationInfo(p, 0).loadLabel(pm).toString() } catch (e: Exception) { p }
            Svc(p, c, label)
        }.sortedBy { it.label.lowercase() }.toList()
    }

    fun stopService(s: Svc) { Shell.run("am stopservice -n ${s.pkg}/${s.cls}") }

    fun isApp(ctx: Context, name: String): Boolean {
        val p = name.substringBefore(':')
        return try { ctx.packageManager.getLaunchIntentForPackage(p) != null } catch (e: Exception) { false }
    }
}
