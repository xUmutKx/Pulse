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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.umutk.pulse.PCheck
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
class DlTask(url: String, file: File) {
    /** the link: a torrent with no peers gives way to the next of [alts] */
    @Volatile var url: String = url
    /** other streams of the same episode or movie, closest in size first: tried in turn when a torrent finds no one to download from */
    val alts: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
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
    /** Removing a finished download only takes it off the list: the file stays on the phone. Off at first. */
    var keepFiles by mutableStateOf(false)
    /** A torrent that finds nobody to download from within 15 seconds gives way to another stream of the episode (a bigger one is fine, never under 200 MB). On at first. */
    var autoSwitch by mutableStateOf(true)
    /** On a Wi-Fi that blocks torrents (a school, a hotel), the torrents go over mobile data while they run (the rest of the phone keeps the Wi-Fi). */
    var torrentCell by mutableStateOf(false)
    /** Automatic network for torrents (on at first): on Wi-Fi that gives no peers (no DHT nodes a minute after the engine started, or a sign-in page / no internet) the torrents go over mobile data by themselves; [torrentCell] forces it always. */
    var torrentAuto by mutableStateOf(true)
    /** Encrypted torrent connections only (on at first): a school or hotel network that spots and blocks BitTorrent by its traffic lets an encrypted one through more often. Fewer peers can talk this way. */
    var torrentEnc by mutableStateOf(true)
    /** Set when this Wi-Fi was seen to give no peers: the torrents use mobile data until the network changes. */
    @Volatile var wifiTorrentBad = false
    /** Magnet links, and Stremio's local links that become magnets: off by default, they start only when this is on. */
    var magnetLinks by mutableStateOf(false)
    /** The address of a local torrent server (Stremio's, or another app's that serves torrents the same way): empty = the torrent engine in Pulse; Series and movies sends its torrents there when set. */
    var localServer by mutableStateOf("")
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
        keepFiles = p.getBoolean("dlKeepFiles", false)
        autoSwitch = p.getBoolean("dlAutoSwitch", true)
        torrentCell = p.getBoolean("dlTorrentCell", false)
        torrentAuto = p.getBoolean("dlTorrentAuto", true)
        torrentEnc = p.getBoolean("dlTorrentEnc", true)
        magnetLinks = p.getBoolean("dlMagnet", false)
        localServer = p.getString("dlLocalServer", "") ?: ""
        speed = p.getInt("dlSpeed", 0).coerceIn(0, SPEED_KBS.size - 1)
        // the activity, the service and the boot receiver all call this; the list is read once per process
        if (loaded) return
        loaded = true
        watchNetwork(ctx.applicationContext)
        scope.launch { delay(5000); sweepTorrents() }
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
                cols.getOrNull(9)?.split(' ')?.filter { it.isNotBlank() }?.let { t.alts.addAll(it) }
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
                override fun onLost(n: Network) { online.value = false; wifiTorrentBad = false }
            })
        }
    }

    /** Saves the list (in order) so the downloads are still there after the app restarts. */
    internal fun persist() {
        val c = app ?: return
        fun clean(s: String?) = s.orEmpty().replace('\t', ' ')
        val rows = tasks.value.filter { !it.quiet }.mapIndexed { i, t -> "%04d\t%s\t%s\t%s\t%d\t%s\t%d\t%s\t%s\t%s".format(i, t.state.value.name, t.url, t.file.path, if (t.resumable) 1 else 0, t.partPos?.joinToString(",").orEmpty(), t.total.value, clean(t.etag), clean(t.lastMod), synchronized(t.alts) { t.alts.joinToString(" ") }) }.toSet()
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putStringSet("dl_list", rows).apply()
        // a copy of the list for YTXTube's Downloads page (it runs in its own process and cannot read this list): name, state, bytes done, total, speed, path, error
        runCatching {
            val lines = tasks.value.filter { !it.quiet }.joinToString("\n") { t ->
                listOf(t.file.name, t.state.value.name, t.done.value, t.total.value, t.speed.value, t.file.path, t.error.value.take(80)).joinToString("\t") { it.toString().replace(Regex("[\\t\\r\\n]"), " ") }
            }
            val tmp = File(c.filesDir, "dl_snapshot.tsv.tmp"); tmp.writeText(lines); tmp.renameTo(File(c.filesDir, "dl_snapshot.tsv"))
        }
    }

    fun setLocalServer(ctx: Context, v: String) {
        localServer = v.trim()
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("dlLocalServer", localServer).apply()
    }

    fun setMagnetLinks(ctx: Context, v: Boolean) {
        magnetLinks = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlMagnet", v).apply()
    }

    fun setTorrentAuto(ctx: Context, v: Boolean) {
        torrentAuto = v; if (!v) wifiTorrentBad = false
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlTorrentAuto", v).apply()
    }

    fun setTorrentEnc(ctx: Context, v: Boolean) {
        torrentEnc = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlTorrentEnc", v).apply()
        // a running session keeps its old settings: it is let go, the next torrent starts a new one with the new
        TorrentEngine.resetSession()
    }

    fun setTorrentCell(ctx: Context, v: Boolean) {
        torrentCell = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlTorrentCell", v).apply()
    }

    fun onWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    /** The Wi-Fi the phone is on asks for a sign-in page first (a captive portal) or has no working internet: nothing reaches the torrent peers through it. */
    fun wifiBlocked(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) &&
            (nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) || !nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }.getOrDefault(false)

    private var cellCb: android.net.ConnectivityManager.NetworkCallback? = null
    private var cellUsers = 0

    /** While a torrent runs with [torrentCell] on and the phone is on Wi-Fi: the app's connections go over mobile data. Undone by [releaseCell]. */
    @Synchronized fun holdCell(ctx: Context, force: Boolean = false): Boolean {
        if (!torrentCell && !force) return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val on = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!on) return false
        cellUsers++
        if (cellCb != null) return true
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { runCatching { cm.bindProcessToNetwork(network) } }
            override fun onLost(network: android.net.Network) { runCatching { cm.bindProcessToNetwork(null) } }
        }
        cellCb = cb
        runCatching { cm.requestNetwork(android.net.NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb) }
        return true
    }

    @Synchronized fun releaseCell(ctx: Context) {
        if (cellUsers > 0) cellUsers--
        if (cellUsers > 0) return
        val cb = cellCb ?: return
        cellCb = null
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        runCatching { cm.bindProcessToNetwork(null) }
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    fun setAutoSwitch(ctx: Context, v: Boolean) {
        autoSwitch = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlAutoSwitch", v).apply()
    }

    fun setKeepFiles(ctx: Context, v: Boolean) {
        keepFiles = v
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dlKeepFiles", v).apply()
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

    /** Where the torrent engine keeps its working folders: the app's own storage on the big volume, never the Downloads folder (nothing is left lying there). */
    internal fun torrentWork(c: Context): File = File(c.getExternalFilesDir(null) ?: c.filesDir, "torrent-work").also { it.mkdirs() }

    /**
     * The collector: working folders of torrents (".torrent-<hash>") that no download wants any more are deleted, in the app's own storage and in the Downloads folder (where older
     * versions left them). A folder of a download still waiting (paused, failed) stays, and one found in Downloads is moved to the private place. It does nothing when a download's
     * torrent cannot be told from its link (a .torrent file), so it never takes away what is still wanted.
     */
    fun sweepTorrents() {
        val c = app ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val keep = HashSet<String>()
                for (t in tasks.value) {
                    if (t.state.value == DlState.DONE) continue
                    val u = t.url
                    if (!u.startsWith("magnet:", ignoreCase = true)) { if (isTorrentFileUrl(u)) return@runCatching else continue }
                    keep += Regex("xt=urn:btih:([0-9a-fA-F]{40})", RegexOption.IGNORE_CASE).find(u)?.groupValues?.get(1)?.lowercase() ?: return@runCatching
                }
                val work = torrentWork(c)
                for (root in listOf(work, File(dirPath), File(defaultDir())).distinct()) {
                    root.listFiles { f -> f.isDirectory && f.name.startsWith(".torrent-") }?.forEach { d ->
                        if (d.name.removePrefix(".torrent-").lowercase() in keep) {
                            if (root != work) { val to = File(work, d.name); if (!to.exists()) d.renameTo(to) }
                        } else d.deleteRecursively()
                    }
                }
            }
        }
    }

    /** [fixedName] (without extension) is kept: the file is named so, with the extension its content type tells. */
    fun add(ctx: Context, link: String, fixedName: String? = null, alts: List<String> = emptyList(), into: File? = null) {
        // a link of Stremio's local server is the torrent itself: it is downloaded as a torrent here (no need for Stremio to be running)
        // Stremio's local link becomes a torrent (magnet) only when the switch is on; off, the link goes as it is, as before the switch existed
        val url = (if (Downloads.magnetLinks) stremioMagnet(link) else null) ?: link
        val c = ctx.applicationContext
        app = c
        scope.launch {
            // [into]: a folder of its own for this one download (the update of Pulse goes to Download/Pulse/Updates); else the downloads folder of the settings
            val dir = into?.also { it.mkdirs() } ?: File(dirPath)
            // a link of the local Stremio server ends in the file's number in the torrent ("…/64"): it is named after the torrent and the number at first, and takes the real
            // name once the server has answered (asked then, not before: the start must not wait for it)
            val name = fixedName?.let { cleanName(it) } ?: magnetName(url) ?: streamFallbackName(url) ?: cleanName(Uri.parse(url).lastPathSegment)?.let { if (isHlsUrl(url)) it.replace(Regex("(?i)\\.m3u8$"), "") + ".ts" else it } ?: "download"
            val t = try { DlTask(url, reserve(dir, name)) } catch (e: Exception) {
                DlTask(url, File(dir, name)).apply { state.value = DlState.FAILED; error.value = "cannot write to ${dir.path}: ${e.message ?: e.javaClass.simpleName}" }
            }
            // a torrent stream gives its first pieces in order: eight connections asking for eight places made it fetch pieces all over the file and start slowly
            if (isStreamLink(url)) t.noSplit = true
            if (fixedName != null) t.keepName = true
            t.alts.addAll(alts.filter { it != url })
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

    /** Stops a download (its partial file goes too) or removes a finished one together with its file (only from the list when [keepFiles] is on). */
    fun remove(t: DlTask, alsoFiles: Boolean = !keepFiles) {
        val keep = !alsoFiles && t.state.value == DlState.DONE
        t.cancelled = true
        tasks.update { it - t }
        persist()
        app?.getSystemService(NotificationManager::class.java)?.cancel(t.nid)
        // after the writer has stopped, so it cannot bring the file back
        val running = t.job
        if (keep) return
        scope.launch {
            runCatching { running?.join() }
            sweepTorrents()
            val ok = eraseFile(t.file)
            // the folder it sat in (a season's or an episode's own) goes when nothing is left in it; the downloads folder itself never
            if (ok) pruneEmptyDirs(t.file.parentFile, File(dirPath))
            if (!ok) {
                // the file is still there (the phone refused the delete): the row goes, so the file is said to stay
                val c = app ?: return@launch
                android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(c, com.umutk.pulse.tr("Could not delete ${t.file.name}", "${t.file.name} silinemedi"), android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }

    /** Removes [dir] and the empty folders above it, up to (never including) [stop], while nothing is left in them (only hidden leftovers such as ".nomedia" do not count). */
    private fun pruneEmptyDirs(dir: File?, stop: File) {
        var d = dir
        val root = runCatching { stop.canonicalPath }.getOrNull() ?: return
        while (d != null && d.isDirectory) {
            val here = runCatching { d!!.canonicalPath }.getOrNull() ?: return
            if (here == root || !here.startsWith(root + File.separator)) return
            val left = d.listFiles()?.filter { !it.name.startsWith(".") || it.name.startsWith(".torrent-") } ?: return
            if (left.isNotEmpty()) return
            d.listFiles()?.forEach { runCatching { it.delete() } }
            if (!d.delete()) return
            d = d.parentFile
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
            .setGroup(GROUP).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
            // Android 16 Live Updates: the download asks to be promoted (the status bar chip shows the percent); other versions ignore the two extras
            .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true); if (total > 0) putCharSequence("android.shortCriticalText", "$pct%") })
            .build()
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
    else (Downloads.cleanName(magnetParam(url, "dn")) ?: "torrent").let { n ->
        // nearly every torrent of a film or an episode is a Matroska file: the reserved file says so from the start (the real name replaces it once the file list is known)
        if (Regex("\\.(mkv|mp4|avi|webm|mov|m4v|mp3|flac|zip|rar|iso)$", RegexOption.IGNORE_CASE).containsMatchIn(n)) n else "$n.mkv"
    }

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
    if (!TorrentEngine.ready(c)) throw NeedsEngine()
    resumable = false
    // the torrent's own working folder sits in the app's own storage, out of sight (shared by the downloads of the same torrent: the episodes of a season pack)
    // a torrent nobody sends anything of within a minute and a half gives way to the next stream of the same episode, when there is one
    val began = System.currentTimeMillis()
    var switchNow = false
    // a torrent that crawls (under 40 KB/s for a whole minute) while little of it is here also gives way to the next stream
    var winT = 0L; var winD = 0L
    // the network is chosen by itself (the setting "automatic", on at first): a Wi-Fi with a sign-in page or no internet, or one that was seen to give no peers, sends the torrent over mobile data
    val auto = Downloads.torrentAuto && !Downloads.torrentCell
    if (auto && Downloads.wifiBlocked(c)) Downloads.wifiTorrentBad = true
    val held = Downloads.holdCell(c, force = auto && Downloads.wifiTorrentBad)
    var cellRestart = false
    val blocked = Downloads.wifiBlocked(c) && !Downloads.torrentCell && !held
    val got = try { TorrentEngine.download(c, url, torrent, Downloads.torrentWork(c), { cancelled || paused || switchNow },
        { name ->
            // the file's own name (or the chosen name with its extension), while nothing is written yet
            if (!quiet && done.value == 0L) {
                val rel = name.replace('\\', '/')
                val leaf = rel.substringAfterLast('/')
                val ext = leaf.substringAfterLast('.', "")
                val wanted = if (keepName) file.nameWithoutExtension + (if (ext.isNotEmpty()) ".$ext" else "") else leaf
                // the episodes of a season pack go together into the pack's own folder (the one the torrent has), so there is one folder to open, not a file loose for each
                val folder = if (rel.contains('/')) Downloads.cleanName(rel.substringBefore('/')) else null
                if (folder != null) moveInto(folder, wanted) else rename(wanted)
            }
        },
        { d, t, p ->
            // while the file list is on its way, or no peer is sending yet, the row says so (instead of a still "0 MB")
            if (t > 0) total.value = t
            done.value = d
            error.value = when { t <= 0 && blocked -> "this Wi-Fi blocks torrents (sign-in page or no internet): use mobile data"; t <= 0 -> "finding peers ($p)  ·  DHT ${TorrentEngine.dhtNodes()}"; p == 0 && d < t -> "no peers yet"; else -> "" }
            val nowT = System.currentTimeMillis()
            // a minute on a Wi-Fi where the engine found no DHT nodes and no file list: the Wi-Fi gives no way out (many block it); the torrents are started again over mobile data
            if (auto && !held && t <= 0 && p == 0 && nowT - began > 60_000L && TorrentEngine.sessionAgeMs() > 60_000L && TorrentEngine.dhtNodes() < 3 && Downloads.onWifi(c)) { cellRestart = true; switchNow = true }
            // nobody to download from for a while (see the patience below): the next stream of the episode is tried
            // (the file list of a magnet link takes time: the DHT of a young session has to find its nodes first, then the peers, then they send the list; 10 seconds gave up on almost every torrent before it began)
            val patience = when {
                t <= 0 -> if (TorrentEngine.dhtNodes() < 8 || TorrentEngine.sessionAgeMs() < 60_000L) 75_000L else 45_000L
                p > 0 -> 25_000L
                else -> 20_000L
            }
            if (Downloads.autoSwitch && d == 0L && alts.isNotEmpty() && nowT - began > patience) switchNow = true
            if (t > 0) {
                if (winT == 0L) { winT = nowT; winD = d }
                else if (nowT - winT >= 60_000) {
                    val rate = (d - winD) * 1000 / (nowT - winT)
                    if (rate < 40_000 && d < t / 4 && alts.isNotEmpty()) switchNow = true
                    winT = nowT; winD = d
                }
            }
        },
    ) } finally { if (held) Downloads.releaseCell(c) }
    if (got == null && cellRestart && !cancelled && !paused) {
        Downloads.wifiTorrentBad = true
        TorrentEngine.resetSession()
        error.value = "Wi-Fi gives no peers: trying mobile data"
        throw IOException("Wi-Fi gives no peers: trying mobile data")
    }
    if (got == null) { if (switchNow && !cancelled && !paused) throw SwitchStream(); return false }
    file.delete()
    if (!got.renameTo(file)) { got.copyTo(file, overwrite = true); got.delete() }
    TorrentEngine.done(got)
    // a stream this download gave up for another one leaves its pieces behind: they go now
    Downloads.sweepTorrents()
    total.value = file.length(); done.value = file.length()
    Downloads.persist()
    return true
}

/**
 * A link of the Stremio app's own local server ("http://127.0.0.1:11470/<infohash>/<file number or name>") names a torrent by its 40-letter infohash: it is turned into a
 * magnet link (with the file's number) so Pulse downloads the torrent itself. Null when [u] is no such link, or this build has no torrent engine.
 * (The idea of reading the hash out of Stremio's link is that of Magnet Bridge, https://github.com/leffmarques/MagnetBridge; this is written afresh.)
 */
internal fun stremioMagnet(u: String): String? {
    if (!TorrentEngine.available) return null
    val m = Regex("^https?://(?:127\\.0\\.0\\.1|localhost|\\[::1\\]|0\\.0\\.0\\.0)(?::\\d+)?/([A-Fa-f0-9]{40})(?:/([^/?#]*))?", RegexOption.IGNORE_CASE).find(u.trim()) ?: return null
    val idx = m.groupValues[2].toIntOrNull()
    return "magnet:?xt=urn:btih:" + m.groupValues[1].lowercase() + (if (idx != null) "&pulse_idx=$idx" else "")
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

/** The torrent engine is not on the phone: it is fetched only when the person asks for it (the button on the row). */
internal const val NEEDS_ENGINE = "the torrent engine is not downloaded yet"
private class NeedsEngine : IOException(NEEDS_ENGINE)
/** A torrent that found no one to download from in time, while another stream of the same episode is there to try. */
private class SwitchStream : IOException("no peers: trying another stream")

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
            if (e is SwitchStream) {
                val next = synchronized(alts) { if (alts.isEmpty()) null else alts.removeAt(0) }
                if (next != null) {
                    url = next; done.value = 0L; total.value = -1L; resumable = false; partPos = null; tries = 0
                    error.value = "trying another stream"
                    Downloads.persist()
                    continue
                }
            }
            if (e is NeedsEngine) { error.value = NEEDS_ENGINE; state.value = DlState.FAILED; Downloads.persist(); return }
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

/** Moves the reserved file into [folder] of the download folder (made when needed), named [raw]. */
private fun DlTask.moveInto(folder: String, raw: String) {
    val name = Downloads.cleanName(raw) ?: return
    val cur = file.parentFile ?: return
    val root = if (cur.name == folder) cur.parentFile ?: return else cur
    val dir = File(root, folder).also { it.mkdirs() }
    if (cur == dir && name == file.name) return
    val next = runCatching { reserve(dir, name) }.getOrNull() ?: return
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
        // a different size is a different file; a different ETag alone is not (CDN edges of one file send different tags), it counts only together with a different date.
        // The saved bytes used to be thrown away at every such change, and the download began again from zero after a lost connection.
        if (started && ranged && ((total.value > 0 && size != total.value) || (etag != null && tag != etag && (lastMod == null || mod != lastMod)))) throw Changed()
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
        // Series and movies is a page of its own: back goes to the downloads
        if (SeasonDl.pageOpen) {
            androidx.activity.compose.BackHandler { SeasonDl.pageOpen = false }
            // full screen, in a window of its own: the page is not squeezed above the keyboard (the gap it left) and back closes it
            androidx.compose.ui.window.Dialog(onDismissRequest = { SeasonDl.pageOpen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                // no dimming behind the window: on Windows 11 the page is see-through, the app's own backdrop shows, as on every other page
                val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                androidx.compose.runtime.SideEffect { dialogWindow?.setDimAmount(0f) }
                Box(Modifier.fillMaxSize().background(T.bg.copy(alpha = 1f))) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        // the way back, in the look's own style: a Windows button on the Windows looks, the arrow elsewhere
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (T.id in com.umutk.pulse.winButtonLooks && T.id != "win11") com.umutk.pulse.PButton(tr("Back", "Geri"), compact = true) { SeasonDl.pageOpen = false }
                            else Box(Modifier.size(40.dp).clickable { SeasonDl.pageOpen = false }, contentAlignment = androidx.compose.ui.Alignment.Center) { PIcon("back", T.text, 22.dp) }
                            Text(tr("Series and movies", "Diziler ve filmler"), color = T.text, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                        }
                        Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) { SeasonDl.Card(page = true) }
                    }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth()) { DownloadsContent() }
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
    var bulkFiles by remember { mutableStateOf(!Downloads.keepFiles) }
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
    val listScroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(listScroll).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead("Downloads")
        if (bulkAsk) WinAlert(onDismissRequest = { bulkAsk = false }, warn = true,
            title = { Text(tr("Delete ${chosen.size} downloads", "${chosen.size} indirmeyi sil")) },
            text = { Row(Modifier.clickable { bulkFiles = !bulkFiles }, verticalAlignment = Alignment.CenterVertically) { PCheck(bulkFiles, { bulkFiles = it }); Text(tr("  Delete from the device", "  Cihazdan sil")) } },
            confirmButton = { DlDialogBtn(tr("Delete", "Sil"), true) { val n = chosen.toList(); bulkAsk = false; chosen = emptySet(); n.forEach { runCatching { Downloads.remove(it, bulkFiles) } }
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
            Box(Modifier.clickable { openTool("downloads_settings") }) { PIcon("settings", T.text, 22.dp) }
        }
        // the torrent engine (6 MB) is fetched from here when it is not on the phone yet: a torrent download waits for it, and it goes on by itself once it is here
        if (TorrentEngine.available) {
            val engineHere by androidx.compose.runtime.produceState(initialValue = true, ctx) { value = withContext(Dispatchers.IO) { TorrentEngine.ready(ctx) } }
            val engineBusy by TorrentEngine.fetching.collectAsState()
            var engineFailed by remember { mutableStateOf(false) }
            var engineNow by remember { mutableStateOf<Boolean?>(null) }
            val here = engineNow ?: engineHere
            if (!here) PCard(Modifier.clickable(enabled = !engineBusy) {
                findScope.launch {
                    val ok = TorrentEngine.fetch(ctx)
                    engineFailed = !ok
                    if (ok) {
                        engineNow = true
                        Downloads.tasks.value.filter { it.state.value == DlState.FAILED && it.error.value == NEEDS_ENGINE }.forEach { Downloads.resume(it) }
                    }
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon("download", com.umutk.pulse.Look.accent, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(when {
                        engineBusy -> tr("Downloading the torrent engine…", "Torrent motoru indiriliyor…")
                        engineFailed -> tr("The torrent engine could not be downloaded. Try again", "Torrent motoru indirilemedi. Tekrar dene")
                        else -> tr("Download the torrent engine (6 MB), for torrent downloads", "Torrent motorunu indir (6 MB), torrent indirmeleri için")
                    }, color = T.text, fontSize = 13.sp)
                }
            }
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
                                if (u.startsWith("magnet:", ignoreCase = true)) { if (Downloads.magnetLinks) Downloads.add(ctx, u); pending.remove(u); return@launch }
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
        // long press a row, then slide the finger over the others: every row it passes is picked (as in Files); a tap picks or lets go one row once some are picked
        val shownRows = list.filter { cat == "All" || category(it.file) == cat }.sortedWith(order)
        val hits = remember { HashMap<DlTask, androidx.compose.ui.layout.LayoutCoordinates>() }
        var listBox by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val dm = ctx.resources.displayMetrics
        Column(Modifier.fillMaxWidth().onGloballyPositioned { listBox = it }.pointerInput(shownRows) {
            var anchor = -1
            var base: Set<DlTask> = emptySet()
            fun hitAt(p: androidx.compose.ui.geometry.Offset): Int {
                val root = listBox?.takeIf { it.isAttached }?.localToRoot(p) ?: return -1
                for ((t, c) in hits) if (c.isAttached && c.boundsInRoot().contains(root)) return shownRows.indexOf(t)
                return -1
            }
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    anchor = hitAt(p)
                    if (anchor >= 0) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); base = chosen; chosen = base + shownRows[anchor] }
                },
                onDragEnd = { anchor = -1 }, onDragCancel = { anchor = -1 },
                onDrag = { change, _ ->
                    change.consume()
                    val i = hitAt(change.position)
                    if (i >= 0 && anchor >= 0) chosen = base + (minOf(anchor, i)..maxOf(anchor, i)).map { shownRows[it] }
                    // the list follows the finger at the top and bottom edges
                    val y = listBox?.takeIf { it.isAttached }?.localToRoot(change.position)?.y ?: 0f
                    if (y < 140 * dm.density) findScope.launch { listScroll.scrollBy(-24f) }
                    else if (y > dm.heightPixels - 180 * dm.density) findScope.launch { listScroll.scrollBy(24f) }
                },
            )
        }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shownRows.forEach { t ->
                androidx.compose.runtime.key(t) {
                    androidx.compose.runtime.DisposableEffect(t) { onDispose { hits.remove(t) } }
                    Box(Modifier.onGloballyPositioned { hits[t] = it }) {
                        DownloadRow(t, t in chosen, chosen.isNotEmpty()) { chosen = if (t in chosen) chosen - t else chosen + t }
                    }
                }
            }
        }
        // room under the last row for the floating bar
        Spacer(Modifier.height(72.dp))
    }
    // the bar of the chosen rows floats at the bottom: it stays in reach while the list scrolls, to delete them one after the other
    androidx.compose.animation.AnimatedVisibility(chosen.isNotEmpty(), Modifier.align(Alignment.BottomCenter),
        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
        exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180)) { it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth()) {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${chosen.size}", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // all the rows shown, or none of them again when they are all picked already
                val every = list.filter { cat == "All" || category(it.file) == cat }.toSet()
                val allOn = every.isNotEmpty() && chosen.containsAll(every)
                Row(Modifier.clip(RoundedCornerShape(50)).background(if (allOn) com.umutk.pulse.Look.accent.copy(alpha = .18f) else Color.Transparent)
                    .clickable { chosen = if (allOn) emptySet() else every }.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("check", if (allOn) com.umutk.pulse.Look.accent else T.text, 20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("All", "Tümü"), color = if (allOn) com.umutk.pulse.Look.accent else T.text, fontSize = 14.sp)
                }
                Box(Modifier.clickable { bulkFiles = !Downloads.keepFiles; bulkAsk = true }) { PIcon("delete", Color(0xFFE5484D), 22.dp) }
                Box(Modifier.clickable { chosen = emptySet() }) { PIcon("close", T.sub, 22.dp) }
            }
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
    val ring by androidx.compose.animation.animateColorAsState(if (picked) com.umutk.pulse.Look.accent else Color.Transparent, androidx.compose.animation.core.tween(200), label = "pick")
    val lift by androidx.compose.animation.core.animateFloatAsState(if (picked) .97f else 1f, androidx.compose.animation.core.tween(200), label = "lift")
    PCard(Modifier.fillMaxWidth().graphicsLayer { scaleX = lift; scaleY = lift }.border(2.dp, ring, RoundedCornerShape(8.dp))
        .clickable { if (selecting) toggle() else if (st == DlState.DONE) Downloads.openFile(ctx, t.file) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.file.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = T.sub, fontSize = 12.sp)
            }
            if (st == DlState.RUNNING) Box(Modifier.padding(start = 8.dp).clickable { Downloads.pause(t) }) { PIcon("pause", T.sub, 22.dp) }
            if (st == DlState.PAUSED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("play", T.text, 22.dp) }
            if (st == DlState.FAILED) Box(Modifier.padding(start = 8.dp).clickable { Downloads.resume(t) }) { PIcon("download", T.text, 22.dp) }
            var pick by remember { mutableStateOf(false) }
            // a tap opens this download's own folder in the file app; held, the folder new downloads go to can be changed
            Box(Modifier.padding(start = 8.dp).combinedClickable(onLongClick = { pick = true }, onClick = { Downloads.openFolder(ctx, t.file.parentFile ?: File(Downloads.dirPath)) })) { PIcon("folder", T.sub, 22.dp) }
            if (pick) FolderPicker(File(Downloads.dirPath)) { pick = false }
            Spacer(Modifier.width(12.dp))
            var confirmDel by remember { mutableStateOf(false) }
            var alsoFiles by remember { mutableStateOf(!Downloads.keepFiles) }
            Box(Modifier.clickable { alsoFiles = !Downloads.keepFiles; confirmDel = true }) { PIcon("delete", T.sub, 22.dp) }
            if (confirmDel) WinAlert(onDismissRequest = { confirmDel = false }, warn = true,
                title = { Text(tr("Delete download", "İndirmeyi sil")) },
                text = { Column {
                    Text(t.file.name)
                    if (st == DlState.DONE) Row(Modifier.padding(top = 10.dp).clickable { alsoFiles = !alsoFiles }, verticalAlignment = Alignment.CenterVertically) {
                        PCheck(alsoFiles, { alsoFiles = it }); Text(tr("  Delete from the device", "  Cihazdan sil"))
                    }
                } },
                confirmButton = { DlDialogBtn(tr("Delete", "Sil"), true) { confirmDel = false; Downloads.remove(t, alsoFiles || st != DlState.DONE) } },
                dismissButton = { DlDialogBtn(tr("Cancel", "Vazgeç"), false) { confirmDel = false } })
        }
        // a torrent waiting for the engine: it is fetched here, once (6 MB), then every download that waited for it goes on
        if (st == DlState.FAILED && err == NEEDS_ENGINE) {
            val getting by TorrentEngine.fetching.collectAsState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(T.text.copy(alpha = .08f))
                .clickable(enabled = !getting) {
                    scope.launch {
                        val ok = TorrentEngine.fetch(ctx)
                        if (ok) Downloads.tasks.value.filter { it.state.value == DlState.FAILED && it.error.value == NEEDS_ENGINE }.forEach { Downloads.resume(it) }
                        else android.widget.Toast.makeText(ctx, tr("The torrent engine could not be downloaded", "Torrent motoru indirilemedi"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("download", com.umutk.pulse.Look.accent, 20.dp)
                Text(if (getting) tr("Downloading the torrent engine…", "Torrent motoru indiriliyor…") else tr("Download the torrent engine (6 MB)", "Torrent motorunu indir (6 MB)"),
                    color = T.text, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
            }
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
