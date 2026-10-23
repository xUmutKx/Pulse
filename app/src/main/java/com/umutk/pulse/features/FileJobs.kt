package com.umutk.pulse.features

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.MainActivity
import com.umutk.pulse.tr
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FJ_CH_RUN = "fileops_run"
private const val FJ_CH_END = "fileops_end"
private const val FJ_ID_RUN = 7611
private const val FJ_ID_END = 7700
private const val FJ_ACT_CANCEL = "com.umutk.pulse.FILEOPS_CANCEL"

/** One running copy or delete. [total] and [done] count bytes (copy) or items (delete). */
class FileJob(val id: Int, val title: String, val total: Long) {
    val done = AtomicLong(0)
    @Volatile var cancelled = false
}

/**
 * Long file operations (a big copy, a delete of many files) report their progress in a notification and keep the process alive in the
 * background, like the compressor does. Short ones never start a job.
 */
object FileJobs {
    private val list = CopyOnWriteArrayList<FileJob>()
    private var seq = 0

    fun running(): List<FileJob> = list.toList()

    fun begin(ctx: Context, title: String, total: Long): FileJob {
        val c = ctx.applicationContext
        val job = FileJob(++seq, title, total)
        list += job
        runCatching { ContextCompat.startForegroundService(c, Intent(c, FileOpService::class.java)) }
        return job
    }

    fun cancelAll() { list.forEach { it.cancelled = true } }

    /** Ends the job: its progress goes away and a short result is posted. */
    fun end(ctx: Context, job: FileJob, text: String) {
        list -= job
        val c = ctx.applicationContext
        val nm = c.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(FJ_CH_END, tr("Finished file operations", "Biten dosya işlemleri"), NotificationManager.IMPORTANCE_DEFAULT)) else Unit
        val open = PendingIntent.getActivity(c, job.id, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(FJ_ID_END + job.id, NotificationCompat.Builder(c, FJ_CH_END).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(job.title).setContentText(text).setAutoCancel(true).setSilent(true).setTimeoutAfter(4000L).setContentIntent(open).build())
    }

    /** The bytes of a file or of everything under a folder. */
    fun treeSize(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Copies [src] to [dst] (a file or a whole folder), counting the bytes into [job] as they go. Stops quietly when the job is cancelled. */
    fun copyTree(src: File, dst: File, job: FileJob?) {
        if (job?.cancelled == true) return
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name), job) }
        } else {
            src.inputStream().use { i ->
                dst.outputStream().use { o ->
                    val buf = ByteArray(256 * 1024)
                    while (job?.cancelled != true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        job?.done?.addAndGet(n.toLong())
                    }
                }
            }
        }
    }

    /** Deletes [files] for good. Many items show a progress notification; a handful go quietly. */
    fun deleteTracked(ctx: Context, files: List<File>) {
        val total = files.sumOf { f -> if (f.isDirectory) f.walkTopDown().count().toLong() else 1L }
        if (total < 40) { files.forEach { it.deleteRecursively() }; return }
        val job = begin(ctx, tr("Deleting", "Siliniyor"), total)
        files.forEach { f ->
            f.walkBottomUp().forEach { x -> if (!job.cancelled) { x.delete(); job.done.incrementAndGet() } }
        }
        end(ctx, job, if (job.cancelled) tr("Delete cancelled", "Silme iptal edildi") else tr("Deleted $total items", "$total öğe silindi"))
    }
}

/** Keeps the process alive while a file operation runs, and shows its progress in one notification. */
class FileOpService : Service() {
    private var watching = false
    private var lastId = 0
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastId = startId
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(FJ_CH_RUN, tr("File operations", "Dosya işlemleri"), NotificationManager.IMPORTANCE_LOW)) else Unit
        if (intent?.action == FJ_ACT_CANCEL) FileJobs.cancelAll()
        val fg = runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(FJ_ID_RUN, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(FJ_ID_RUN, note())
        }
        if (fg.isFailure) { stopSelf(startId); return START_NOT_STICKY }
        if (lock == null) lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:fileops").apply { setReferenceCounted(false) }
        if (lock?.isHeld == false) lock?.acquire(6 * 60 * 60 * 1000L)
        if (!watching) {
            watching = true
            CoroutineScope(Dispatchers.Main).launch {
                while (FileJobs.running().isNotEmpty()) { nm.notify(FJ_ID_RUN, note()); delay(600) }
                watching = false
                if (lock?.isHeld == true) lock?.release()
                stopSelfResult(lastId)
            }
        }
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val b = NotificationCompat.Builder(this, FJ_CH_RUN).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
        val job = FileJobs.running().firstOrNull() ?: return b.setContentTitle("Pulse").setContentText(tr("Done", "Bitti")).build()
        val pct = if (job.total > 0) (job.done.get() * 100 / job.total).toInt().coerceIn(0, 100) else 0
        val more = FileJobs.running().size - 1
        val cancel = PendingIntent.getService(this, job.id, Intent(this, FileOpService::class.java).setAction(FJ_ACT_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return b.setContentTitle(job.title).setContentText("$pct%" + (if (more > 0) " · +$more" else ""))
            .setProgress(100, pct, false).addAction(0, tr("Cancel", "İptal"), cancel).build()
    }
}
