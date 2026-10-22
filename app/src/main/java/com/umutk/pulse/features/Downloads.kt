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
private const val PARTS = 4
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
    fun partBytes(): Long = partPos?.let { p -> val step = partStep(total.value); p.indices.sumOf { p[it] - it * step } } ?: 0L

    /** What If-Range sends; a weak ETag is not allowed there. */
    fun validator(): String? = etag?.takeUnless { it.startsWith("W/") } ?: lastMod
}

/** Size of each range of a split download. */
private fun partStep(total: Long) = (total + PARTS - 1) / PARTS

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

    fun add(ctx: Context, url: String) {
        val c = ctx.applicationContext
        app = c
        scope.launch {
            val dir = File(dirPath)
            val name = cleanName(Uri.parse(url).lastPathSegment) ?: "download"
            val t = try { DlTask(url, reserve(dir, name)) } catch (e: Exception) {
                DlTask(url, File(dir, name)).apply { state.value = DlState.FAILED; error.value = "cannot write to ${dir.path}: ${e.message ?: e.javaClass.simpleName}" }
            }
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
        scope.launch { running?.join(); t.file.delete() }
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
        PendingIntent.getForegroundService(c, t.nid * 31 + act.hashCode(), Intent(c, DownloadService::class.java).setAction(act).putExtra("id", t.nid),
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
        nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
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

/** The name from a Content-Disposition header: filename* (RFC 5987) first, else filename. */
private fun dispositionName(h: String?): String? {
    if (h == null) return null
    Regex("filename\\*\\s*=\\s*[^']*'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(h)?.let { return Uri.decode(it.groupValues[1].trim().trim('"')) }
    return Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.trim()
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
            readTimeout = 30_000
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
        val ranged = code == 206
        val size = if (ranged) first.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else first.contentLengthLong
        val tag = first.getHeaderField("ETag")
        val mod = first.getHeaderField("Last-Modified")
        // the saved bytes only count for the same file: same validators, same size
        if (started && ranged && ((etag != null && tag != etag) || (lastMod != null && mod != lastMod) || (total.value > 0 && size != total.value))) throw Changed()
        etag = tag
        lastMod = mod
        if (!started && !quiet) rename(dispositionName(first.getHeaderField("Content-Disposition")) ?: Uri.decode(first.url.path).takeIf { first.url.toString() != url })
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
    val step = partStep(total)
    val fresh = partPos == null
    val pos = partPos ?: LongArray(PARTS) { it * step }.also { partPos = it }
    val tag = validator()
    FileChannel.open(file.toPath(), if (fresh) setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        else setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE)).use { ch ->
        done.value = pos.indices.sumOf { pos[it] - it * step }
        (0 until PARTS).map { i ->
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
        nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH_RUN, "Downloads", NotificationManager.IMPORTANCE_LOW))
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
    var finding by remember { mutableStateOf(false) }
    var findNote by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<String>?>(null) }
    val findScope = androidx.compose.runtime.rememberCoroutineScope()
    val openTool = com.umutk.pulse.LocalOpenTool.current
    var cat by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf(0) }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Downloads")
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
                Box(Modifier.padding(start = 8.dp).clickable(enabled = !finding) {
                    if (link.startsWith("http")) {
                        val u = link.trim(); finding = true; findNote = ""
                        findScope.launch {
                            // a file starts at once; a web page is read for its download links
                            when (val r = LinkFinder.find(u)) {
                                is LinkFinder.Result.Direct -> { Downloads.add(ctx, u); link = "" }
                                is LinkFinder.Result.Found -> found = r.links
                                is LinkFinder.Result.None -> findNote = tr("No download link found on that page", "Bu sayfada indirme bağlantısı bulunamadı")
                                is LinkFinder.Result.Failed -> { findNote = r.why; Downloads.add(ctx, u); link = "" }
                            }
                            finding = false
                        }
                    }
                }) {
                    if (finding) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = T.text, strokeWidth = 2.dp) else PIcon("download", T.text, 24.dp)
                }
            }
        }
        if (findNote.isNotEmpty()) Text(findNote, color = T.sub, fontSize = 12.sp)
        found?.let { links ->
            WinAlert(onDismissRequest = { found = null },
                title = { Text(tr("Download links", "İndirme bağlantıları")) },
                text = { androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(links, key = { it }) { l ->
                        Column(Modifier.fillMaxWidth().clickable { found = null; link = ""; Downloads.add(ctx, l) }.padding(vertical = 8.dp)) {
                            Text(Uri.decode(l.substringBefore('?').substringAfterLast('/')).ifBlank { l }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Uri.parse(l).host.orEmpty(), color = T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                } },
                confirmButton = {}, dismissButton = { PButton(tr("Cancel", "İptal")) { found = null } })
        }
        if (list.isEmpty()) Text("No downloads", color = T.sub, fontSize = 13.sp)
        list.filter { cat == "All" || category(it.file) == cat }.sortedWith(order).forEach { DownloadRow(it) }
    }
}

@Composable
private fun DownloadRow(t: DlTask) {
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
    PCard(Modifier.fillMaxWidth().clickable(enabled = st == DlState.DONE) { Downloads.openFile(ctx, t.file) }) {
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
                confirmButton = { Text(tr("Delete", "Sil"), Modifier.clickable { confirmDel = false; Downloads.remove(t) }.padding(8.dp)) },
                dismissButton = { Text(tr("Cancel", "Vazgeç"), Modifier.clickable { confirmDel = false }.padding(8.dp)) })
        }
        if (st == DlState.RUNNING || st == DlState.PAUSED) BarMeter(if (total > 0) done.toFloat() / total else 0f, T.text, Modifier.padding(top = 6.dp), h = 6.dp)
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
        confirmButton = { TextButton(onClick = { Downloads.setDir(ctx, cur.path); onDismiss() }) { Text("Use this folder") } },
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
