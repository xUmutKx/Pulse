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

    /** The normal request; when the name cannot be reached (blocked or broken DNS) the same request is retried to the address a DNS-over-HTTPS server gives. */
    private fun request(url: String): Triple<Int, String, Long> =
        try { requestOn(url, null) } catch (e: IOException) {
            val host = URL(url).host
            val ip = DohResolver.resolve(host) ?: throw IOException("${e.javaClass.simpleName}: ${e.message}; DNS-over-HTTPS found no address")
            Lyrics.lastError = null
            try { requestOn(url, ip) } catch (e2: IOException) { throw IOException("${e2.javaClass.simpleName}: ${e2.message} (via $ip)") }
        }

    private fun requestOn(url: String, ip: String?): Triple<Int, String, Long> {
        val real = URL(url)
        val c = (if (ip == null) real else URL(real.protocol, ip, real.port, real.file)).openConnection() as HttpURLConnection
        if (ip != null && c is javax.net.ssl.HttpsURLConnection) {
            val host = real.host
            val base = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            // the TLS name (SNI) and the certificate check stay the real host name
            c.sslSocketFactory = object : javax.net.ssl.SSLSocketFactory() {
                override fun getDefaultCipherSuites(): Array<String> = base.defaultCipherSuites
                override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites
                override fun createSocket(s: java.net.Socket, h: String?, port: Int, auto: Boolean): java.net.Socket = base.createSocket(s, host, port, auto)
                override fun createSocket(h: String?, port: Int): java.net.Socket = base.createSocket(host, port)
                override fun createSocket(h: String?, port: Int, la: java.net.InetAddress?, lp: Int): java.net.Socket = base.createSocket(host, port, la, lp)
                override fun createSocket(a: java.net.InetAddress?, port: Int): java.net.Socket = base.createSocket(a, port)
                override fun createSocket(a: java.net.InetAddress?, port: Int, la: java.net.InetAddress?, lp: Int): java.net.Socket = base.createSocket(a, port, la, lp)
            }
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session -> javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session) }
            c.setRequestProperty("Host", host)
        }
        try {
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 6_000
            c.readTimeout = 8_000
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

/** Looks a host up over HTTPS (Cloudflare, then Google, by address) for the networks whose own DNS does not answer for it. */
object DohResolver {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun resolve(host: String): String? {
        cache[host]?.let { return it }
        val servers = listOf("https://1.1.1.1/dns-query?name=$host&type=A", "https://8.8.8.8/resolve?name=$host&type=A")
        for (u in servers) {
            val ip = runCatching {
                val c = URL(u).openConnection() as HttpURLConnection
                try {
                    c.setRequestProperty("Accept", "application/dns-json"); c.connectTimeout = 4_000; c.readTimeout = 4_000
                    val answers = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("Answer") ?: return@runCatching null
                    (0 until answers.length()).map { answers.getJSONObject(it) }.firstOrNull { it.optInt("type") == 1 }?.optString("data")
                } finally { c.disconnect() }
            }.getOrNull()
            if (!ip.isNullOrBlank()) { cache[host] = ip; return ip }
        }
        return null
    }
}
