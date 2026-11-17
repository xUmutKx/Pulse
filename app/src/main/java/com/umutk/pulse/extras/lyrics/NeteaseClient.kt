package com.umutk.pulse.extras.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * NetEase Cloud Music's public lyric pages, as a second source next to LRCLIB: they hold the lyrics of many songs LRCLIB lacks (also in other languages) and, for many,
 * a time for every word ("yrc"). One search and one lyric request per song, only when the lyrics are not already on the phone. Switched in Settings > Lyrics.
 * What it gives is an enhanced LRC ([WordLyrics]) or a plain LRC, as a [LyricsRecord] like any other find.
 */
object NeteaseClient {
    private const val BASE = "https://music.163.com/api"
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Referer", "https://music.163.com/"); c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 6_000; c.readTimeout = 8_000
            if (c.responseCode in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        } finally { c.disconnect() }
    }.getOrNull()

    /** The song that fits [title] and [artist] best (and the length, within a few seconds), or null when none is close enough. */
    private fun best(title: String, artist: String, durationMs: Long): Pair<Long, JSONObject>? {
        val titles = listOf(title, Lyrics.cleanTitle(title)).filter { it.isNotBlank() }.distinct()
        val artists = (listOf(artist) + Lyrics.splitArtists(artist)).filter { it.isNotBlank() }.distinct()
        val q = (artists.firstOrNull().orEmpty() + " " + titles.last()).trim()
        val body = get("$BASE/search/get?s=" + URLEncoder.encode(q, "UTF-8") + "&type=1&limit=8") ?: return null
        val songs = runCatching { JSONObject(body).getJSONObject("result").getJSONArray("songs") }.getOrNull() ?: return null
        var top: Pair<Long, JSONObject>? = null; var topScore = 0f
        for (i in 0 until songs.length()) {
            val s = songs.getJSONObject(i)
            val name = s.optString("name")
            val names = s.optJSONArray("artists")?.let { a -> List(a.length()) { a.getJSONObject(it).optString("name") } }.orEmpty()
            val ts = titles.maxOf { Lyrics.alike(it, name) }
            val ars = if (artists.isEmpty()) .5f else artists.maxOf { a -> names.maxOfOrNull { Lyrics.alike(a, it) } ?: 0f }
            val dur = s.optLong("duration")
            val near = if (durationMs > 0 && dur > 0) (if (kotlin.math.abs(dur - durationMs) <= 4_000) .12f else if (kotlin.math.abs(dur - durationMs) > 15_000) -.2f else 0f) else 0f
            val score = ts * .62f + ars * .38f + near
            if (ts >= .6f && score > topScore) { topScore = score; top = s.optLong("id") to s }
        }
        return top?.takeIf { topScore >= .62f }
    }

    /** The lyrics of a song: with a time for every word when NetEase has them, else with the times of the lines; null when there is nothing. */
    suspend fun find(title: String, artist: String, durationMs: Long): LyricsRecord? = withContext(Dispatchers.IO) {
        val (id, song) = best(title, artist, durationMs) ?: return@withContext null
        val body = get("$BASE/song/lyric?id=$id&lv=-1&kv=-1&tv=-1&yv=-1") ?: return@withContext null
        val j = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        val words = j.optJSONObject("yrc")?.optString("lyric")?.takeIf { it.isNotBlank() }?.let { WordLyrics.fromYrc(it) }
        val lines = j.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }?.let { WordLyrics.cleanLrc(it) }
        val synced = words ?: lines ?: return@withContext null
        val artists = song.optJSONArray("artists")?.let { a -> List(a.length()) { a.getJSONObject(it).optString("name") }.joinToString(", ") }.orEmpty()
        LyricsRecord(id, song.optString("name"), artists, song.optJSONObject("album")?.optString("name"), false, null, synced)
    }
}
