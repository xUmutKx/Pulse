package com.umutk.pulse.extras.music

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the cover search is doing; the page reads it, so leaving the page and coming back shows the same bar. */
object CoverRepairJob {
    var busy by mutableStateOf(false)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var applied by mutableIntStateOf(0)
    var finished by mutableStateOf(false)
    var left by mutableStateOf(emptyList<Track>())

    /** True while the run writes covers into the files instead of searching for them. */
    var embedding by mutableStateOf(false)
    var embedded by mutableIntStateOf(0)
    var note by mutableStateOf("")

    fun start(c: Context, embed: Boolean = false) {
        if (busy) return
        embedding = embed; embedded = 0; note = ""
        busy = true; finished = false; done = 0; total = 0; applied = 0; left = emptyList()
        runCatching { ContextCompat.startForegroundService(c.applicationContext, Intent(c.applicationContext, CoverRepairService::class.java).putExtra("embed", embed)) }
            .onFailure { busy = false }
    }
    fun stop(c: Context) { c.applicationContext.stopService(Intent(c.applicationContext, CoverRepairService::class.java)) }
}

/**
 * "Search and apply missing covers" runs here, in the background: a notification with a progress bar shows how far it is, and it goes on when the page is left.
 * The same careful match as the lookup of one song (no cover is better than a wrong one); the songs it could not match stay in [CoverRepairJob.left] for picking by hand.
 */
class CoverRepairService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val ch = "cover_repair"
    private val id = 7431

    override fun onBind(intent: Intent?): IBinder? = null

    private fun note(): Notification {
        val total = CoverRepairJob.total
        val b = NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.stat_notify_sync).setOnlyAlertOnce(true).setOngoing(true)
            .setContentTitle(tr("Searching for covers", "Kapaklar aranıyor"))
            .setContentText(if (total > 0) "${CoverRepairJob.done} / $total · " + tr("${CoverRepairJob.applied} found", "${CoverRepairJob.applied} bulundu") else "…")
        if (total > 0) b.setProgress(total, CoverRepairJob.done, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun update() { getSystemService(NotificationManager::class.java).notify(id, note()) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(ch, tr("Cover search", "Kapak arama"), NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) startForeground(id, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(id, note())
        if (job?.isActive == true) return START_NOT_STICKY
        if (intent?.getBooleanExtra("embed", false) == true) { runEmbed(nm); return START_NOT_STICKY }
        job = scope.launch {
            val todo = MusicLibrary.tracks.value.filter { !it.path.startsWith("http") && PowerampCovers.saved(this@CoverRepairService, it.path) == null && embeddedArt(this@CoverRepairService, it.path) == null }
            CoverRepairJob.total = todo.size
            val miss = ArrayList<Track>()
            for (t in todo) {
                if (!isActive) break
                val got = runCatching { CoverFetch.find(this@CoverRepairService, t, force = true) }.getOrNull()
                if (got != null) { CoverRepairJob.applied++; forgetArt(t.path) } else miss += t
                CoverRepairJob.done++
                update()
                delay(1200)
            }
            CoverRepairJob.left = miss
            CoverRepairJob.busy = false; CoverRepairJob.finished = true
            // the result stays as a notice after the bar is gone
            nm.notify(id + 1, NotificationCompat.Builder(this@CoverRepairService, ch).setSmallIcon(android.R.drawable.stat_notify_sync).setAutoCancel(true)
                .setContentTitle(tr("Cover search finished", "Kapak arama bitti"))
                .setContentText(tr("${CoverRepairJob.applied} covers applied, ${miss.size} not matched", "${CoverRepairJob.applied} kapak uygulandı, ${miss.size} eşleşmedi")).build())
            stopForeground(true); stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Writes the saved covers into the mp3 files, safely (see [CoverEmbed]); the first song is rehearsed on a copy, and a failed rehearsal stops everything. */
    private fun runEmbed(nm: NotificationManager) {
        job = scope.launch {
            val todo = CoverEmbed.candidates(this@CoverRepairService, MusicLibrary.tracks.value)
            CoverRepairJob.total = todo.size
            if (todo.isNotEmpty() && !CoverEmbed.rehearse(this@CoverRepairService, todo.first())) {
                CoverRepairJob.note = tr("The test on a copy failed, so no song was touched.", "Kopya üzerindeki deneme olmadı, bu yüzden hiçbir şarkıya dokunulmadı.")
            } else for (t in todo) {
                if (!isActive) break
                if (CoverEmbed.embed(this@CoverRepairService, t)) { CoverRepairJob.embedded++; CoverRepairJob.applied++ }
                CoverRepairJob.done++
                update()
            }
            if (CoverRepairJob.note.isEmpty()) CoverRepairJob.note = tr("${CoverRepairJob.embedded} of ${todo.size} songs got their cover inside the file", "${todo.size} şarkıdan ${CoverRepairJob.embedded} tanesine kapak dosyanın içine yazıldı")
            CoverRepairJob.busy = false; CoverRepairJob.finished = true
            nm.notify(id + 1, NotificationCompat.Builder(this@CoverRepairService, ch).setSmallIcon(android.R.drawable.stat_notify_sync).setAutoCancel(true)
                .setContentTitle(tr("Writing covers finished", "Kapak yazma bitti")).setContentText(CoverRepairJob.note).build())
            stopForeground(true); stopSelf()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        if (CoverRepairJob.busy) { CoverRepairJob.busy = false }
        super.onDestroy()
    }
}
