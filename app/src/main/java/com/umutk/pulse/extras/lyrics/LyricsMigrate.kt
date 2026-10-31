package com.umutk.pulse.extras.lyrics

import android.content.Context
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.Track
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Moves the lyrics Pulse kept in its own data and cache to the songs: each one is written beside its song (.lrc or .txt)
 * and the copy goes. A copy whose song has no writable file beside it stays in lyrics-saved. Runs once.
 */
object LyricsMigrate {
    private const val VERSION = 1

    suspend fun run(app: Context) {
        val prefs = app.getSharedPreferences(LiveLyrics.PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("moved_v", 0) >= VERSION) return
        if (MusicLibrary.tracks.value.isEmpty()) runCatching { MusicLibrary.refresh(app) }
        runCatching { moveAll(app, MusicLibrary.tracks.value) }
        prefs.edit().putInt("moved_v", VERSION).apply()
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Keeps [body] with the song (its tag, else a .lrc beside it); true when the song has its lyrics now. */
    private fun beside(audio: String, body: String, @Suppress("UNUSED_PARAMETER") synced: Boolean): Boolean =
        Lyrics.keepText(audio, body, "saved")

    private fun moveAll(app: Context, tracks: List<Track>) {
        val saved = File(app.filesDir, "lyrics-saved")
        val cache = File(app.filesDir, "lyrics-cache")
        // the copies saved for a file, through links.json
        val links = File(saved, "links.json")
        if (links.isFile) {
            val m = JSONObject(links.readText())
            val rest = JSONObject()
            m.keys().forEach { audio ->
                val f = File(saved, m.getString(audio))
                val r = runCatching { LyricsRecord.fromJson(JSONObject(f.readText())) }.getOrNull()
                if (r != null) {
                    val synced = !r.syncedLyrics.isNullOrBlank()
                    val body = (if (synced) r.syncedLyrics else r.plainLyrics)?.takeIf { it.isNotBlank() }
                    if (body != null && beside(audio, body, synced)) f.delete()
                }
                if (f.isFile) rest.put(audio, m.getString(audio))
            }
            links.writeText(rest.toString())
        }
        // the songs of the library: the copy saved under the song's artist and title, and the downloaded one
        tracks.filter { !it.path.contains("://") }.forEach { t ->
            val artist = t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
            val title = t.title?.takeIf { it.isNotBlank() } ?: t.path.substringAfterLast('/').substringBeforeLast('.')
            File(saved, sha("$artist|$title".lowercase()) + ".json").takeIf { it.isFile }?.let { f ->
                runCatching { LyricsRecord.fromJson(JSONObject(f.readText())) }.getOrNull()?.let { r ->
                    val synced = !r.syncedLyrics.isNullOrBlank()
                    val body = (if (synced) r.syncedLyrics else r.plainLyrics)?.takeIf { it.isNotBlank() }
                    if (body != null && beside(t.path, body, synced)) f.delete()
                }
            }
            File(cache, sha("t|${artist.trim().lowercase()}|${title.trim().lowercase()}").take(32) + ".txt").takeIf { it.isFile }?.let { f ->
                val text = runCatching { f.readText() }.getOrNull()?.substringAfter('\n').orEmpty()
                if (text.isNotBlank()) beside(t.path, text, Regex("\\[\\d{1,3}:\\d{2}").containsMatchIn(text))
            }
        }
        // the rest is copies that can be fetched again: the request cache, the search hits and the old folders go
        listOf(File(app.cacheDir, "lyrics"), File(app.cacheDir, "lyrics-search"), cache, File(app.filesDir, "lyrics-hits")).forEach { it.deleteRecursively() }
    }
}
