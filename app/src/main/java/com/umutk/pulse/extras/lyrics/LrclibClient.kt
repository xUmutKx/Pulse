package com.umutk.pulse.extras.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** LRCLIB (lrclib.net) client. Requests run one at a time, at least 300 ms apart; answers are cached on disk. */
class LrclibClient(private val cache: LyricsCache) {

    suspend fun search(query: String): List<LyricsRecord> {
        val body = cached("search|$query") { fetch("$BASE/search?" + qs("q" to query)) } ?: return emptyList()
        val arr = JSONArray(body)
        return List(arr.length()) { LyricsRecord.fromJson(arr.getJSONObject(it)) }
    }

    /** Exact match by title, artist, album and duration; falls back to the first search hit with lyrics. */
    suspend fun get(title: String, artist: String, album: String?, durationSec: Int?): LyricsRecord? {
        val body = cached("get|$artist|$title|$album|$durationSec") {
            fetch("$BASE/get?" + qs("track_name" to title, "artist_name" to artist, "album_name" to album, "duration" to durationSec?.toString()))
        } ?: return search("$artist $title").firstOrNull { it.hasLyrics }
        return LyricsRecord.fromJson(JSONObject(body))
    }

    /** Empty cached answers mean "not found"; network errors are thrown and not cached. */
    private suspend fun cached(key: String, load: suspend () -> String?): String? {
        cache.get(key)?.let { return if (it.isEmpty()) null else it }
        val body = load()
        cache.put(key, body ?: "")
        return body
    }

    /** Body on 2xx, null on 404, one retry after 429/503 (Retry-After, at most 5 s), otherwise IOException. */
    private suspend fun fetch(url: String): String? = gate.withLock {
        for (attempt in 0..1) {
            val wait = MIN_GAP_MS - (System.currentTimeMillis() - lastAt)
            if (wait > 0) delay(wait)
            val (code, body, retryAfter) = withContext(Dispatchers.IO) { request(url) }
            lastAt = System.currentTimeMillis()
            when {
                code in 200..299 -> return@withLock body
                code == 404 -> return@withLock null
                (code == 429 || code == 503) && attempt == 0 -> delay(retryAfter.coerceIn(1L, 5L) * 1000)
                else -> throw IOException("LRCLIB HTTP $code")
            }
        }
        throw IOException("LRCLIB busy")
    }

    private fun request(url: String): Triple<Int, String, Long> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            val code = c.responseCode
            val body = if (code in 200..299) c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else ""
            return Triple(code, body, c.getHeaderField("Retry-After")?.toLongOrNull() ?: 1L)
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://lrclib.net/api"
        const val USER_AGENT = "Pulse/1.0 (Android)"
        const val MIN_GAP_MS = 300L
        val gate = Mutex()
        @Volatile var lastAt = 0L
    }
}

private fun qs(vararg pairs: Pair<String, String?>): String =
    pairs.mapNotNull { (k, v) -> v?.let { "$k=${URLEncoder.encode(it, "UTF-8")}" } }.joinToString("&")

/** Disk cache, one JSON file per request key. shortcut: never expires and never trimmed, add a size cap and expiry if it grows. */
class LyricsCache(private val dir: File) {
    fun get(key: String): String? = File(dir, name(key)).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun put(key: String, value: String) {
        runCatching { dir.mkdirs(); File(dir, name(key)).writeText(value) }
    }

    private fun name(key: String) =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json"
}
