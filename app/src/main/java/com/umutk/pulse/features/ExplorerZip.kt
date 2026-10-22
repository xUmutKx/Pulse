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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umutk.pulse.MainActivity
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Compress in the background: a zip job runs in ZipService (its own notification with progress and Cancel),
// so closing the Files screen or turning the screen off does not stop it. The Files screen only shows the progress.

private const val ZC_CH_RUN = "compress"
private const val ZC_CH_END = "compress_done"
private const val ZC_ID_RUN = 6201
private const val ZC_ID_END = 6300
private const val ZC_ACT_CANCEL = "com.umutk.pulse.ZIP_CANCEL"

enum class ZipState { RUNNING, DONE, FAILED, CANCELLED }

/** One compression: [done] counts the bytes read so far out of [total]; [cancelled] stops it between reads. */
class ZipJob(val id: Int, val name: String, val files: List<File>, val target: File, val total: Long, val kind: String = "zip") {
    val done = MutableStateFlow(0L)
    val state = MutableStateFlow(ZipState.RUNNING)
    @Volatile var cancelled = false
}

/** Thrown inside the writer when the job is cancelled. */
private class ZipCancelled : IOException("cancelled")

object ZipJobs {
    private val list = MutableStateFlow<List<ZipJob>>(emptyList())
    /** the jobs still running, oldest first */
    val jobs: StateFlow<List<ZipJob>> = list.asStateFlow()
    private val lastNote = MutableStateFlow("")
    /** the result of the job that just ended; the Files screen shows it once and clears it */
    val last: StateFlow<String> = lastNote.asStateFlow()
    private var seq = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun clearLast() { lastNote.value = "" }

    fun cancel(id: Int) { list.value.firstOrNull { it.id == id }?.let { it.cancelled = true } }

    /** Starts compressing [files] into [dir] as "[name].[kind]" (zip or 7z; a number is added when the name is taken). */
    fun start(ctx: Context, files: List<File>, name: String, dir: File, kind: String = "zip"): ZipJob {
        val c = ctx.applicationContext
        val job = ZipJob(++seq, name, files, zipTarget(dir, name, kind), files.sumOf { sizeOf(it) }, kind)
        list.value = list.value + job
        runCatching { ContextCompat.startForegroundService(c, Intent(c, ZipService::class.java)) }
        scope.launch {
            val failed = runCatching { zipWrite(job) }.exceptionOrNull()
            val st = when {
                failed == null -> ZipState.DONE
                failed is ZipCancelled -> ZipState.CANCELLED
                else -> ZipState.FAILED
            }
            if (st != ZipState.DONE) job.target.delete()
            job.state.value = st
            list.value = list.value - job
            lastNote.value = endText(job, st)
            notifyEnd(c, job, st)
        }
        return job
    }
}

/** Writes the zip entry by entry, the same layout the old Compress button made: each file or folder under its parent's name. */
private fun zipWrite(job: ZipJob) {
    if (job.kind == "7z") return sevenZWrite(job)
    val buf = ByteArray(64 * 1024)
    ZipOutputStream(job.target.outputStream().buffered(256 * 1024)).use { zos ->
        for (f in job.files) {
            val root = f.parentFile ?: job.target.absoluteFile.parentFile
            f.walkTopDown().forEach { e ->
                if (job.cancelled) throw ZipCancelled()
                val rel = e.relativeTo(root).path.replace(File.separatorChar, '/')
                if (e.isDirectory) zos.putNextEntry(ZipEntry("$rel/")) else zos.putNextEntry(ZipEntry(rel))
                if (e.isFile) e.inputStream().use { ins ->
                    while (true) {
                        if (job.cancelled) throw ZipCancelled()
                        val n = ins.read(buf)
                        if (n < 0) break
                        zos.write(buf, 0, n)
                        job.done.value += n.toLong()
                    }
                }
                zos.closeEntry()
            }
        }
    }
}

/** The 7z twin of [zipWrite]: same entries and names, written through commons-compress (rar cannot be written). */
private fun sevenZWrite(job: ZipJob) {
    val buf = ByteArray(64 * 1024)
    org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(job.target).use { z ->
        for (f in job.files) {
            val root = f.parentFile ?: job.target.absoluteFile.parentFile
            f.walkTopDown().forEach { e ->
                if (job.cancelled) throw ZipCancelled()
                val rel = e.relativeTo(root).path.replace(File.separatorChar, '/')
                z.putArchiveEntry(z.createArchiveEntry(e, rel))
                if (e.isFile) e.inputStream().use { ins ->
                    while (true) {
                        if (job.cancelled) throw ZipCancelled()
                        val n = ins.read(buf)
                        if (n < 0) break
                        z.write(buf, 0, n)
                        job.done.value += n.toLong()
                    }
                }
                z.closeArchiveEntry()
            }
        }
    }
}

private fun zipTarget(dir: File, name: String, ext: String): File {
    var target = File(dir, "$name.$ext")
    var n = 1
    while (target.exists()) target = File(dir, "$name ($n).$ext").also { n++ }
    return target
}

private fun sizeOf(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

private fun endText(job: ZipJob, st: ZipState): String = when (st) {
    ZipState.DONE -> tr("Compressed ${job.target.name} · ${exSize(job.target.length())}", "${job.target.name} sıkıştırıldı · ${exSize(job.target.length())}")
    ZipState.CANCELLED -> tr("Compression cancelled", "Sıkıştırma iptal edildi")
    else -> tr("Could not compress ${job.name}", "${job.name} sıkıştırılamadı")
}

private fun notifyEnd(c: Context, job: ZipJob, st: ZipState) {
    val nm = c.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(ZC_CH_END, tr("Finished compressing", "Sıkıştırma bitti"), NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(c, job.id, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val icon = if (st == ZipState.DONE) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
    nm.notify(ZC_ID_END + job.id, NotificationCompat.Builder(c, ZC_CH_END).setSmallIcon(icon)
        .setContentTitle(job.name).setContentText(endText(job, st)).setAutoCancel(true).setSilent(true).setTimeoutAfter(4000L).setContentIntent(open).build())
}

/** Keeps the process alive while a compression runs, and shows its progress in one notification. */
class ZipService : Service() {
    private var watching = false
    private var lastId = 0
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastId = startId
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ZC_CH_RUN, tr("Compressing", "Sıkıştırma"), NotificationManager.IMPORTANCE_LOW))
        if (intent?.action == ZC_ACT_CANCEL) ZipJobs.cancel(intent.getIntExtra("id", 0))
        val fg = runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ZC_ID_RUN, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ZC_ID_RUN, note())
        }
        if (fg.isFailure) { stopSelf(startId); return START_NOT_STICKY }
        // the CPU stays awake, so the screen going off does not stop the zip
        if (lock == null) lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pulse:zip").apply { setReferenceCounted(false) }
        if (lock?.isHeld == false) lock?.acquire(6 * 60 * 60 * 1000L)
        if (!watching) {
            watching = true
            CoroutineScope(Dispatchers.Main).launch {
                while (ZipJobs.jobs.value.isNotEmpty()) { nm.notify(ZC_ID_RUN, note()); delay(700) }
                // nothing runs any more: this check and the flag reset happen on the main thread without a gap, so a new start cannot be lost
                watching = false
                if (lock?.isHeld == true) lock?.release()
                stopSelfResult(lastId)
            }
        }
        return START_NOT_STICKY
    }

    private fun note(): Notification {
        val b = NotificationCompat.Builder(this, ZC_CH_RUN).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
        val job = ZipJobs.jobs.value.firstOrNull() ?: return b.setContentTitle("Pulse").setContentText(tr("Compression done", "Sıkıştırma bitti")).build()
        val pct = if (job.total > 0) (job.done.value * 100 / job.total).toInt().coerceIn(0, 100) else 0
        val more = ZipJobs.jobs.value.size - 1
        val cancel = PendingIntent.getService(this, job.id, Intent(this, ZipService::class.java).setAction(ZC_ACT_CANCEL).putExtra("id", job.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return b.setContentTitle(tr("Compressing ${job.name}", "${job.name} sıkıştırılıyor"))
            .setContentText("$pct%" + (if (more > 0) " · +$more" else ""))
            .setProgress(100, pct, false)
            .addAction(0, tr("Cancel", "İptal"), cancel).build()
    }
}

/** The compress box: the output name, what goes in, and the size the zip should come to. Confirm starts the job in the background. */
@Composable
fun ZipAskDialog(files: List<File>, onDismiss: () -> Unit, onStart: (String, String) -> Unit) {
    val est by produceState<ZipEstimate?>(null, files) { value = withContext(Dispatchers.IO) { zipEstimate(files) } }
    var name by remember(files) { mutableStateOf(if (files.size == 1) files[0].name else "Archive") }
    var kind by remember(files) { mutableStateOf("zip") }
    WinAlert(onDismissRequest = onDismiss,
        title = { Text(tr("Compress", "Sıkıştır")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
            // the archive format; rar is read-only in this app, so it is not offered
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("zip", "7z").forEach { k -> PChip(kind == k, k.uppercase()) { kind = k } }
            }
            ZipInfo(tr("Content", "İçerik"), zipKind(files))
            ZipInfo(tr("Items", "Öğe"), est?.items?.toString() ?: "…")
            ZipInfo(tr("Size", "Boyut"), est?.let { exSize(it.bytes) } ?: "…")
            ZipInfo(tr("Estimated archive", "Tahmini arşiv"), est?.let { "≈ " + exSize(it.packed) } ?: "…")
        } },
        confirmButton = { TextButton({ val n = name.trim().removeSuffix(".zip").removeSuffix(".7z"); if (n.isNotBlank()) onStart(n, kind) }) { Text(tr("Compress", "Sıkıştır")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "İptal")) } })
}

@Composable
private fun ZipInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = T.sub, fontSize = 13.sp)
        Text(value, color = T.text, fontSize = 13.sp)
    }
}

/** One running compression in the Files status line; it goes on when this screen is closed, and its notification shows it too. */
@Composable
fun ZipProgressLine(job: ZipJob) {
    val done by job.done.collectAsState()
    val pct = if (job.total > 0) (done * 100 / job.total).toInt().coerceIn(0, 100) else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(tr("Compressing ${job.name} · $pct%", "${job.name} sıkıştırılıyor · %$pct"), Modifier.weight(1f), color = T.sub, fontSize = 12.sp)
        PButton(tr("Cancel", "İptal"), compact = true) { job.cancelled = true }
    }
}

/** What the box shows before the zip: items, the size now, and the size the zip should come to (a guess from the file types). */
class ZipEstimate(val items: Int, val bytes: Long, val packed: Long)

/** Types that are already compressed: zipping them saves almost nothing. */
private val ZC_PACKED = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "mp3", "m4a", "aac", "ogg", "opus", "flac", "mp4", "mkv", "webm", "mov", "avi", "3gp",
    "zip", "7z", "rar", "gz", "xz", "bz2", "zst", "apk", "apks", "apkm", "xapk", "docx", "xlsx", "pptx", "odt", "ods", "odp", "jar", "aab")
/** Text-like types: they shrink to about a quarter. */
private val ZC_TEXT = setOf("txt", "md", "html", "htm", "css", "js", "json", "xml", "csv", "log", "kt", "java", "py", "sql", "svg", "srt", "lrc", "ini", "cfg", "conf", "yml", "yaml")

private fun zipRatio(ext: String): Double = when (ext) {
    in ZC_PACKED -> 1.0
    in ZC_TEXT -> 0.25
    else -> 0.6
}

internal fun zipEstimate(files: List<File>): ZipEstimate {
    var items = 0
    var bytes = 0L
    var packed = 0.0
    for (f in files) f.walkTopDown().forEach { e ->
        items++
        if (e.isFile) {
            val s = e.length()
            bytes += s
            packed += s * zipRatio(e.extension.lowercase())
        }
    }
    return ZipEstimate(items, bytes, packed.toLong())
}

private fun zipKind(files: List<File>): String {
    if (files.size == 1) {
        val f = files[0]
        return when {
            f.isDirectory -> tr("Folder", "Klasör")
            f.extension.isEmpty() -> tr("File", "Dosya")
            else -> "${f.extension.uppercase()} " + tr("file", "dosyası")
        }
    }
    val dirs = files.count { it.isDirectory }
    return tr("${files.size} items: $dirs folders, ${files.size - dirs} files", "${files.size} öğe: $dirs klasör, ${files.size - dirs} dosya")
}
