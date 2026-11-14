package com.umutk.pulse.features

import com.umutk.pulse.PButton
import androidx.compose.foundation.lazy.items
import com.umutk.pulse.WinAlert
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.TextButton
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.umutk.pulse.BarMeter
import com.umutk.pulse.Guard
import com.umutk.pulse.Look
import com.umutk.pulse.MainActivity
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.ThemedMenu
import com.umutk.pulse.ThemedMenuItem
import com.umutk.pulse.tr
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.rate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong

/** Parallel connections for a file the server can split. */
private const val PARTS = 8 // (a copy split in the old four keeps its four: the number is read back from the saved positions)
/** Smaller files are one connection; splitting them costs more than it saves. */
private const val MIN_SPLIT = 4L shl 20
private const val BUF = 256 * 1024
private const val CH_RUN = "downloads"
private const val CH_DONE = "downloads_done"
private const val ID_RUN = 7
/** The running downloads' notifications sit together under the service's one. */
private const val GROUP = "pulse_downloads"
/** Write speed per mode in KB/s: Unlimited, Steady, Slow, Snail. 0 = no limit. */
val SPEED_KBS = intArrayOf(0, 8192, 1024, 128)
private const val APK_MIME = "application/vnd.android.package-archive"
/** Notification buttons, handled by [DownloadService]. */
private const val ACT_PAUSE = "pause"
private const val ACT_RESUME = "resume"
private const val ACT_CANCEL = "cancel"

enum class DlState { RUNNING, PAUSED, DONE, FAILED }

/** One download: where it goes, how far it has got, its speed, and how it ended. */
class DlTask(val url: String, file: File) {
    /** can change once, before the first byte, when the server names the file */
    @Volatile var file: File = file
    val done = MutableStateFlow(0L)
    val total = MutableStateFlow(-1L)
    val state = MutableStateFlow(DlState.RUNNING)
    val error = MutableStateFlow("")
    /** Bytes per second, set by [Downloads.tick]. */
    val speed = MutableStateFlow(0L)
    @Volatile var lastDone = 0L
    @Volatile var cancelled = false
    /** true while the file on disk is one sequential copy that a retry can continue from */
    @Volatile var resumable = false
    /** set after a failure: the next try uses one connection */
    @Volatile var noSplit = false
    /** where each part of a split download has got to, so a continue picks up each part where it stopped */
    @Volatile var partPos: LongArray? = null
    /** set by [Downloads.pause]: the transfer stops and keeps its partial file */
    @Volatile var paused = false
    /** the coroutine fetching this download, so a resume can wait until it has stopped */
    @Volatile var job: Job? = null
    /** speed limit: when the counted bytes started, and how many there are */
    @Volatile var gateStart = 0L
    val gateBytes = AtomicLong()
    /** ETag and Last-Modified of the first answer: saved bytes are only kept while the server still sends this file */
    @Volatile var etag: String? = null
    @Volatile var lastMod: String? = null
    /** true while the transfer waits for the network to come back */
    @Volatile var waiting = false
    /** an HLS (m3u8) download: how many of its pieces are already in the file */
    @Volatile var hlsNext = 0
    /** the name was chosen by the caller (a season episode): the server's own name is not taken */
    @Volatile var keepName = false
    /** a store download: keeps the name it was given, is not saved in the list and posts no end notification */
    @Volatile var quiet = false
    private var pending = 0L
    private var shownAt = 0L

    /** Counts [n] more bytes. [done] is published at most every 500 ms; [flush] publishes the rest. */
    fun count(n: Long) {
        synchronized(this) {
            pending += n
            val now = SystemClock.elapsedRealtime()
            if (now - shownAt >= 500) { done.value += pending; pending = 0L; shownAt = now }
        }
    }

    fun flush() {
        synchronized(this) { done.value += pending; pending = 0L }
    }
    /** this download's notification */
    val nid: Int get() = hashCode()

    /** Bytes a split download has already written, from its part positions. */
    fun partBytes(): Long = partPos?.let { p -> val step = partStep(total.value, p.size); p.indices.sumOf { p[it] - it * step } } ?: 0L

    /** What If-Range sends; a weak ETag is not allowed there. */
    fun validator(): String? = etag?.takeUnless { it.startsWith("W/") } ?: lastMod
}

/** Size of each range of a split download. */
private fun partStep(total: Long, parts: Int = PARTS) = (total + parts - 1) / parts

/**
 * Downloads run in this process and keep going while the app is in the background: [DownloadService] holds the
 * process in the foreground until the last one ends. Big files are cut into [PARTS] ranges fetched at once.
 */
object Downloads {
    val tasks = MutableStateFlow<List<DlTask>>(emptyList())
    /** Set when another app shares a link to Pulse, or a notification is tapped: the app opens the Downloads page and clears it. */
    var wanted by mutableStateOf(false)
    /** Where new downloads go. Picked on the Downloads page and kept between runs. */
    var dirPath by mutableStateOf(defaultDir())
    /** false: ask which app each time; true: open with the app that handles the file */
    var directOpen by mutableStateOf(false)
    /** index into [SPEED_KBS]: caps downloads and installer copies; kept between runs */
    var speed by mutableStateOf(0)
    /** false while there is no working network; transfers wait for it instead of failing */
    val online = MutableStateFlow(true)
    private var app: Context? = null
    internal fun appContext(): Context? = app
    private var loaded = false
    private var lastTick = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun defaultDir(): String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

    @Synchronized
    fun load(ctx: Context) {
        app = ctx.applicationContext
        val p = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
        dirPath = p.getString("dlDir", null) ?: defaultDir()
        directOpen = p.getBoolean("openDirect", false)
        speed = p.getInt("dlSpeed", 0).coerceIn(0, SPEED_KBS.size - 1)
        // the activity, the service and the boot receiver all call this; the list is read once per process
        if (loaded) return
        loaded = true
        watchNetwork(ctx.applicationContext)
        // every download kept from earlier runs: finished ones stay finished, paused ones paused, running ones carry on
        val saved = p.getStringSet("dl_list", emptySet()).orEmpty().sorted()
        val carryOn = ArrayList<DlTask>()
        tasks.value = saved.mapNotNull { row ->
            val cols = row.split('\t')
            if (cols.size < 4) return@mapNotNull null
            val f = File(cols[3])
            DlTask(cols[2], f).also { t ->
                t.resumable = cols.getOrNull(4) == "1"
                t.partPos = cols.getOrNull(5)?.takeIf { it.isNotBlank() }?.split(',')?.map { it.toLong() }?.toLongArray()
                t.total.value = cols.getOrNull(6)?.toLongOrNull() ?: -1L
                t.etag = cols.getOrNull(7)?.ifEmpty { null }
                t.lastMod = cols.getOrNull(8)?.ifEmpty { null }
                if (cols[1] == DlState.DONE.name && f.isFile) { t.state.value = DlState.DONE; t.done.value = f.length(); t.total.value = f.length() }
                else {
                    t.state.value = if (cols[1] == DlState.PAUSED.name) DlState.PAUSED else DlState.FAILED
                    if (cols[1] == DlState.FAILED.name) t.error.value = "interrupted"
                    if (cols[1] == DlState.RUNNING.name) carryOn += t
                    t.done.value = if (t.partPos != null) t.partBytes() else if (f.isFile) f.length() else 0L
                }
            }
        }
        carryOn.forEach { resume(it) }
    }

    /** Keeps [online] in step with the default network. */
    private fun watchNetwork(c: Context) {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return
        fun ok(caps: NetworkCapabilities?) = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        online.value = ok(cm.getNetworkCapabilities(cm.activeNetwork))
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) { online.value = ok(caps) }
                override fun onLost(n: Network) { online.value = false }
            })
        }
    }

    /** Saves the list (in order) so the downloads are still there after the app restarts. */
    internal fun persist() {
        val c = app ?: return
        fun clean(s: String?) = s.orEmpty().replace('\t', ' ')
        val rows = tasks.value.filter { !it.quiet }.mapIndexed { i, t -> "%04d\t%s\t%s\t%s\t%d\t%s\t%d\t%s\t%s".format(i, t.state.value.name, t.url, t.file.path, if (t.resumable) 1 else 0, t.partPos?.joinToString(",").orEmpty(), t.total.value, clean(t.etag), clean(t.lastMod)) }.toSet()
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putStringSet("dl_list", rows).apply()
    }

    fun setDirectOpen(ctx: Context, v: Boolean) {
        directOpen = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("openDirect", v).apply()
    }

    fun setSpeed(ctx: Context, v: Int) {
        speed = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putInt("dlSpeed", v).apply()
    }

    fun setDir(ctx: Context, path: String) {
        dirPath = path
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("dlDir", path).apply()
    }

    /** A file name safe for shared storage, or null when nothing usable is left. */
    internal fun cleanName(raw: String?): String? = raw?.substringAfterLast('/')?.substringAfterLast('\\')
        ?.replace(Regex("[:*?\"<>|\\x00-\\x1f]"), "_")?.trim()?.trimStart('.')?.take(120)?.takeIf { it.isNotBlank() }

    /** [fixedName] (without extension) is kept: the file is named so, with the extension its content type tells. */
    fun add(ctx: Context, url: String, fixedName: String? = null) {
        val c = ctx.applicationContext
        app = c
        scope.launch {
            val dir = File(dirPath)
            // a link of the local Stremio server ends in the file's number in the torrent ("…/64"): it is named after the torrent and the number at first, and takes the real
            // name once the server has answered (asked then, not before: the start must not wait for it)
            val name = fixedName?.let { cleanName(it) } ?: magnetName(url) ?: streamFallbackName(url) ?: cleanName(Uri.parse(url).lastPathSegment)?.let { if (isHlsUrl(url)) it.replace(Regex("(?i)\\.m3u8$"), "") + ".ts" else it } ?: "download"
            val t = try { DlTask(url, reserve(dir, name)) } catch (e: Exception) {
                DlTask(url, File(dir, name)).apply { state.value = DlState.FAILED; error.value = "cannot write to ${dir.path}: ${e.message ?: e.javaClass.simpleName}" }
            }
            // a torrent stream gives its first pieces in order: eight connections asking for eight places made it fetch pieces all over the file and start slowly
            if (isStreamLink(url)) t.noSplit = true
            if (fixedName != null) t.keepName = true
            tasks.update { it + t }
            persist()
            if (t.state.value != DlState.RUNNING) return@launch
            DownloadService.start(c)
            t.job = scope.launch { t.fetch() }
        }
    }

    /**
     * Downloads [url] to [file] and returns when it is whole: the same engine, notification and service as the
     * Downloads page, for callers that then use the file themselves. [onProgress] gets 0..1 of [size].
     * Throws IOException when it fails, CancellationException when it is cancelled or removed.
     */
    suspend fun fetchFile(c: Context, url: String, file: File, size: Long, onProgress: (Float) -> Unit) {
        val app = c.applicationContext
        load(app)
        val t = DlTask(url, file).apply { quiet = true; job = currentCoroutineContext()[Job] }
        tasks.update { it + t }
        DownloadService.start(app)
        try {
            val returned = java.util.concurrent.atomic.AtomicBoolean(false)
            coroutineScope {
                val show = launch { t.done.collect { onProgress((it.toFloat() / size).coerceIn(0f, 1f)) } }
                // cancelling the caller (before the fetch has returned) stops the transfer at its next read
                val stop = launch { try { awaitCancellation() } finally { if (!returned.get()) t.cancelled = true } }
                try { t.fetch() } finally { returned.set(true); show.cancel(); stop.cancel() }
            }
            when {
                t.state.value == DlState.DONE -> Unit
                t.cancelled -> throw CancellationException("cancelled")
                else -> throw IOException(t.error.value.ifEmpty { "download failed" })
            }
        } finally {
            tasks.update { it - t }
            if (t.state.value != DlState.DONE) t.file.delete()
        }
    }

    /** Stops a download (its partial file goes too) or removes a finished one together with its file. */
    fun remove(t: DlTask) {
        t.cancelled = true
        tasks.update { it - t }
        persist()
        app?.getSystemService(NotificationManager::class.java)?.cancel(t.nid)
        // after the writer has stopped, so it cannot bring the file back
        val running = t.job
        scope.launch {
            runCatching { running?.join() }
            if (!eraseFile(t.file)) {
                // the file is still there (the phone refused the delete): the row goes, so the file is said to stay
                val c = app ?: return@launch
                android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(c, com.umutk.pulse.tr("Could not delete ${t.file.name}", "${t.file.name} silinemedi"), android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }

    /** Deletes [f]: the plain way first, then through MediaStore (a file in the shared Downloads folder that this app may not remove directly). True when it is gone. */
    private fun eraseFile(f: File): Boolean {
        if (!f.exists()) return true
        runCatching { f.delete() }
        if (!f.exists()) return true
        val c = app ?: return false
        runCatching { c.contentResolver.delete(android.provider.MediaStore.Files.getContentUri("external"), "_data=?", arrayOf(f.path)) }
        return !f.exists()
    }

    /** A finished download whose file is gone (deleted in a file manager, an unmounted card) leaves the list: the list shows what is really there. */
    fun prune() {
        val gone = tasks.value.filter { it.state.value == DlState.DONE && !it.file.exists() }
        if (gone.isEmpty()) return
        tasks.update { l -> l - gone.toSet() }
        persist()
    }

    /** Holds a running download: the transfer stops and the partial file stays, so [resume] continues it. */
    fun pause(t: DlTask) {
        if (t.state.value != DlState.RUNNING || t.quiet) return
        t.paused = true
        t.state.value = DlState.PAUSED
        persist()
    }

    fun pauseAll() = tasks.value.forEach { pause(it) }

    fun resumeAll() = tasks.value.filter { it.state.value == DlState.PAUSED }.forEach { resume(it) }

    /** Continues a download that stopped or was paused, from the bytes it already has. */
    fun resume(t: DlTask) {
        val c = app ?: return
        if (t.state.value == DlState.DONE || t.state.value == DlState.RUNNING) return
        t.cancelled = false
        t.paused = false
        t.error.value = ""
        t.state.value = DlState.RUNNING
        persist()
        DownloadService.start(c)
        // waits for the transfer that is still stopping, so two never write the same file
        val prev = t.job
        t.job = scope.launch { prev?.join(); if (t.state.value == DlState.RUNNING) t.fetch() }
    }

    fun activeCount() = tasks.value.count { it.state.value == DlState.RUNNING }

    /** The speed of each running download since the last call. */
    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val ms = (now - lastTick).coerceIn(1L, 60_000L)
        lastTick = now
        tasks.value.forEach { t ->
            val d = t.done.value
            t.speed.value = if (t.state.value == DlState.RUNNING && !t.waiting) (d - t.lastDone).coerceAtLeast(0L) * 1000 / ms else 0L
            t.lastDone = d
        }
    }

    /** Overall progress of the running downloads, 0 to 100 (0 while no size is known). */
    fun percent(): Int {
        val known = tasks.value.filter { it.state.value == DlState.RUNNING && it.total.value > 0 }
        val total = known.sumOf { it.total.value }
        return if (total <= 0) 0 else (known.sumOf { it.done.value } * 100 / total).toInt().coerceIn(0, 100)
    }

    fun summary(): String {
        val run = tasks.value.filter { it.state.value == DlState.RUNNING }
        if (run.isEmpty()) return "Downloading in the background"
        val pct = if (run.any { it.total.value > 0 }) " · ${percent()}%" else ""
        return "${run.size} running$pct · ${rate(run.sumOf { it.speed.value }.toFloat())}"
    }

    /** A notification button that pauses, resumes or cancels [t]. */
    internal fun action(c: Context, t: DlTask, act: String): PendingIntent =
        (if (android.os.Build.VERSION.SDK_INT >= 26) PendingIntent::getForegroundService else PendingIntent::getService)(c, t.nid * 31 + act.hashCode(), Intent(c, DownloadService::class.java).setAction(act).putExtra("id", t.nid),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The ongoing notification of a running download: name, progress, speed, time left, Pause and Cancel. */
    internal fun runNote(c: Context, t: DlTask): Notification {
        val done = t.done.value
        val total = t.total.value
        val sp = t.speed.value
        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
        val text = when {
            t.waiting -> "Waiting for network · ${Guard.gb(done)}" + if (total > 0) " of ${Guard.gb(total)}" else ""
            total > 0 -> "$pct% · ${rate(sp.toFloat())}" + if (sp > 0) " · ${left((total - done) / sp)} left" else ""
            else -> "${Guard.gb(done)} · ${rate(sp.toFloat())}"
        }
        return NotificationCompat.Builder(c, CH_RUN).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(t.file.name).setContentText(text)
            .setProgress(100, pct, total <= 0 && !t.waiting)
            .setContentIntent(PendingIntent.getActivity(c, 0, openPage(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(android.R.drawable.ic_media_pause, "Pause", action(c, t, ACT_PAUSE))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", action(c, t, ACT_CANCEL))
            .setGroup(GROUP).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    /** A paused download: it stays in the shade with Resume and Cancel. */
    internal fun pausedNote(c: Context, t: DlTask): Notification =
        NotificationCompat.Builder(c, CH_RUN).setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle(t.file.name)
            .setContentText("Paused · ${Guard.gb(t.done.value)}" + if (t.total.value > 0) " of ${Guard.gb(t.total.value)}" else "")
            .setContentIntent(PendingIntent.getActivity(c, 0, openPage(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .addAction(android.R.drawable.ic_media_play, "Resume", action(c, t, ACT_RESUME))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", action(c, t, ACT_CANCEL))
            .setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    /** Posts a notification when a download ends: Open for a finished file, Retry for a failed one. */
    internal fun notifyDone(t: DlTask, ok: Boolean) {
        val c = app ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)) else Unit
        val page = PendingIntent.getActivity(c, t.nid, openPage(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(c, CH_DONE).setContentTitle(t.file.name).setAutoCancel(true)
        if (ok) {
            // a finished APK opens the installer window; anything else opens with its app
            val open = runCatching { PendingIntent.getActivity(c, t.nid + 1, openIntent(c, t.file), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }.getOrNull() ?: page
            b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentText("Download finished · ${Guard.gb(t.done.value)}")
                .setContentIntent(open).addAction(0, "Open", open)
        } else {
            b.setSmallIcon(android.R.drawable.stat_notify_error).setContentText("Download failed: ${t.error.value}")
                .setContentIntent(page).addAction(0, "Retry", action(c, t, ACT_RESUME))
        }
        nm.notify(t.nid, b.build())
    }

    /** What opening [f] starts: the installer for an APK, else its app or the phone's chooser. */
    internal fun openIntent(c: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        if (f.extension.equals("apk", true))
            return Intent(c, InstallActivity::class.java).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, fileMime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri("", uri)
        // ask: the phone's own chooser shows every app that can open the file
        return if (directOpen && view.resolveActivity(c.packageManager) != null) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent.createChooser(view, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens a file with the app that handles it (an APK goes to the installer). */
    fun openFile(ctx: Context, f: File) {
        try {
            ctx.startActivity(openIntent(ctx, f))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not open ${f.name}: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens a folder in the phone's file app. */
    fun openFolder(ctx: Context, dir: File) {
        val rel = dir.path.removePrefix(Environment.getExternalStorageDirectory().path).trim('/')
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + Uri.encode(rel))
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.document/directory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No file app opens this folder: ${dir.path}", Toast.LENGTH_LONG).show()
        }
    }

    internal fun openPage(c: Context) = Intent(c, MainActivity::class.java).putExtra("downloads", true)
}

/** "1h 4m", "3m 20s", "12s" */
private fun left(sec: Long): String = when {
    sec >= 3600 -> "${sec / 3600}h ${sec % 3600 / 60}m"
    sec >= 60 -> "${sec / 60}m ${sec % 60}s"
    else -> "${sec}s"
}

/** A free name in [dir], created empty at once so two downloads never take the same one. "a.zip" becomes "a (1).zip". */
@Synchronized
private fun reserve(dir: File, name: String): File {
    dir.mkdirs()
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var f = File(dir, name)
    var n = 1
    while (!f.createNewFile()) f = File(dir, "$base ($n)$ext").also { n++ }
    return f
}

/**
 * A torrent stream link of the Stremio server on the phone (http://127.0.0.1:11470/<info hash>/<file number>?tr=...): the name of the video, from the server's own
 * stats page; when that is not there yet, "video-<start of the hash>-<number>". Null for any other link.
 */
private fun streamParts(url: String): List<String>? {
    val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = u.host ?: return null
    if (host != "127.0.0.1" && host != "localhost") return null
    val seg = u.pathSegments
    if (seg.size < 2 || !Regex("[0-9a-fA-F]{40}").matches(seg[0]) || seg[1].toIntOrNull() == null) return null
    return listOf(u.scheme.orEmpty(), u.authority.orEmpty(), seg[0], seg[1])
}
internal fun isStreamLink(url: String) = streamParts(url) != null
/** "video-<start of the hash>-<number>": the name before the server has told the real one. */
private fun streamFallbackName(url: String): String? = streamParts(url)?.let { "video-" + it[2].take(8) + "-" + it[3] }
/** The real name of the video, from the stream server's stats page (short timeouts: it is asked while the download runs). Null when it does not say. */
private fun streamName(url: String): String? {
    val p = streamParts(url) ?: return null
    return runCatching {
        val c = java.net.URL("${p[0]}://${p[1]}/${p[2]}/${p[3]}/stats.json").openConnection() as HttpURLConnection
        c.connectTimeout = 1500; c.readTimeout = 2500
        try { org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("streamName").takeIf { it.isNotBlank() } } finally { c.disconnect() }
    }.getOrNull()
}

/** [name] with the extension the content type tells, when the name has none (a stream link has no file name in it). */
private fun withExtension(name: String, contentType: String?): String {
    val ext = name.substringAfterLast('.', "")
    if (ext.length in 1..5 && ext.any { it.isLetter() }) return name
    val e = when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
        "video/mp4" -> "mp4"; "video/x-matroska" -> "mkv"; "video/webm" -> "webm"; "video/x-msvideo" -> "avi"; "video/quicktime" -> "mov"; "video/mpeg" -> "mpg"
        "audio/mpeg" -> "mp3"; "audio/mp4", "audio/x-m4a" -> "m4a"; "audio/flac" -> "flac"; "audio/ogg" -> "ogg"; "audio/wav" -> "wav"
        "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"
        "application/pdf" -> "pdf"; "application/zip" -> "zip"; "application/vnd.android.package-archive" -> "apk"
        else -> null
    } ?: return name
    return "$name.$e"
}

/** The name from a Content-Disposition header: filename* (RFC 5987) first, else filename. */
private fun dispositionName(h: String?): String? {
    if (h == null) return null
    Regex("filename\\*\\s*=\\s*[^']*'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(h)?.let { return Uri.decode(it.groupValues[1].trim().trim('"')) }
    return Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.trim()
}

/** The name a magnet link gives itself ("dn"), as a file name; null when it has none (or it is not a magnet link). */
private fun magnetName(url: String): String? =
    if (!url.startsWith("magnet:", ignoreCase = true)) null
    else Downloads.cleanName(magnetParam(url, "dn")) ?: "torrent"

/** A parameter of a magnet link. (Uri.getQueryParameter throws on it: "This isn't a hierarchical URI".) */
internal fun magnetParam(url: String, key: String): String? =
    url.substringAfter('?', "").split('&').firstNotNullOfOrNull { p ->
        val k = p.substringBefore('=')
        if (k == key) Uri.decode(p.substringAfter('=', "").replace('+', ' ')) else null
    }

/**
 * A magnet link: the torrent engine downloads the one file wanted into a working folder, and the finished file takes the place of the empty one reserved for this download.
 * Pausing or removing stops it and keeps what is there, so a continue goes on from the pieces already on disk.
 */
private suspend fun DlTask.transferTorrent(torrent: ByteArray?): Boolean {
    if (!TorrentEngine.available) throw IOException("the torrent engine is in the full build, on 64-bit phones")
    val c = Downloads.appContext() ?: throw IOException("no context for the torrent engine")
    resumable = false
    // the torrent's own working folder sits next to the downloads (shared by the downloads of the same torrent: the episodes of a season pack)
    val got = TorrentEngine.download(c, url, torrent, file.parentFile ?: throw IOException("no folder for this download"), { cancelled || paused },
        { name ->
            // the file's own name (or the chosen name with its extension), while nothing is written yet
            if (!quiet && done.value == 0L) {
                val ext = name.substringAfterLast('.', "")
                rename(if (keepName) file.nameWithoutExtension + (if (ext.isNotEmpty()) ".$ext" else "") else name)
            }
        },
        { d, t, p ->
            // while the file list is on its way, or no peer is sending yet, the row says so (instead of a still "0 MB")
            if (t > 0) total.value = t
            done.value = d
            error.value = when { t <= 0 -> "finding peers ($p)"; p == 0 && d < t -> "no peers yet"; else -> "" }
        },
    ) ?: return false
    file.delete()
    if (!got.renameTo(file)) { got.copyTo(file, overwrite = true); got.delete() }
    TorrentEngine.done(got)
    total.value = file.length(); done.value = file.length()
    Downloads.persist()
    return true
}

/** A link to a .torrent file. */
internal fun isTorrentFileUrl(u: String) = Regex("\\.torrent(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)

/** A link to an HLS playlist (.m3u8): a list of the video's pieces, not a video. */
internal fun isHlsUrl(u: String) = Regex("\\.m3u8(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)

private class HlsSeg(val uri: String, val keyUri: String?, val iv: ByteArray?, val seq: Long)

private fun hlsBytes(u: String): ByteArray {
    val c = open(u, "bytes=0-")
    try {
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        return c.inputStream.use { it.readBytes() }
    } finally { c.disconnect() }
}

private fun hlsResolve(base: String, ref: String): String = java.net.URI(base).resolve(ref.trim()).toString()
private fun hlsAttr(line: String, key: String): String? =
    Regex("(?:^|[,:])" + key + "=(\"[^\"]*\"|[^,]*)").find(line)?.groupValues?.get(1)?.trim('"')

/**
 * Downloads an HLS playlist: the best quality of a master list, then every piece in order into one file (.ts, or .mp4 when the pieces are fragmented MP4),
 * four pieces at a time. AES-128 encrypted pieces are decrypted. A retry goes on from the piece it had reached.
 */
private suspend fun DlTask.transferHls(): Boolean {
    resumable = false
    var listUrl = url
    var text = String(hlsBytes(listUrl))
    if (text.contains("#EXT-X-STREAM-INF")) {
        val ls = text.lines(); var best = -1L; var pick: String? = null
        for (i in ls.indices) if (ls[i].startsWith("#EXT-X-STREAM-INF")) {
            val bw = hlsAttr(ls[i], "BANDWIDTH")?.toLongOrNull() ?: 0L
            val uri = ls.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (uri != null && bw >= best) { best = bw; pick = uri }
        }
        listUrl = hlsResolve(listUrl, pick ?: throw IOException("no video in the playlist"))
        text = String(hlsBytes(listUrl))
    }
    var seq = 0L; var keyUri: String? = null; var iv: ByteArray? = null; var map: String? = null
    val segs = ArrayList<HlsSeg>()
    for (ln in text.lines()) {
        val l = ln.trim()
        when {
            l.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> seq = l.substringAfter(':').trim().toLongOrNull() ?: 0L
            l.startsWith("#EXT-X-KEY:") -> {
                if (hlsAttr(l, "METHOD") == "AES-128") {
                    keyUri = hlsAttr(l, "URI")?.let { hlsResolve(listUrl, it) }
                    iv = hlsAttr(l, "IV")?.removePrefix("0x")?.removePrefix("0X")?.padStart(32, '0')?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
                } else if (hlsAttr(l, "METHOD") == "NONE") { keyUri = null; iv = null }
                else throw IOException("this playlist is encrypted in a way Pulse cannot read")
            }
            l.startsWith("#EXT-X-MAP:") -> map = hlsAttr(l, "URI")?.let { hlsResolve(listUrl, it) }
            l.isNotEmpty() && !l.startsWith("#") -> { segs.add(HlsSeg(hlsResolve(listUrl, l), keyUri, iv, seq)); seq++ }
        }
    }
    if (segs.isEmpty()) throw IOException("the playlist has no pieces")
    // the file takes the right extension while nothing is written yet
    if (hlsNext == 0 && !quiet) rename(file.nameWithoutExtension.ifBlank { "video" } + if (map != null) ".mp4" else ".ts")
    val keys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    fun piece(s: HlsSeg): ByteArray {
        var raw = hlsBytes(s.uri)
        val k = s.keyUri
        if (k != null) {
            val key = keys.getOrPut(k) { hlsBytes(k) }
            val v = s.iv ?: ByteArray(16).also { b -> var n = s.seq; for (i in 15 downTo 8) { b[i] = (n and 0xff).toByte(); n = n shr 8 } }
            val c = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(v))
            raw = c.doFinal(raw)
        }
        return raw
    }
    val fresh = hlsNext == 0
    if (fresh) { done.value = 0L; lastDone = 0L }
    val out = FileOutputStream(file, !fresh)
    try {
        if (fresh && map != null) { val init = hlsBytes(map); out.write(init); count(init.size.toLong()) }
        var i = hlsNext
        while (i < segs.size) {
            if (cancelled || paused) { return false }
            val chunk = segs.subList(i, minOf(i + 4, segs.size))
            val got = coroutineScope { chunk.map { s -> async(Dispatchers.IO) { piece(s) } }.awaitAll() }
            for (b in got) { out.write(b); count(b.size.toLong()) }
            i += chunk.size
            hlsNext = i
            // the size is not known before the end: it is guessed from the pieces so far (the percent and the time left follow it)
            total.value = if (i > 0) maxOf(done.value + 1, (done.value.toDouble() / i * segs.size).toLong()) else -1L
        }
        out.flush()
    } finally { runCatching { out.close() }; flush() }
    total.value = done.value
    Downloads.persist()
    return true
}

/** Errors without progress in a row before a download fails. Time offline does not count. */
private const val RETRIES = 8

/** The server did not answer a range request, so a split cannot go on. */
private class NoRange(msg: String) : IOException(msg)
/** The server now sends a different file than the saved bytes came from. */
private class Changed : IOException("the file changed on the server")

/** Opens [url] with a byte range, following redirects by hand (the built-in follow stops at http to https). */
private fun open(url: String, range: String, ifRange: String? = null): HttpURLConnection {
    var u = URL(url)
    repeat(10) {
        val c = (u.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            // a torrent stream answers only once it has found its first pieces: that can take a minute or two
            readTimeout = if (isStreamLink(url)) 150_000 else 30_000
            // some hosts refuse the default Java agent
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            setRequestProperty("Range", range)
            // a changed file answers 200 with the whole body instead of the stale range
            if (ifRange != null) setRequestProperty("If-Range", ifRange)
            // a compressed body would not match the byte ranges
            setRequestProperty("Accept-Encoding", "identity")
        }
        val code = c.responseCode
        val to = c.getHeaderField("Location")
        if (code !in 300..399 || code == 304 || to == null) return c
        c.disconnect()
        u = URL(u, to)
    }
    throw IOException("too many redirects")
}

/**
 * Tries the transfer again after a failure, continuing from the bytes on disk. While the network is gone it waits
 * for it without counting a try; it gives up after [RETRIES] failures in a row that brought no bytes.
 */
private suspend fun DlTask.fetch() {
    var tries = 0
    while (!cancelled && !paused) {
        val before = done.value
        try {
            // false: paused or removed; the partial file stays for the next resume
            if (!transfer() || cancelled) return
            error.value = ""
            state.value = DlState.DONE
            Downloads.persist()
            return
        } catch (e: Exception) {
            if (cancelled || paused) return
            if (e is Changed) { startOver(); continue }
            // no ranges from the server: the parts are dropped and the file starts again on one connection
            if (e is NoRange) partPos = null
            // a split download keeps its part positions, so only a single copy falls back to one connection
            if (partPos == null) noSplit = true
            Downloads.persist()
            if (done.value > before) tries = 0
            // the network callback can lag behind the broken socket
            delay(1500)
            if (!Downloads.online.value) { waitForNetwork(); continue }
            tries++
            // a refusal from the server (404, 403, 410...) is final; a network error never is: it keeps retrying once a minute
            val refused = Regex("HTTP 4\\d\\d").matches(e.message.orEmpty()) && e.message != "HTTP 408" && e.message != "HTTP 429"
            if (tries >= RETRIES && !refused) {
                error.value = "retrying: ${e.message ?: e.javaClass.simpleName}"
                delay(60_000L)
                continue
            }
            if (tries >= RETRIES) {
                error.value = e.message ?: e.javaClass.simpleName
                state.value = DlState.FAILED
                Downloads.persist()
                return
            }
            error.value = "retrying (${tries}/${RETRIES - 1}): ${e.message ?: e.javaClass.simpleName}"
            delay(minOf(30_000L, 1000L shl tries))
        }
    }
}

private suspend fun DlTask.waitForNetwork() {
    waiting = true
    error.value = "waiting for network"
    while (!Downloads.online.value && !cancelled && !paused) delay(1000)
    waiting = false
    error.value = ""
}

/** Drops every saved byte: the next try fetches the file from the start. */
private fun DlTask.startOver() {
    hlsNext = 0
    partPos = null
    resumable = false
    etag = null
    lastMod = null
    total.value = -1L
    done.value = 0L
    Downloads.persist()
}

/** Takes the name the server gives, while nothing is written yet. */
private fun DlTask.rename(raw: String?) {
    val name = Downloads.cleanName(raw) ?: return
    if (name == file.name) return
    val next = runCatching { reserve(file.parentFile ?: return, name) }.getOrNull() ?: return
    file.delete()
    file = next
    Downloads.persist()
}

/** true when the whole file arrived; false when it was paused or removed on the way. */
private suspend fun DlTask.transfer(): Boolean {
    // a magnet link: the torrent engine fetches the file from the peers
    if (url.startsWith("magnet:", ignoreCase = true)) return transferTorrent(null)
    // a link to a .torrent file: the small file is fetched, and the engine downloads what it lists
    if (isTorrentFileUrl(url)) return transferTorrent(hlsBytes(url))
    // a playlist (m3u8) is not the video: its pieces are fetched one after the other into one file
    if (isHlsUrl(url)) return transferHls()
    // split parts written into a file that is gone would leave holes
    if (partPos != null && !file.isFile) partPos = null
    // a single sequential copy resumes from its length; a split download resumes from its part positions
    val have = if (resumable && file.isFile) file.length() else 0L
    val started = have > 0 || partPos != null
    val first = open(url, if (have > 0) "bytes=$have-" else "bytes=0-", if (started) validator() else null)
    try {
        val code = first.responseCode
        // asked from the end of the file: the saved copy is already whole, or the file shrank
        if (code == 416 && have > 0) { if (have == total.value) return true; throw Changed() }
        if (code !in 200..299) throw IOException("HTTP $code")
        if (!started && first.contentType?.contains("mpegurl", ignoreCase = true) == true) { first.disconnect(); return transferHls() }
        if (!started && first.contentType?.contains("bittorrent", ignoreCase = true) == true) { first.disconnect(); return transferTorrent(hlsBytes(url)) }
        val ranged = code == 206
        val size = if (ranged) first.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else com.umutk.pulse.Compat.contentLength(first)
        val tag = first.getHeaderField("ETag")
        val mod = first.getHeaderField("Last-Modified")
        // the saved bytes only count for the same file: same validators, same size
        if (started && ranged && ((etag != null && tag != etag) || (lastMod != null && mod != lastMod) || (total.value > 0 && size != total.value))) throw Changed()
        etag = tag
        lastMod = mod
        if (!started && !quiet) {
            val given = if (keepName) null else dispositionName(first.getHeaderField("Content-Disposition")) ?: streamName(url) ?: Uri.decode(first.url.path).takeIf { first.url.toString() != url }
            rename(withExtension(given ?: file.name, first.contentType))
        }
        // no ranges this time: the parts cannot go on, the copy below starts from the first byte
        if (partPos != null && !ranged) partPos = null
        total.value = size
        Downloads.persist()
        return if (partPos != null || (have == 0L && ranged && size > MIN_SPLIT && !noSplit)) {
            first.disconnect()
            split(size)
        } else {
            val resumed = ranged && have > 0
            first.inputStream.use { copy(it, append = resumed, start = if (resumed) have else 0L) }
        }
    } finally {
        first.disconnect()
        flush()
    }
}

/** Writes each range from its own connection at its own place in the file. [partPos] keeps where each part has got to, so a resume goes on from there. */
private suspend fun DlTask.split(total: Long): Boolean = coroutineScope {
    resumable = false
    val parts = partPos?.size ?: PARTS
    val step = partStep(total, parts)
    val fresh = partPos == null
    val pos = partPos ?: LongArray(parts) { it * step }.also { partPos = it }
    val tag = validator()
    java.io.RandomAccessFile(file, "rw").also { if (fresh) it.setLength(0) }.channel.use { ch ->
        done.value = pos.indices.sumOf { pos[it] - it * step }
        (0 until parts).map { i ->
            async(Dispatchers.IO) {
                val end = minOf(total, (i + 1) * step) - 1
                if (pos[i] > end) return@async
                val c = open(url, "bytes=${pos[i]}-$end", tag)
                try {
                    // a 200 here is the whole file (no ranges, or it changed); a wrong start would land in the wrong place
                    if (c.responseCode != 206 || c.getHeaderField("Content-Range")?.startsWith("bytes ${pos[i]}-") != true)
                        throw NoRange("the server does not split the file (HTTP ${c.responseCode})")
                    c.inputStream.use { ins ->
                        val buf = ByteArray(BUF)
                        while (isActive && !cancelled && !paused && pos[i] <= end) {
                            val n = ins.read(buf, 0, minOf(BUF.toLong(), end + 1 - pos[i]).toInt())
                            // the stream ended before the part did: retry, the bytes so far are kept
                            if (n < 0) throw IOException("connection closed early")
                            val bb = ByteBuffer.wrap(buf, 0, n)
                            while (bb.hasRemaining()) ch.write(bb, pos[i] + bb.position())
                            pos[i] += n
                            count(n.toLong())
                            throttle(n)
                        }
                    }
                } finally {
                    c.disconnect()
                }
            }
        }.awaitAll()
    }
    !cancelled && !paused
}

/** Copy for the installer: the same speed cap as downloads (one stream, so a local counter). */
fun InputStream.copyCapped(out: java.io.OutputStream) {
    val kbs = SPEED_KBS[Downloads.speed]
    val buf = ByteArray(if (kbs in 1..1023) 4096 else 65536)
    val t0 = System.currentTimeMillis(); var sent = 0L
    while (true) {
        val n = read(buf); if (n < 0) break
        out.write(buf, 0, n); sent += n
        if (kbs > 0) { val wait = sent * 1000 / (kbs * 1024L) - (System.currentTimeMillis() - t0); if (wait > 0) Thread.sleep(wait) }
    }
}

/** Sleeps when this transfer is ahead of the chosen speed; the bytes count from the moment a cap is on. */
private fun DlTask.throttle(n: Int) {
    val kbs = SPEED_KBS[Downloads.speed]
    if (kbs == 0) { gateBytes.set(0); gateStart = 0L; return }
    val now = System.currentTimeMillis()
    if (gateStart == 0L) gateStart = now
    val due = gateBytes.addAndGet(n.toLong()) * 1000 / (kbs * 1024L)
    val wait = due - (now - gateStart)
    if (wait > 0) Thread.sleep(wait)
}

private fun DlTask.copy(ins: InputStream, append: Boolean = false, start: Long = 0L): Boolean {
    resumable = true
    done.value = start
    FileOutputStream(file, append).use { out ->
        val buf = ByteArray(BUF)
        while (!cancelled && !paused) {
            val n = ins.read(buf)
            if (n < 0) {
                // a clean end before the announced size is a dropped connection, not a finished file
                if (total.value > 0 && done.value < total.value) throw IOException("connection closed early")
                return true
            }
            out.write(buf, 0, n)
            count(n.toLong())
            throttle(n)
        }
    }
    return false
}

/**
 * Keeps the app in the foreground while downloads run, so they go on when Pulse leaves the screen or is swiped away.
 * Each running download has its own notification; this service's one groups them.
 */
class DownloadService : Service() {
    /** main thread only, like [onStartCommand], so a stop cannot cross a new start */
    private var watching = false
    private var lastId = 0
    /** the state each download had at the last pass, to post its end once */
    private val seen = HashMap<DlTask, DlState>()
    /** keeps the CPU awake while a download runs, so the screen going off does not stop it */
    private var lock: android.os.PowerManager.WakeLock? = null
    /** keeps Wi-Fi at full speed and awake while the screen is off (the CPU lock alone does not) */
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastId = startId
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH_RUN, "Downloads", NotificationManager.IMPORTANCE_LOW)) else Unit
        val fg = runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID_RUN, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID_RUN, note())
        }
        if (fg.isFailure) { stopSelf(startId); return START_NOT_STICKY }
        // a restart after the process was killed brings the list back and carries on the running ones
        Downloads.load(this)
        val t = intent?.getIntExtra("id", 0)?.let { id -> Downloads.tasks.value.find { it.nid == id } }
        if (t != null) when (intent?.action) {
            ACT_PAUSE -> Downloads.pause(t)
            ACT_RESUME -> Downloads.resume(t)
            ACT_CANCEL -> Downloads.remove(t)
        }
        if (lock == null) {
            lock = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "pulse:downloads").apply { setReferenceCounted(false) }
        }
        if (lock?.isHeld == false) lock?.acquire(6 * 60 * 60 * 1000L)
        if (wifi == null) {
            wifi = applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
                ?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pulse:downloads")?.apply { setReferenceCounted(false) }
        }
        if (wifi?.isHeld == false) wifi?.acquire()
        if (!watching) {
            watching = true
            CoroutineScope(Dispatchers.Main).launch {
                var armed = 0L
                var pass = 0
                while (true) {
                    // the guard alarm brings the service back if Android kills it; re-armed while downloads run
                    if (SystemClock.elapsedRealtime() - armed > 30_000L) { DlGuard.arm(this@DownloadService); armed = SystemClock.elapsedRealtime() }
                    unstick()
                    withContext(Dispatchers.IO) {
                        // speeds and progress every 500 ms; notifications and the saved list every second (the system drops faster updates)
                        Downloads.tick()
                        if (pass++ % 2 == 0) { Downloads.persist(); post(nm) }
                    }
                    if (Downloads.activeCount() == 0) break
                    delay(500)
                }
                // back on the main thread: nothing runs any more, so this pass sees every end
                DlGuard.cancel(this@DownloadService)
                post(nm)
                watching = false
                if (lock?.isHeld == true) lock?.release()
                if (wifi?.isHeld == true) wifi?.release()
                // a start that came in meanwhile has a newer id and keeps the service
                stopSelfResult(lastId)
            }
        }
        // the system restarts the service after killing the process, and the running downloads go on
        return START_STICKY
    }

    /** Bytes and time of each running download's last progress. */
    private val progress = HashMap<DlTask, Pair<Long, Long>>()

    /** A running download that moved no byte for 90 s (not waiting for the network, not between retries) is restarted from its bytes. */
    private fun unstick() {
        val now = SystemClock.elapsedRealtime()
        progress.keys.retainAll(Downloads.tasks.value.toSet())
        Downloads.tasks.value.filter { it.state.value == DlState.RUNNING && !it.quiet }.forEach { t ->
            val (b, at) = progress[t] ?: (-1L to now)
            if (t.done.value != b || t.waiting || t.error.value.startsWith("retrying")) { progress[t] = t.done.value to now; return@forEach }
            if (now - at > 90_000L) {
                android.util.Log.w("Downloads", "stalled 90 s, restarting ${t.file.name}")
                progress[t] = t.done.value to now
                Downloads.pause(t); Downloads.resume(t)
            }
        }
    }

    /** Updates each download's notification and posts the end of each one that stopped since the last pass. */
    private fun post(nm: NotificationManager) {
        val list = Downloads.tasks.value
        seen.keys.filter { it !in list }.forEach { nm.cancel(it.nid); seen.remove(it) }
        list.forEach { t ->
            val st = t.state.value
            val was = seen.put(t, st)
            when {
                st == DlState.RUNNING -> nm.notify(t.nid, Downloads.runNote(this, t))
                was != DlState.RUNNING -> Unit
                st == DlState.PAUSED -> nm.notify(t.nid, Downloads.pausedNote(this, t))
                !t.quiet -> Downloads.notifyDone(t, st == DlState.DONE)
            }
        }
        nm.notify(ID_RUN, note())
    }

    private fun note(): Notification =
        NotificationCompat.Builder(this, CH_RUN).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Pulse downloads").setContentText(Downloads.summary())
            .setContentIntent(PendingIntent.getActivity(this, 0, Downloads.openPage(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setGroup(GROUP).setGroupSummary(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    companion object {
        fun start(c: Context) { runCatching { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) } }
    }
}

/** After a reboot, and on each guard alarm: brings the download list back and carries on the downloads that were running. */
class DownloadBoot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (i?.action != Intent.ACTION_BOOT_COMPLETED && i?.action != DlGuard.ACTION) return
        Downloads.load(c)
        if (Downloads.activeCount() > 0) { DownloadService.start(c); DlGuard.arm(c) }
    }
}

/** Download watchdog, always on while a download runs: a wake-up alarm every minute restarts the download service if Android stopped it. */
object DlGuard {
    const val ACTION = "com.umutk.pulse.DL_GUARD"
    private fun pi(c: Context) = PendingIntent.getBroadcast(c, 31, Intent(c, DownloadBoot::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun arm(c: Context) = runCatching { c.getSystemService(android.app.AlarmManager::class.java).setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60_000L, pi(c)) }
    fun cancel(c: Context) = runCatching { c.getSystemService(android.app.AlarmManager::class.java).cancel(pi(c)) }
}

// ---------------------------------------------------------------- the page

/** Folders offered as one tap; any other folder can be typed in. */
val QUICK_DIRS = listOf("Download", "Documents", "Pictures", "Movies", "Music")
/** Filter chips: the file kinds, by extension ("Other" takes the rest). */
private val CATEGORIES = listOf("All", "Apps", "Video", "Images", "Documents", "Other")
/** The sort menu offers these. */
private val SORTS = listOf("Date", "Size", "Name")
private val SORTS_TR = listOf("Tarih", "Boyut", "Ad")
private val CAT_ICON = mapOf("All" to "details", "Apps" to "apps", "Video" to "video", "Images" to "image", "Documents" to "document", "Other" to "more")
private val CAT_TR = mapOf("All" to "Tümü", "Apps" to "Uygulamalar", "Video" to "Video", "Images" to "Görseller", "Documents" to "Belgeler", "Other" to "Diğer")

private fun category(f: File): String = when (f.extension.lowercase()) {
    "apk", "xapk", "apks", "aab" -> "Apps"
    "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv" -> "Video"
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> "Images"
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "epub" -> "Documents"
    else -> "Other"
}

@Composable
fun DownloadsPage() {
    // no picture on this page: the chosen background shows behind the whole app
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { DownloadsContent() }
    }
}

@Composable
private fun DownloadsContent() {
    val ctx = LocalContext.current
    val list by Downloads.tasks.collectAsState()
    var link by remember { mutableStateOf("") }
    // links being looked at in the background: each one shows as a row of its own, and the field is free for the next link at once
    val pending = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val foundQueue = remember { androidx.compose.runtime.mutableStateListOf<List<String>>() }
    var findNote by remember { mutableStateOf("") }
    // set only on LinkFinder.Result.None: the page itself (not a download) is worth just looking at, not re-trying as a download
    var noLinkUrl by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<String>?>(null) }
    val findScope = androidx.compose.runtime.rememberCoroutineScope()
    val openTool = com.umutk.pulse.LocalOpenTool.current
    var cat by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(0) }
    // several rows chosen with a long press; the bar at the top deletes them all after one question
    var chosen by remember { mutableStateOf(setOf<DlTask>()) }
    var bulkAsk by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { withContext(Dispatchers.IO) { Downloads.prune() }; chosen = chosen.filter { it in Downloads.tasks.value }.toSet(); kotlinx.coroutines.delay(2500) } }
    // Android 13+ hides notifications until the user allows them; the download still runs without
    val askNotes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    val order: Comparator<DlTask> = when (sort) {
        0 -> compareByDescending<DlTask> { it.file.lastModified() }
        1 -> compareByDescending<DlTask> { it.done.value }
        else -> compareBy<DlTask> { it.file.name.lowercase() }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Downloads")
        if (bulkAsk) WinAlert(onDismissRequest = { bulkAsk = false }, warn = true,
            title = { Text(tr("Delete ${chosen.size} downloads", "${chosen.size} indirmeyi sil")) },
            text = { Text(tr("The files are deleted from the storage too.", "Dosyalar depolamadan da silinir.")) },
            confirmButton = { DlDialogBtn(tr("Delete", "Sil"), true) { val n = chosen.toList(); bulkAsk = false; chosen = emptySet(); n.forEach { runCatching { Downloads.remove(it) } }
                findScope.launch { kotlinx.coroutines.delay(1500); val left = n.count { it.file.exists() }; if (left > 0) android.widget.Toast.makeText(ctx, tr("$left files could not be deleted", "$left dosya silinemedi"), android.widget.Toast.LENGTH_LONG).show() } } },
            dismissButton = { DlDialogBtn(tr("Cancel", "Vazgeç"), false) { bulkAsk = false } })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.clickable { Downloads.pauseAll() }) { PIcon("pause", T.text, 22.dp) }
            Box(Modifier.clickable { Downloads.resumeAll() }) { PIcon("play", T.text, 22.dp) }
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.clickable { sortMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    PIcon("sort", T.text, 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(tr(SORTS[sort], SORTS_TR[sort]), color = T.sub, fontSize = 12.sp)
                }
                ThemedMenu(sortMenu, { sortMenu = false }) {
                    SORTS.forEachIndexed { i, n -> ThemedMenuItem(tr(n, SORTS_TR[i]), { sort = i; sortMenu = false }, checked = sort == i) }
                }
            }
            var catMenu by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.clickable { catMenu = true }, verticalAlignment = Alignment.CenterVertically) {
                    PIcon(CAT_ICON[cat] ?: "filter", T.text, 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(tr(cat, CAT_TR[cat] ?: cat), color = T.sub, fontSize = 12.sp)
                }
                ThemedMenu(catMenu, { catMenu = false }) {
                    CATEGORIES.forEach { c -> ThemedMenuItem(tr(c, CAT_TR[c] ?: c), { cat = c; catMenu = false }, iconKey = CAT_ICON[c], checked = cat == c) }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clickable { openTool("installer_settings") }) { PIcon("settings", T.text, 22.dp) }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(link, { link = it }, "https://", Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp).clickable {
                    // one link, or several pasted together (one per line, or run on): each one is taken, so a whole season's links go in at once
                    val urls = Regex("(?:https?://|magnet:\\?).*?(?=https?://|magnet:\\?|\\s|$)", RegexOption.IGNORE_CASE).findAll(link.trim()).map { it.value }.filter { it.length > 8 }.distinct().toList()
                    if (urls.isNotEmpty()) {
                        // the links leave the field at once and are looked at in the background, so the next ones can be pasted straight away
                        link = ""; findNote = ""; noLinkUrl = ""
                        urls.forEach { u ->
                            pending.add(u)
                            findScope.launch {
                                // a magnet link is a download of its own: it starts at once
                                if (u.startsWith("magnet:", ignoreCase = true)) { Downloads.add(ctx, u); pending.remove(u); return@launch }
                                // a file starts at once; a web page is read for its download links
                                when (val r = LinkFinder.find(u)) {
                                    is LinkFinder.Result.Direct -> Downloads.add(ctx, u)
                                    is LinkFinder.Result.Found -> { if (found == null) found = r.links else foundQueue.add(r.links) }
                                    is LinkFinder.Result.None -> { findNote = tr("No download link found on that page", "Bu sayfada indirme bağlantısı bulunamadı"); noLinkUrl = u }
                                    is LinkFinder.Result.Failed -> { findNote = r.why; Downloads.add(ctx, u) }
                                }
                                pending.remove(u)
                            }
                        }
                    }
                }) {
                    PIcon("download", T.text, 24.dp)
                }
            }
        }
        SeasonDl.Card()
        // a link still being looked at: a row with a small spinner, until its download starts (then its own row with the bar takes over)
        pending.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = T.text, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(Uri.decode(u.substringBefore('?').substringAfterLast('/')).ifBlank { Uri.parse(u).host.orEmpty() }, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Looking for the file…", "Dosya aranıyor…"), color = T.sub, fontSize = 11.sp)
                }
            }
        }
        if (findNote.isNotEmpty()) Text(findNote, color = T.sub, fontSize = 12.sp)
        if (noLinkUrl.isNotEmpty()) Row(Modifier.clickable {
            runCatching {
                ctx.startActivity(android.content.Intent().setClassName(ctx, "com.umutk.pulse.ExtraToolActivity")
                    .putExtra("route", "browser").putExtra("browser_url", noLinkUrl).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            noLinkUrl = ""; link = ""
        }, verticalAlignment = Alignment.CenterVertically) {
            PIcon("globe", Look.accent, 18.dp); Spacer(Modifier.width(6.dp))
            Text(tr("Open in Browser instead", "Bunun yerine Browser'da aç"), color = Look.accent, fontSize = 13.sp)
        }
        found?.let { links ->
            WinAlert(onDismissRequest = { found = foundQueue.removeFirstOrNull() },
                title = { Text(tr("Download links", "İndirme bağlantıları")) },
                text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(links, key = { it }) { l ->
                        Column(Modifier.fillMaxWidth().clickable { found = foundQueue.removeFirstOrNull(); Downloads.add(ctx, l) }.padding(vertical = 8.dp)) {
                            Text(Uri.decode(l.substringBefore('?').substringAfterLast('/')).ifBlank { l }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Uri.parse(l).host.orEmpty(), color = T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                } },
                confirmButton = {}, dismissButton = { PButton(tr("Cancel", "İptal")) { found = foundQueue.removeFirstOrNull() } })
        }
        if (list.isEmpty()) Text("No downloads", color = T.sub, fontSize = 13.sp)
        list.filter { cat == "All" || category(it.file) == cat }.sortedWith(order).forEach { t -> DownloadRow(t, t in chosen, chosen.isNotEmpty()) { chosen = if (t in chosen) chosen - t else chosen + t } }
        // room under the last row for the floating bar
        Spacer(Modifier.height(72.dp))
    }
    // the bar of the chosen rows floats at the bottom: it stays in reach while the list scrolls, to delete them one after the other
    if (chosen.isNotEmpty()) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth()) {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${chosen.size}", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.clickable { chosen = list.filter { cat == "All" || category(it.file) == cat }.toSet() }) { PIcon("check", T.text, 22.dp) }
                Box(Modifier.clickable { bulkAsk = true }) { PIcon("delete", Color(0xFFE5484D), 22.dp) }
                Box(Modifier.clickable { chosen = emptySet() }) { PIcon("close", T.sub, 22.dp) }
            }
        }
    }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(t: DlTask, picked: Boolean, selecting: Boolean, toggle: () -> Unit) {
    val ctx = LocalContext.current
    val done by t.done.collectAsState()
    val total by t.total.collectAsState()
    val st by t.state.collectAsState()
    val err by t.error.collectAsState()
    val speed by t.speed.collectAsState()
    val size = if (total > 0) "${Guard.gb(done)} of ${Guard.gb(total)}" else Guard.gb(done)
    val label = when (st) {
        DlState.RUNNING -> size + " · " + err.ifEmpty { rate(speed.toFloat()) }
        DlState.PAUSED -> "Paused · $size"
        DlState.DONE -> "Done · ${Guard.gb(done)}"
        DlState.FAILED -> "Failed: $err"
    }
    PCard(Modifier.fillMaxWidth().then(if (picked) Modifier.border(2.dp, com.umutk.pulse.Look.accent, RoundedCornerShape(8.dp)) else Modifier)
        .combinedClickable(onLongClick = toggle) { if (selecting) toggle() else if (st == DlState.DONE) Downloads.openFile(ctx, t.file) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.file.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = T.sub, fontSize = 12.sp)
            }
            if (st == DlState.RUNNING) Box(Modifier.padding(start = 8.dp).clickable { Downloads.pause(t) }) { PIcon("pause", T.sub, 22.dp) }
            if (st == DlState.PAUSED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("play", T.text, 22.dp) }
            if (st == DlState.FAILED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("download", T.text, 22.dp) }
            var pick by remember { mutableStateOf(false) }
            Box(Modifier.padding(start = 8.dp).clickable { pick = true }) { PIcon("folder", T.sub, 22.dp) }
            if (pick) FolderPicker(File(Downloads.dirPath)) { pick = false }
            Spacer(Modifier.width(12.dp))
            var confirmDel by remember { mutableStateOf(false) }
            Box(Modifier.clickable { confirmDel = true }) { PIcon("delete", T.sub, 22.dp) }
            if (confirmDel) WinAlert(onDismissRequest = { confirmDel = false }, warn = true,
                title = { Text(tr("Delete download", "İndirmeyi sil")) },
                text = { Text(t.file.name) },
                confirmButton = { DlDialogBtn(tr("Delete", "Sil"), true) { confirmDel = false; Downloads.remove(t) } },
                dismissButton = { DlDialogBtn(tr("Cancel", "Vazgeç"), false) { confirmDel = false } })
        }
        if (st == DlState.RUNNING || st == DlState.PAUSED) BarMeter(if (total > 0) done.toFloat() / total else 0f, T.text, Modifier.padding(top = 6.dp), h = 6.dp)
    }
}

/** A button of a download dialog: the delete one is red with white text, the other has the window's own text colour, so both read in every look. */
@Composable
private fun DlDialogBtn(text: String, danger: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.clip(shape).background(if (danger) Color(0xFFC42B1C) else T.text.copy(alpha = .12f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (danger) Color.White else T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Picks the folder new downloads go to: browse shared storage, then "Use this folder". */
@Composable
private fun FolderPicker(start: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val top = remember { Environment.getExternalStorageDirectory() }
    var cur by remember { mutableStateOf(start) }
    val dirs = remember(cur) { cur.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.sortedBy { it.name.lowercase() }.orEmpty() }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(cur.path.removePrefix(top.path).ifEmpty { "/" }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cur != top) Row(Modifier.fillMaxWidth().clickable { cur = cur.parentFile ?: top }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("chevron", T.text, 20.dp, Modifier.rotate(180f))
                    Spacer(Modifier.width(12.dp))
                    Text("..", color = T.text, fontSize = 14.sp)
                }
                dirs.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { cur = d }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("folder", T.text, 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(d.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (dirs.isEmpty()) Text("No folders here", color = T.sub, fontSize = 13.sp)
            }
        },
        confirmButton = { androidx.compose.foundation.layout.Row(Modifier.clickable { Downloads.setDir(ctx, cur.path); onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { com.umutk.pulse.PIcon("folder", com.umutk.pulse.T.text, 24.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)); com.umutk.pulse.PIcon("check", com.umutk.pulse.Look.accent, 24.dp) } },
        dismissButton = { TextButton(onClick = { Downloads.openFolder(ctx, cur) }) { Text("Open in files") } },
    )
}

/** Text formats Android often does not know (m3u, lrc, srt, ...) open as text. */
private val textExts = setOf("txt", "lrc", "srt", "vtt", "ass", "md", "log", "json", "xml", "csv", "tsv", "ini", "conf", "cfg",
    "yml", "yaml", "properties", "sh", "kt", "java", "py", "js", "ts", "html", "htm", "css", "gradle", "toml", "sql", "nfo", "bat")

/** The MIME type a file opens with: the known type, else text/plain for text formats, else sniffed text, else any. */
fun fileMime(f: File): String {
    val ext = f.extension.lowercase()
    if (ext == "m3u" || ext == "m3u8") return "audio/x-mpegurl"
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    if (ext in textExts || looksLikeText(f)) return "text/plain"
    return "*/*"
}

/** True when the first 512 bytes hold no NUL byte, the usual sign of plain text. */
private fun looksLikeText(f: File): Boolean = runCatching {
    f.inputStream().use { i ->
        val b = ByteArray(512)
        val n = i.read(b)
        n > 0 && (0 until n).none { b[it] == 0.toByte() }
    }
}.getOrDefault(false)
