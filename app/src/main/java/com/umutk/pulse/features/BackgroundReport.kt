package com.umutk.pulse.features

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.job.JobScheduler
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.umutk.pulse.BuildConfig
import com.umutk.pulse.Shell

/**
 * What Pulse does when nobody is looking, written as one text to read or to send (Settings > Report > Performance): the services that run and since when, the jobs it has asked the
 * system to run, who keeps the processor awake, how long it ran as a foreground service today, whether the keyboard is the chosen one and the battery state; with root or Shizuku also
 * the system's own account (wake locks, alarms, jobs and the battery statistics of Pulse's user id). Nothing leaves the phone.
 */
object BackgroundReport {
    private fun age(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

    fun build(ctx: Context): String {
        val c = ctx.applicationContext
        val out = StringBuilder()
        fun head(t: String) { out.append("\n== ").append(t).append(" ==\n") }
        out.append("Pulse ").append(BuildConfig.VERSION_NAME).append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        out.append("Phone on for ").append(age(SystemClock.elapsedRealtime())).append(" · ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())).append('\n')

        head("Battery")
        runCatching {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val sc = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            out.append("level ").append(if (lvl >= 0) lvl * 100 / sc else -1).append("% · ")
                .append(if (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL) "charging" else "on battery")
                .append(" · ").append((b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f).append(" C\n")
        }
        runCatching {
            val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            out.append("power saver ").append(if (pm.isPowerSaveMode) "on" else "off").append(" · ignoring battery optimisation ").append(if (pm.isIgnoringBatteryOptimizations(c.packageName)) "YES (the system does not limit Pulse)" else "no").append('\n')
        }

        head("Keyboard")
        runCatching {
            val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
            out.append("chosen keyboard: ").append(cur.substringBefore('/')).append(if (cur.startsWith(c.packageName)) "  (Pulse keyboard is the one in use: its service stays alive all day, so its idle work counts)" else "  (not Pulse)").append('\n')
        }

        head("Services of Pulse that run now")
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching {
            @Suppress("DEPRECATION")
            val svcs = am.getRunningServices(Int.MAX_VALUE).filter { it.service.packageName == c.packageName }.sortedBy { it.activeSince }
            if (svcs.isEmpty()) out.append("none\n")
            svcs.forEach { r ->
                out.append(r.service.className.removePrefix("com.umutk.pulse.")).append(if (r.foreground) "  [notification]" else "").append(if (r.restarting > 0) "  [restarting]" else "")
                    .append("  running ").append(age(SystemClock.elapsedRealtime() - r.activeSince)).append("  process ").append(r.process.substringAfter(':', "main")).append('\n')
            }
        }.onFailure { out.append("could not read: ").append(it.message).append('\n') }
        runCatching {
            val procs = am.runningAppProcesses.orEmpty().filter { it.processName.startsWith(c.packageName) }
            procs.forEach { out.append("process ").append(it.processName).append(" importance ").append(it.importance).append(if (it.importance <= 125) "  (foreground or visible)" else if (it.importance >= 400) "  (cached)" else "  (background)").append('\n') }
        }

        head("Work Pulse asked the system to run later")
        runCatching {
            val js = (c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).allPendingJobs
            if (js.isEmpty()) out.append("no pending jobs\n")
            js.forEach { j -> out.append("job ").append(j.id).append(' ').append(j.service.className.substringAfterLast('.')).append(if (j.isPeriodic) "  every ${j.intervalMillis / 60000} min" else "").append(if (j.isRequireDeviceIdle) "  needs idle" else "").append('\n') }
        }
        out.append("watchdog keeps wanted: ").append(c.getSharedPreferences("service_watchdog", Context.MODE_PRIVATE).getStringSet("wanted", emptySet()).orEmpty().joinToString { it.substringAfterLast('.') }.ifEmpty { "nothing" }).append('\n')
        out.append("Game Hub menu service wanted: ").append(c.getSharedPreferences("gamehub", Context.MODE_PRIVATE).getBoolean("menu", true)).append('\n')

        head("Time used today (needs usage access)")
        runCatching {
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val allowed = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
            if (!allowed) { out.append("not allowed: give Pulse usage access to see this\n"); return@runCatching }
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val st = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 24L * 3600_000L, end).filter { it.packageName == c.packageName }
            val fg = st.sumOf { it.totalTimeInForeground }
            out.append("on the screen ").append(age(fg)).append('\n')
            if (Build.VERSION.SDK_INT >= 29) out.append("as a foreground service ").append(age(st.sumOf { it.totalTimeForegroundServiceUsed })).append("  (the notification time: the battery screen's \"background\")\n")
        }.onFailure { out.append("could not read: ").append(it.message).append('\n') }

        head("The system's own account (root or Shizuku)")
        if (Shell.root == true || Shell.shizukuOk()) {
            fun sh(title: String, cmd: String) { out.append("-- ").append(title).append('\n').append(runCatching { Shell.run(cmd).trim().ifEmpty { "(nothing)" } }.getOrDefault("(failed)")).append('\n') }
            val pkg = c.packageName
            sh("wake locks held by Pulse", "dumpsys power | grep -i -B1 -A2 '$pkg' | head -60")
            sh("alarms of Pulse", "dumpsys alarm | grep -i -A3 '$pkg' | head -80")
            sh("jobs of Pulse", "dumpsys jobscheduler | grep -i -A5 '$pkg' | head -80")
            sh("battery statistics of Pulse since the last charge", "dumpsys batterystats --charged | grep -i -A16 'Uid .*$pkg\\|$pkg' | head -90")
        } else out.append("not available: with root or Shizuku this part lists the wake locks, alarms, jobs and the battery statistics of Pulse\n")
        return out.toString()
    }
}
