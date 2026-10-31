package com.umutk.pulse.extras.lyrics

import android.content.Context
import com.umutk.pulse.extras.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Saved lyrics: one JSON file per song in filesDir/lyrics-saved, plus links.json (audio path to file name)
 * so the playing track finds its lyrics. An .lrc / .txt is also written beside the audio file when the folder is writable.
 */
class LyricsStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "lyrics-saved")
    private val links = File(dir, "links.json")

    private fun name(r: LyricsRecord) = sha("${r.artistName}|${r.trackName}".lowercase()) + ".json"

    fun list(): List<LyricsRecord> =
        (dir.listFiles { f -> f.name.endsWith(".json") && f.name != "links.json" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> runCatching { LyricsRecord.fromJson(JSONObject(f.readText())) }.getOrNull() }

    /** Lyrics saved for this audio file, else saved under the same artist and title. */
    fun load(path: String?, artist: String, title: String): LyricsRecord? {
        val linked = path?.let { runCatching { JSONObject(links.readText()).optString(it) }.getOrNull() }?.takeIf { it.isNotEmpty() }
        val f = linked?.let { File(dir, it) }?.takeIf { it.isFile }
            ?: File(dir, name(LyricsRecord(0, title, artist, null, false, null, null))).takeIf { it.isFile }
        return f?.let { runCatching { LyricsRecord.fromJson(JSONObject(it.readText())) }.getOrNull() }
    }

    fun isSaved(r: LyricsRecord) = File(dir, name(r)).isFile

    /**
     * The lyrics go beside the song as a .lrc (synced) or .txt file. Only when that cannot be written (a stream, a read-only folder)
     * a copy is kept here. Returns the sidecar file when one was written.
     */
    fun save(r: LyricsRecord, audioPath: String?): File? {
        val body = r.syncedLyrics ?: r.plainLyrics
        if (audioPath != null && body != null && Lyrics.keepText(audioPath, body, "saved")) {
            return File(audioPath.substringBeforeLast('.') + ".lrc")
        }
        dir.mkdirs()
        val o = JSONObject().put("id", r.id).put("trackName", r.trackName).put("artistName", r.artistName)
            .put("albumName", r.albumName ?: "").put("instrumental", r.instrumental)
            .put("plainLyrics", r.plainLyrics ?: "").put("syncedLyrics", r.syncedLyrics ?: "")
        File(dir, name(r)).writeText(o.toString())
        if (audioPath != null) runCatching {
            val m = if (links.isFile) JSONObject(links.readText()) else JSONObject()
            links.writeText(m.put(audioPath, name(r)).toString())
        }
        return null
    }

    fun delete(r: LyricsRecord, audioPath: String?) {
        File(dir, name(r)).delete()
        if (audioPath != null) runCatching {
            val m = JSONObject(links.readText()); m.remove(audioPath); links.writeText(m.toString())
        }
    }

    companion object {
        /** A .lrc next to the audio file, if any. */
        fun sidecar(path: String, title: String, artist: String): LyricsRecord? {
            val f = File(path.substringBeforeLast('.') + ".lrc").takeIf { it.isFile } ?: return null
            val t = runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return LyricsRecord(0, title, artist, null, false, null, t)
        }
    }
}

private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/** "Artist - Title" names are split; otherwise the artist is unknown. */
fun guessArtistTitle(t: Track): Pair<String, String> {
    val n = t.title?.takeIf { it.isNotBlank() } ?: t.path.substringAfterLast('/').substringBeforeLast('.')
    val i = n.indexOf(" - ")
    return if (i > 0) n.substring(0, i).trim() to n.substring(i + 3).trim() else "" to n.trim()
}

/** lyrics.ovh, plain text only: https://api.lyrics.ovh/v1/artist/title. Null when not found. */
suspend fun ovhLyrics(artist: String, title: String): LyricsRecord? = withContext(Dispatchers.IO) {
    fun e(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    val c = URL("https://api.lyrics.ovh/v1/${e(artist)}/${e(title)}").openConnection() as HttpURLConnection
    try {
        c.setRequestProperty("User-Agent", "Pulse/1.0 (Android)")
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        if (c.responseCode !in 200..299) return@withContext null
        val text = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("lyrics").trim()
        if (text.isEmpty()) null else LyricsRecord(0, title, artist, null, false, text, null)
    } finally { c.disconnect() }
}

/** LRCLIB first (exact match, else best search hit, synced preferred), then lyrics.ovh. Network errors are thrown. */
suspend fun autoFind(client: LrclibClient, artist: String, title: String, durationSec: Int?): LyricsRecord? {
    val hit = if (artist.isNotBlank()) client.get(title, artist, null, durationSec)?.takeIf { it.hasLyrics } else null
    val found = hit ?: client.search("$artist $title".trim()).let { l -> l.firstOrNull { it.hasLyrics && it.syncedLyrics != null } ?: l.firstOrNull { it.hasLyrics } }
    return found ?: if (artist.isNotBlank()) ovhLyrics(artist, title) else null
}
