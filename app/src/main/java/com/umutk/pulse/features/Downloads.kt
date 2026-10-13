package com.umutk.pulse.features

import com.umutk.pulse.WinAlert
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.TextButton
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
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
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.rate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
private const val PARTS = 6
/** Smaller files are one connection; splitting them costs more than it saves. */
private const val MIN_SPLIT = 4L shl 20
private const val BUF = 64 * 1024
private const val CH_RUN = "downloads"
private const val CH_DONE = "downloads_done"
private const val ID_RUN = 7
/** Speed limit when the switch on the Downloads page is on, in KB/s. */
private const val LIMIT_KBS = 512
private const val APK_MIME = "application/vnd.android.package-archive"

enum class DlState { RUNNING, PAUSED, DONE, FAILED }

/** One download: where it goes, how far it has got, its speed, and how it ended. */
class DlTask(val url: String, val file: File) {
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

    /** Bytes a split download has already written, from its part positions. */
    fun partBytes(): Long = partPos?.let { p -> val step = partStep(total.value); p.indices.sumOf { p[it] - it * step } } ?: 0L
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
    /** true: downloads are held to [LIMIT_KBS]; kept between runs */
    var limitOn by mutableStateOf(false)
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun defaultDir(): String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

    fun load(ctx: Context) {
        app = ctx.applicationContext
        val p = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
        dirPath = p.getString("dlDir", null) ?: defaultDir()
        directOpen = p.getBoolean("openDirect", false)
        limitOn = p.getBoolean("dlLimit", false)
        // every download kept from earlier runs: finished ones stay finished, the rest are shown as interrupted
        val saved = p.getStringSet("dl_list", emptySet()).orEmpty().sorted()
        tasks.value = saved.mapNotNull { row ->
            val cols = row.split('\t')
            if (cols.size < 4) return@mapNotNull null
            val f = File(cols[3])
            DlTask(cols[2], f).also { t ->
                t.resumable = cols.getOrNull(4) == "1"
                t.partPos = cols.getOrNull(5)?.takeIf { it.isNotBlank() }?.split(',')?.map { it.toLong() }?.toLongArray()
                t.total.value = cols.getOrNull(6)?.toLongOrNull() ?: -1L
                if (cols[1] == DlState.DONE.name && f.isFile) { t.state.value = DlState.DONE; t.done.value = f.length(); t.total.value = f.length() }
                else { t.state.value = DlState.FAILED; t.error.value = "interrupted"; t.done.value = if (t.partPos != null) t.partBytes() else if (f.isFile) f.length() else 0L }
            }
        }
    }

    /** Saves the list (in order) so the downloads are still there after the app restarts. */
    internal fun persist() {
        val c = app ?: return
        val rows = tasks.value.mapIndexed { i, t -> "%04d\t%s\t%s\t%s\t%d\t%s\t%d".format(i, t.state.value.name, t.url, t.file.path, if (t.resumable) 1 else 0, t.partPos?.joinToString(",").orEmpty(), t.total.value) }.toSet()
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putStringSet("dl_list", rows).apply()
    }

    fun setDirectOpen(ctx: Context, v: Boolean) {
        directOpen = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("openDirect", v).apply()
    }

    fun setLimit(ctx: Context, v: Boolean) {
        limitOn = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlLimit", v).apply()
    }

    fun setDir(ctx: Context, path: String) {
        dirPath = path
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("dlDir", path).apply()
    }

    fun add(ctx: Context, url: String) {
        app = ctx.applicationContext
        val dir = File(dirPath).apply { mkdirs() }
        val name = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }?.replace(Regex("[/\\\\:*?\"<>|]"), "_") ?: "download"
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, "$n-$name").also { n++ }
        val t = DlTask(url, file)
        tasks.value = tasks.value + t
        persist()
        DownloadService.start(ctx)
        t.job = scope.launch { t.fetch() }
    }

    /** Stops a download (its partial file goes too) or removes a finished one together with its file. */
    fun remove(t: DlTask) {
        t.cancelled = true
        tasks.value = tasks.value - t
        t.file.delete()
        persist()
    }

    /** Holds a running download: the transfer stops and the partial file stays, so [resume] continues it. */
    fun pause(t: DlTask) {
        if (t.state.value != DlState.RUNNING) return
        t.paused = true
        t.state.value = DlState.PAUSED
        persist()
    }

    fun pauseAll() = tasks.value.forEach { pause(it) }

    fun resumeAll() = tasks.value.filter { it.state.value == DlState.PAUSED }.forEach { resume(it) }

    /** Continues a download that stopped or was paused: a single copy picks up from its length, anything else starts again. */
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

    /** Once a second: the speed of each running download. */
    fun tick() {
        tasks.value.forEach { t ->
            val d = t.done.value
            t.speed.value = if (t.state.value == DlState.RUNNING) d - t.lastDone else 0L
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

    /** Posts a notification when a download ends; tapping it opens the Downloads page. */
    internal fun notifyDone(t: DlTask, ok: Boolean) {
        val c = app ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        // a finished APK opens the installer window from the notification; anything else opens the Downloads page
        val apk = if (ok && t.file.extension.equals("apk", true)) runCatching {
            Intent(c, InstallActivity::class.java).setDataAndType(FileProvider.getUriForFile(c, "${c.packageName}.files", t.file), APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull() else null
        val open = PendingIntent.getActivity(c, t.hashCode(), apk ?: openPage(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CH_DONE).setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (ok) "Download finished" else "Download failed")
            .setContentText(if (ok) t.file.name else t.error.value)
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(t.hashCode(), n)
    }

    /** Opens a file with the app that handles it (an APK goes to the installer). */
    fun openFile(ctx: Context, f: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            if (f.extension.equals("apk", true)) {
                ctx.startActivity(Intent(ctx, InstallActivity::class.java).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            val mime = fileMime(f)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.clipData = ClipData.newRawUri("", uri)
            if (directOpen) {
                try {
                    ctx.startActivity(Intent(view).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    ctx.startActivity(Intent.createChooser(view, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else {
                // ask: the phone's own chooser shows every app that can open the file
                ctx.startActivity(Intent.createChooser(view, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
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

private const val RETRIES = 6

/** The server did not answer a range request, so a split cannot go on. */
private class NoRange(msg: String) : IOException(msg)

private fun open(url: String, range: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 20_000
        readTimeout = 30_000
        // some hosts refuse the default Java agent
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        setRequestProperty("Range", range)
        // a compressed body would not match the byte ranges
        setRequestProperty("Accept-Encoding", "identity")
    }

/** Tries the transfer again after a failure, continuing from the file on disk when it can. Gives up after [RETRIES] tries. */
private suspend fun DlTask.fetch() {
    var tries = 0
    while (!cancelled && !paused) {
        try {
            // false: paused or removed; the partial file stays for the next resume
            if (!transfer() || cancelled) return
            state.value = DlState.DONE
            Downloads.persist()
            Downloads.notifyDone(this, true)
            return
        } catch (e: Exception) {
            if (cancelled || paused) return
            tries++
            // no ranges from the server: the parts are dropped and the file starts again on one connection
            if (e is NoRange) partPos = null
            // a split download keeps its part positions, so only a single copy falls back to one connection
            if (partPos == null) noSplit = true
            if (tries >= RETRIES) {
                error.value = e.message ?: e.javaClass.simpleName
                state.value = DlState.FAILED
                Downloads.persist()
                Downloads.notifyDone(this, false)
                return
            }
            error.value = "retrying (${tries}/${RETRIES - 1}): ${e.message ?: e.javaClass.simpleName}"
            delay(4000L * tries)
        }
    }
}

/** true when the whole file arrived; false when it was paused or removed on the way. */
private suspend fun DlTask.transfer(): Boolean {
    // a single sequential copy can resume from its length; a split download resumes from its part positions
    val have = if (resumable && file.isFile) file.length() else 0L
    val first = open(url, if (have > 0) "bytes=$have-" else "bytes=0-")
    val code = first.responseCode
    if (code !in 200..299) throw IOException("HTTP $code")
    val ranged = code == 206
    val resumed = ranged && have > 0
    val size = if (ranged) first.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else first.contentLengthLong
    // saved part positions count only for the same file, and only when the server answers ranges
    if (partPos != null && (!ranged || size != total.value)) partPos = null
    total.value = size
    return if (partPos != null || (!resumed && ranged && size > MIN_SPLIT && !noSplit)) {
        first.disconnect()
        split(size)
    } else {
        first.inputStream.use { copy(it, append = resumed, start = if (resumed) have else 0L) }
    }
}

/** Writes each range from its own connection at its own place in the file. [partPos] keeps where each part has got to, so a resume goes on from there. */
private suspend fun DlTask.split(total: Long): Boolean = coroutineScope {
    resumable = false
    val step = partStep(total)
    val fresh = partPos == null
    val pos = partPos ?: LongArray(PARTS) { it * step }.also { partPos = it }
    FileChannel.open(file.toPath(), if (fresh) setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        else setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE)).use { ch ->
        done.value = pos.indices.sumOf { pos[it] - it * step }
        (0 until PARTS).map { i ->
            async(Dispatchers.IO) {
                val end = minOf(total, (i + 1) * step) - 1
                if (pos[i] > end) return@async
                val c = open(url, "bytes=${pos[i]}-$end")
                if (c.responseCode != 206) throw NoRange("the server does not split the file (HTTP ${c.responseCode})")
                c.inputStream.use { ins ->
                    val buf = ByteArray(BUF)
                    while (!cancelled && !paused && pos[i] <= end) {
                        val n = ins.read(buf)
                        // the stream ended before the part did: retry, the bytes so far are kept
                        if (n < 0) throw IOException("connection closed early")
                        val bb = ByteBuffer.wrap(buf, 0, n)
                        while (bb.hasRemaining()) ch.write(bb, pos[i] + bb.position())
                        pos[i] += n
                        done.update { it + n }
                        throttle(n)
                    }
                }
            }
        }.awaitAll()
    }
    !cancelled && !paused
}

/** Sleeps when this transfer is ahead of [LIMIT_KBS]; the bytes count from the moment the switch is on. */
private fun DlTask.throttle(n: Int) {
    if (!Downloads.limitOn) { gateBytes.set(0); gateStart = 0L; return }
    val now = System.currentTimeMillis()
    if (gateStart == 0L) gateStart = now
    val due = gateBytes.addAndGet(n.toLong()) * 1000 / (LIMIT_KBS * 1024L)
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
            if (n < 0) return true
            out.write(buf, 0, n)
            done.update { it + n }
            throttle(n)
        }
    }
    return false
}

/** Keeps the app in the foreground while downloads run, so they are not stopped when Pulse leaves the screen. Its notification shows the speed. */
class DownloadService : Service() {
    private var watching = false
    /** keeps the CPU awake while a download runs, so the screen going off does not stop it */
    private var lock: android.os.PowerManager.WakeLock? = null
    /** keeps Wi-Fi at full speed and awake while the screen is off (the CPU lock alone does not) */
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "Downloads", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_RUN, note(Downloads.summary()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID_RUN, note(Downloads.summary()))
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
            CoroutineScope(Dispatchers.Default).launch {
                while (Downloads.activeCount() > 0) {
                    Downloads.tick()
                    // shortcut: progress is saved once a second, so a kill loses at most that much
                    Downloads.persist()
                    nm.notify(ID_RUN, note(Downloads.summary()))
                    delay(1000)
                }
                if (lock?.isHeld == true) lock?.release()
                if (wifi?.isHeld == true) wifi?.release()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun note(text: String): Notification =
        NotificationCompat.Builder(this, CH_RUN).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Pulse downloads").setContentText(text)
            .setProgress(100, Downloads.percent(), false)
            .setContentIntent(PendingIntent.getActivity(this, 0, Downloads.openPage(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    companion object {
        fun start(c: Context) { runCatching { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) } }
    }
}

// ---------------------------------------------------------------- the page

/** Folders offered as one tap; any other folder can be typed in. */
private val QUICK_DIRS = listOf("Download", "Documents", "Pictures", "Movies", "Music")
/** Filter chips: the file kinds, by extension ("Other" takes the rest). */
private val CATEGORIES = listOf("All", "Apps", "Video", "Images", "Documents", "Other")
/** The sort icon cycles through these. */
private val SORTS = listOf("Date", "Size", "Name")

private fun category(f: File): String = when (f.extension.lowercase()) {
    "apk", "xapk", "apks", "aab" -> "Apps"
    "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv" -> "Video"
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> "Images"
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "epub" -> "Documents"
    else -> "Other"
}

@Composable
fun DownloadsPage() {
    // no background on the page: only a strip of the chosen picture at its top, when the scope is "downloads"
    val pic = Backdrop.image
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        if (pic != null && Backdrop.scope == "downloads") androidx.compose.foundation.Image(pic, null, Modifier.fillMaxWidth().height(120.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = androidx.compose.ui.Alignment.TopCenter)
        Box(Modifier.weight(1f).fillMaxWidth()) { DownloadsContent() }
    }
}

@Composable
private fun DownloadsContent() {
    val ctx = LocalContext.current
    val list by Downloads.tasks.collectAsState()
    var link by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(Downloads.dirPath) }
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
            Row(Modifier.clickable { sort = (sort + 1) % SORTS.size }, verticalAlignment = Alignment.CenterVertically) {
                PIcon("sort", T.text, 22.dp)
                Spacer(Modifier.width(4.dp))
                Text(SORTS[sort], color = T.sub, fontSize = 12.sp)
            }
            PChip(Downloads.limitOn, "Limit $LIMIT_KBS KB/s") { Downloads.setLimit(ctx, !Downloads.limitOn) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.forEach { c -> PChip(cat == c, c) { cat = c } }
        }
        PCard {
            Text("Save to ${Downloads.dirPath.removePrefix(Environment.getExternalStorageDirectory().path).ifEmpty { "/" }}", color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                QUICK_DIRS.forEach { name ->
                    val p = "${Environment.getExternalStorageDirectory().path}/$name"
                    PChip(Downloads.dirPath == p, name) { Downloads.setDir(ctx, p); folder = p }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(folder, { folder = it }, "/storage/emulated/0/...", Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp).clickable {
                    val d = File(folder.trim())
                    if (d.isDirectory || d.mkdirs()) Downloads.setDir(ctx, d.path) else Toast.makeText(ctx, "Cannot use this folder", Toast.LENGTH_SHORT).show()
                }) { PIcon("folder", Look.accent, 24.dp) }
            }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(link, { link = it }, "https://", Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp).clickable { if (link.startsWith("http")) { Downloads.add(ctx, link.trim()); link = "" } }) {
                    PIcon("download", Look.accent, 24.dp)
                }
            }
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
        DlState.RUNNING -> size + " · " + rate(speed.toFloat())
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
            if (st == DlState.PAUSED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("play", Look.accent, 22.dp) }
            if (st == DlState.FAILED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("download", Look.accent, 22.dp) }
            var pick by remember { mutableStateOf(false) }
            Box(Modifier.padding(start = 8.dp).clickable { pick = true }) { PIcon("folder", T.sub, 22.dp) }
            if (pick) FolderPicker(File(Downloads.dirPath)) { pick = false }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clickable { Downloads.remove(t) }) { PIcon("delete", T.sub, 22.dp) }
        }
        if (st == DlState.RUNNING || st == DlState.PAUSED) BarMeter(if (total > 0) done.toFloat() / total else 0f, Look.accent, Modifier.padding(top = 6.dp), h = 6.dp)
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
