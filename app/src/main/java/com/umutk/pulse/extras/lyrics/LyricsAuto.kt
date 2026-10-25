package com.umutk.pulse.extras.lyrics

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umutk.pulse.extras.music.MusicLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Lyrics for the whole library, found in the background: every song without lyrics is looked up one after another (the phone first, then the net), the found lyrics are
 * saved the way the player saves them. A song the net has nothing for is not asked again for three days (see [Lyrics.forTrack]). Runs on a network connection only.
 */
class LyricsBackfillWorker(private val app: Context, params: WorkerParameters) : CoroutineWorker(app, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!LyricsAuto.on(app)) return@withContext Result.success()
        if (MusicLibrary.tracks.value.isEmpty()) runCatching { MusicLibrary.refresh(app) }
        val list = MusicLibrary.tracks.value.filter { !it.path.startsWith("http") }
        LyricsAuto.progress(app, 0, list.size, 0)
        var found = 0
        list.forEachIndexed { i, t ->
            if (isStopped || !LyricsAuto.on(app)) return@withContext Result.success()
            val title = t.title?.takeIf { it.isNotBlank() } ?: t.path.substringAfterLast('/').substringBeforeLast('.')
            val artist = t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
            val local = t.path.takeIf { !it.contains("://") }
            val r = runCatching { Lyrics.forTrack(app, title, artist, t.album, t.durationSec * 1000L, local) }.getOrNull()
            if (r != null) {
                found++
                if (local != null && r.source in setOf("lrclib", "ovh", "cache")) {
                    if (LiveLyrics.saveLrc(app)) Lyrics.saveSidecar(local, r)
                    if (LiveLyrics.embed(app)) Lyrics.embedMp3(local, r)
                }
            }
            if (i % 10 == 0) LyricsAuto.progress(app, i + 1, list.size, found)
            // the service is asked about two songs a second at most
            delay(450)
        }
        LyricsAuto.progress(app, list.size, list.size, found)
        Result.success()
    }
}

object LyricsAuto {
    private const val NAME = "lyrics_backfill"
    private fun prefs(c: Context) = c.getSharedPreferences(LiveLyrics.PREFS, Context.MODE_PRIVATE)

    /** On by default: the lyrics of the whole library are found in the background. */
    fun on(c: Context) = prefs(c).getBoolean("auto_all", true)
    fun setOn(c: Context, v: Boolean) { prefs(c).edit().putBoolean("auto_all", v).apply(); if (v) ensure(c) else WorkManager.getInstance(c).cancelUniqueWork(NAME) }

    fun progress(c: Context, done: Int, total: Int, found: Int) { prefs(c).edit().putInt("auto_done", done).putInt("auto_total", total).putInt("auto_found", found).apply() }
    fun status(c: Context): Triple<Int, Int, Int> = prefs(c).let { Triple(it.getInt("auto_done", 0), it.getInt("auto_total", 0), it.getInt("auto_found", 0)) }

    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Queues the daily pass (an existing one is kept) and one pass now. */
    fun ensure(c: Context) {
        if (!on(c)) return
        val wm = WorkManager.getInstance(c.applicationContext)
        wm.enqueueUniquePeriodicWork(NAME + "_daily", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<LyricsBackfillWorker>(24, TimeUnit.HOURS).setConstraints(net).build())
        wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LyricsBackfillWorker>().setConstraints(net).build())
    }

    /** "Search now": a new pass at once. */
    fun runNow(c: Context) {
        if (!on(c)) prefs(c).edit().putBoolean("auto_all", true).apply()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LyricsBackfillWorker>().setConstraints(net).build())
    }
}
