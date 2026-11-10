package com.umutk.pulse.extras.gamehub

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.QuietChannel
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * "Look for covers" in the background: the search runs in a job that outlives the settings screen, and CoverService keeps it going with a
 * notification that shows the progress (games done, covers found) and a Stop. The settings page reads the same numbers.
 */
object CoverJob {
    val running = MutableStateFlow(false)
    val done = MutableStateFlow(0)
    val total = MutableStateFlow(0)
    val found = MutableStateFlow(0)
    private var job: Job? = null

    /** Looks for the covers of the games that have none. Does nothing while a search is running. */
    internal fun start(c: Context, games: List<GameRow>) {
        if (running.value) return
        val app = c.applicationContext
        val todo = games.filter { !coverFile(app, it.pkg).isFile }
        running.value = true; done.value = 0; total.value = todo.size; found.value = 0
        runCatching { ContextCompat.startForegroundService(app, Intent(app, CoverService::class.java)) }
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (g in todo) {
                    // a cover the person chose or Steam's art is kept
                    if (!coverFile(app, g.pkg).isFile && !steamCoverFile(app, g.pkg).isFile) {
                        if (fetchBestCover(app, g)) { found.value = found.value + 1; withContext(Dispatchers.Main) { GameArt.version.intValue++ } }
                    }
                    done.value = done.value + 1
                    delay(600)
                }
            } finally {
                running.value = false
            }
        }
    }

    fun cancel() { job?.cancel() }
}

private const val CV_CH = "cover_search"
private const val CV_ID = 7462

/** Keeps the cover search alive in the background, with its progress in the notification. */
class CoverService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel(CV_CH, tr("Cover search", "Kapak araması"), NotificationManager.IMPORTANCE_LOW))
        if (intent?.action == "cancel") CoverJob.cancel()
        val fg = runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(CV_ID, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(CV_ID, note())
        }
        if (fg.isFailure) { stopSelf(); return START_NOT_STICKY }
        CoroutineScope(Dispatchers.Main).launch {
            while (CoverJob.running.value) { nm.notify(CV_ID, note()); delay(1000) }
            nm.notify(CV_ID, NotificationCompat.Builder(this@CoverService, CV_CH).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(tr("Covers", "Kapaklar"))
                .setContentText(tr("${CoverJob.found.value} covers found", "${CoverJob.found.value} kapak bulundu")).setAutoCancel(true).build())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, CoverService::class.java).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val total = CoverJob.total.value
        val done = CoverJob.done.value
        return NotificationCompat.Builder(this, CV_CH).setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentTitle(tr("Looking for covers", "Kapaklar aranıyor"))
            .setContentText(tr("$done of $total · ${CoverJob.found.value} found", "$done / $total · ${CoverJob.found.value} bulundu"))
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .addAction(0, tr("Stop", "Durdur"), stop).build()
    }
}
