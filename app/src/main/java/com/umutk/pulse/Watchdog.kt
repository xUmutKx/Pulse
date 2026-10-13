package com.umutk.pulse

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.umutk.pulse.features.DownloadService
import com.umutk.pulse.features.Downloads

/** Watchdog: a repeating alarm every 15 minutes restarts the monitor and running downloads when Android has stopped them. */
object Watchdog {
    private const val EVERY = 15 * 60 * 1000L

    fun isOn(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("watchdog", false)

    fun set(c: Context, on: Boolean) {
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("watchdog", on).apply()
        sync(c)
    }

    /** Schedules the alarm while the setting is on, cancels it when off. Called at app start, because alarms do not survive a reboot. */
    fun sync(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, 0, Intent(c, WatchdogReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (isOn(c)) am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + EVERY, EVERY, pi)
        else am.cancel(pi)
    }
}

/** Runs on each watchdog alarm: restarts a stopped monitor (when it is switched on) and a stopped download service (when downloads are running). */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (!Watchdog.isOn(c)) return
        Opt.load(c)
        if (!running(c, MonitorService::class.java)) MonitorService.sync(c)
        if (!running(c, DownloadService::class.java) && Downloads.activeCount() > 0) DownloadService.start(c)
    }

    @Suppress("DEPRECATION") // getRunningServices only lists this app's own services on current Android, which is all we need
    private fun running(c: Context, cls: Class<*>) =
        c.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).any { it.service.className == cls.name }
}
