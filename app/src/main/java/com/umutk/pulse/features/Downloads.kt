package com.umutk.pulse.features

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

/** Parallel connections for a file the server can split. */
private const val PARTS = 6
/** Smaller files are one connection; splitting them costs more than it saves. */
private const val MIN_SPLIT = 4L shl 20
private const val BUF = 64 * 1024
private const val CH_RUN = "downloads"
private const val CH_DONE = "downloads_done"
private const val ID_RUN = 7

enum class DlState { RUNNING, DONE, FAILED }

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
}

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
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun defaultDir(): String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

    fun load(ctx: Context) {
        app = ctx.applicationContext
        dirPath = ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getString("dlDir", null) ?: defaultDir()
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
        DownloadService.start(ctx)
        scope.launch { t.fetch() }
    }

    /** Stops a download (its partial file goes too) or removes a finished one together with its file. */
    fun remove(t: DlTask) {
        t.cancelled = true
        tasks.value = tasks.value - t
        t.file.delete()
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

    fun summary(): String {
        val run = tasks.value.filter { it.state.value == DlState.RUNNING }
        if (run.isEmpty()) return "Downloading in the background"
        return "${run.size} running · ${rate(run.sumOf { it.speed.value }.toFloat())}"
    }

    /** Posts a notification when a download ends; tapping it opens the Downloads page. */
    internal fun notifyDone(t: DlTask, ok: Boolean) {
        val c = app ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, t.hashCode(), openPage(c), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            val mime = if (f.extension.equals("apk", true)) "application/vnd.android.package-archive"
                else MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            view.clipData = ClipData.newRawUri("", uri)
            try {
                ctx.startActivity(Intent(view).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
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

private fun open(url: String, range: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Range", range)
        // a compressed body would not match the byte ranges
        setRequestProperty("Accept-Encoding", "identity")
    }

private suspend fun DlTask.fetch() {
    try {
        val first = open(url, "bytes=0-")
        val code = first.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        val total = if (code == 206) first.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L else first.contentLengthLong
        this.total.value = total
        if (code == 206 && total > MIN_SPLIT) {
            first.disconnect()
            split(total)
        } else {
            first.inputStream.use { copy(it) }
        }
        if (!cancelled) {
            state.value = DlState.DONE
            Downloads.notifyDone(this, true)
        }
    } catch (e: Exception) {
        if (!cancelled) {
            error.value = e.message ?: e.javaClass.simpleName
            state.value = DlState.FAILED
            Downloads.notifyDone(this, false)
        }
    }
}

/** Writes each range from its own connection at its own place in the file. */
private suspend fun DlTask.split(total: Long) = coroutineScope {
    FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { ch ->
        val step = (total + PARTS - 1) / PARTS
        (0 until PARTS).map { i ->
            async(Dispatchers.IO) {
                val from = i * step
                val to = minOf(total, from + step) - 1
                if (from <= to) {
                    val c = open(url, "bytes=$from-$to")
                    if (c.responseCode != 206) throw IOException("the server did not split the file (HTTP ${c.responseCode})")
                    var pos = from
                    c.inputStream.use { ins ->
                        val buf = ByteArray(BUF)
                        while (!cancelled) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            val bb = ByteBuffer.wrap(buf, 0, n)
                            while (bb.hasRemaining()) ch.write(bb, pos + bb.position())
                            pos += n
                            done.update { it + n }
                        }
                    }
                }
            }
        }.awaitAll()
    }
}

private fun DlTask.copy(ins: InputStream) {
    FileOutputStream(file).use { out ->
        val buf = ByteArray(BUF)
        while (!cancelled) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done.update { it + n }
        }
    }
}

/** Keeps the app in the foreground while downloads run, so they are not stopped when Pulse leaves the screen. Its notification shows the speed. */
class DownloadService : Service() {
    private var watching = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "Downloads", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_RUN, note(Downloads.summary()), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID_RUN, note(Downloads.summary()))
        if (!watching) {
            watching = true
            CoroutineScope(Dispatchers.Default).launch {
                while (Downloads.activeCount() > 0) {
                    Downloads.tick()
                    nm.notify(ID_RUN, note(Downloads.summary()))
                    delay(1000)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun note(text: String): Notification =
        NotificationCompat.Builder(this, CH_RUN).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Pulse downloads").setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, Downloads.openPage(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    companion object {
        fun start(c: Context) { runCatching { ContextCompat.startForegroundService(c, Intent(c, DownloadService::class.java)) } }
    }
}

// ---------------------------------------------------------------- the page

/** Folders offered as one tap; any other folder can be typed in. */
private val QUICK_DIRS = listOf("Download", "Documents", "Pictures", "Movies", "Music")

@Composable
fun DownloadsPage() {
    val ctx = LocalContext.current
    val list by Downloads.tasks.collectAsState()
    var link by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(Downloads.dirPath) }
    // Android 13+ hides notifications until the user allows them; the download still runs without
    val askNotes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askNotes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Downloads")
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
        list.asReversed().forEach { DownloadRow(it) }
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
    val label = when (st) {
        DlState.RUNNING -> (if (total > 0) "${Guard.gb(done)} of ${Guard.gb(total)}" else Guard.gb(done)) + " · " + rate(speed.toFloat())
        DlState.DONE -> "Done · ${Guard.gb(done)}"
        DlState.FAILED -> "Failed: $err"
    }
    PCard(Modifier.fillMaxWidth().clickable(enabled = st == DlState.DONE) { Downloads.openFile(ctx, t.file) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.file.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = T.sub, fontSize = 12.sp)
            }
            Box(Modifier.padding(start = 8.dp).clickable { Downloads.openFolder(ctx, t.file.parentFile ?: t.file) }) { PIcon("folder", T.sub, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clickable { Downloads.remove(t) }) { PIcon("delete", T.sub, 22.dp) }
        }
        if (st == DlState.RUNNING) BarMeter(if (total > 0) done.toFloat() / total else 0f, Look.accent, Modifier.padding(top = 6.dp), h = 6.dp)
    }
}
