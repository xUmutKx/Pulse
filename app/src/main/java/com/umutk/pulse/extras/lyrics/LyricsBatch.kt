package com.umutk.pulse.extras.lyrics

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
import androidx.core.content.FileProvider
import com.umutk.pulse.extras.ai.Transcriber
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What the lyrics batch is doing. The page reads it, so leaving the page and coming back shows the same progress. */
object LyricsBatchJob {
    var busy by mutableStateOf(false)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var now by mutableStateOf("")
    /** How many songs got their lyrics from the search (the rest were listened to). */
    var found by mutableIntStateOf(0)
    // what the song being worked on shows: how far it is (0..1), what the AI is doing, and the lines it has found so far (LRC text)
    var frac by mutableStateOf(0f)
    var step by mutableStateOf("")
    var live by mutableStateOf("")
    var startedAt by mutableStateOf(0L)
    /** The song that was finished last, its lyrics (LRC text; empty when they came from the search), and a count that changes with each one: screens watch it. */
    var lastPath by mutableStateOf("")
    var lastLrc by mutableStateOf("")
    var doneTick by mutableIntStateOf(0)
    /** The path of the song being worked on now. */
    var nowPath by mutableStateOf("")

    private const val PREF = "lyrics_batch"
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** The songs still waiting, one row each: path, title, artist. Kept on the phone, so a restart goes on with them. */
    internal fun queue(c: Context): List<List<String>> = sp(c).getString("queue", "").orEmpty()
        .lines().filter { it.isNotBlank() }.map { it.split('\t') }.filter { it.size >= 3 }

    internal fun saveQueue(c: Context, rows: List<List<String>>) {
        sp(c).edit().putString("queue", rows.joinToString("\n") { it.joinToString("\t") }).apply()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    /** Starts the batch for [songs] (the ones without lyrics). False when it cannot start: no speech model, nothing to do, or it runs already. */
    fun start(c: Context, songs: List<Track>): Boolean {
        val app = c.applicationContext
        if (busy) return false
        val rows = songs.filter { !it.path.contains("://") && File(it.path).isFile }
            .map { listOf(it.path, clean(it.title ?: File(it.path).nameWithoutExtension), clean(it.artist.orEmpty())) }
        if (rows.isEmpty()) return false
        saveQueue(app, rows)
        busy = true; done = 0; found = 0; total = rows.size; now = ""
        val started = runCatching { ContextCompat.startForegroundService(app, Intent(app, LyricsBatchService::class.java)) }.isSuccess
        if (!started) busy = false
        return started
    }

    /**
     * One song, now: it goes to the front of the queue (the batch, if one runs, takes it next; otherwise it starts alone). It runs in the background
     * service, so leaving the screen or pressing Back does not stop it; screens show [frac], [step] and [live], and see [doneTick] change when it is done.
     */
    fun startOne(c: Context, path: String, title: String, artist: String): Boolean {
        val app = c.applicationContext
        val row = listOf(path, clean(title), clean(artist))
        if (busy) {
            // already in the queue (or being worked on): nothing to add
            if (nowPath == path || queue(app).any { it[0] == path }) return true
            saveQueue(app, listOf(row) + queue(app)); total++
            return true
        }
        return start(app, listOf(Track(path = path, title = title, artist = artist)))
    }

    /** Stops the batch: the songs still waiting are dropped, the one being listened to is left as it is. */
    fun cancel(c: Context) {
        saveQueue(c.applicationContext, emptyList())
        c.applicationContext.stopService(Intent(c.applicationContext, LyricsBatchService::class.java))
    }
}

/** Whether a song has lyrics already, read once per file version (the batch and the page both ask). */
internal object LyricsStatus {
    private val seen = ConcurrentHashMap<String, Pair<Long, Boolean>>()

    fun has(c: Context, path: String, artist: String, title: String): Boolean {
        // the index (read in the background after each scan, kept on the phone) answers at once; a song it has not read yet is read here
        LyricsIndex.status(c, path)?.let { return it }
        val m = File(path).lastModified()
        seen[path]?.takeIf { it.first == m }?.let { return it.second }
        val v = Lyrics.hasLocal(c, path, artist, title)
        seen[path] = m to v
        return v
    }

    fun forget(path: String) { seen.remove(path); LyricsIndex.changed(path) }
}

/**
 * Makes the lyrics of the songs that have none, one after another, with Pulse AI on the phone. It runs as a service with a notification, so it
 * goes on when the page is left or the screen is off; a song that gets lyrics some other way is skipped; a restart of the service goes on with
 * the songs still in the queue. Each song's lyrics are written to its tag (MP3) or a .lrc beside it.
 */
class LyricsBatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val ch = "lyrics_batch"
    private val id = 7437

    override fun onBind(intent: Intent?): IBinder? = null

    private fun note(): Notification {
        val total = LyricsBatchJob.total
        val b = NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.stat_notify_sync).setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .setContentTitle(tr("Making lyrics", "Sözler yapılıyor"))
            .setContentText(if (total > 0) "${LyricsBatchJob.done} / $total · ${LyricsBatchJob.now}" else "…")
        if (total > 0) b.setProgress(total, LyricsBatchJob.done, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun update() { getSystemService(NotificationManager::class.java).notify(id, note()) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(ch, tr("Lyrics", "Sözler"), NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 34) startForeground(id, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(id, note())
        if (job?.isActive == true) return START_STICKY
        job = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val app = applicationContext
        LyricsBatchJob.busy = true
        if (LyricsBatchJob.total == 0) LyricsBatchJob.total = LyricsBatchJob.queue(app).size + LyricsBatchJob.done
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val row = LyricsBatchJob.queue(app).firstOrNull() ?: break
                val path = row[0]; val title = row[1]; val artist = row[2]
                LyricsBatchJob.now = title
                LyricsBatchJob.nowPath = path
                LyricsBatchJob.frac = 0f; LyricsBatchJob.step = ""; LyricsBatchJob.live = ""; LyricsBatchJob.startedAt = System.currentTimeMillis()
                var madeLrc = ""
                update()
                try {
                    if (!LyricsStatus.has(app, path, artist, title) && File(path).isFile) {
                        // first the lyrics search (the same as when a song is opened: the net, kept beside the song); the AI listens only to what it does not find
                        val found = runCatching { Lyrics.forTrack(app, title, artist, null, 0L, path) }.getOrNull()
                        if (found != null) { LyricsStatus.forget(path); LyricsBatchJob.found++ } else if (Transcriber.ready(app)) {
                        val uri = FileProvider.getUriForFile(app, app.packageName + ".files", File(path))
                        // the progress of the song is read while it is listened to
                        val poll = scope.launch { while (true) { LyricsBatchJob.frac = Transcriber.progress(); kotlinx.coroutines.delay(500) } }
                        val lrc = try {
                            Transcriber.lrc(app, uri, "auto", title, artist, { s, _ -> LyricsBatchJob.step = s; LyricsBatchJob.now = "$title · $s" }, { lines -> LyricsBatchJob.live = lines })
                        } finally { poll.cancel() }
                        madeLrc = lrc.orEmpty()
                        lrc?.let { Lyrics.fromText(it, "ai") }?.let { r -> Lyrics.saveEdited(path, r, app) }
                        LyricsStatus.forget(path)
                        }
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) { }
                LyricsBatchJob.saveQueue(app, LyricsBatchJob.queue(app).drop(1))
                LyricsBatchJob.frac = 1f
                LyricsBatchJob.lastPath = path; LyricsBatchJob.lastLrc = madeLrc; LyricsBatchJob.doneTick++
                LyricsBatchJob.done++
                update()
            }
        } finally {
            val finished = LyricsBatchJob.queue(app).isEmpty()
            LyricsBatchJob.busy = false
            if (finished) {
                getSystemService(NotificationManager::class.java).notify(id + 1, NotificationCompat.Builder(this, ch).setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setAutoCancel(true).setContentTitle(tr("Lyrics finished", "Sözler bitti"))
                    .setContentText(tr("${LyricsBatchJob.done} songs got lyrics", "${LyricsBatchJob.done} şarkıya söz yazıldı")).build())
            }
            // the songs that got lyrics are read into the index again (in the background)
            runCatching { Lyrics.warmIndex(app, MusicLibrary.tracks.value) }
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        Transcriber.stop()
        LyricsBatchJob.busy = false
        super.onDestroy()
    }
}
