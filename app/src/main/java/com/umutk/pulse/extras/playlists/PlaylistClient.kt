package com.umutk.pulse.extras.playlists

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One listed song. [videoId] is null for Spotify rows until [PlaylistClient.videoId] finds a YouTube match. */
data class PItem(val title: String, val artist: String, val durSec: Int, val thumb: String?, val videoId: String? = null)

data class PList(val name: String, val items: List<PItem>, val ytPlaylistUrl: String? = null)

/**
 * Tiny Piped API client (no key, no account, plain HTTP). Piped is the open YouTube front end; public instances
 * come and go, so the live list is fetched once and instances are tried in turn.
 * Spotify: only the public embed page is read for the track list; audio is never taken from Spotify,
 * every Spotify row is matched to a YouTube search at play time.
 */
object PlaylistClient {
    private val FALLBACK = listOf("https://pipedapi.kavin.rocks", "https://api.piped.private.coffee", "https://pipedapi.adminforge.de")
    private var instances: List<String>? = null
    private val ids = HashMap<String, String?>()

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Pulse")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun apis(): List<String> {
        instances?.let { return it }
        val live = runCatching {
            val a = JSONArray(get("https://piped-instances.kavin.rocks/"))
            (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("api_url").takeIf { u -> u.startsWith("https://") } }.take(6)
        }.getOrDefault(emptyList())
        return (live + FALLBACK).distinct().also { instances = it }
    }

    /** Runs [path] on the first instance that answers. */
    private suspend fun api(path: String): JSONObject = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (base in apis()) {
            try { return@withContext JSONObject(get(base + path)) } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no instance")
    }

    private fun streamId(url: String) = url.substringAfter("v=", "").substringBefore('&')

    private fun items(a: JSONArray): List<PItem> = (0 until a.length()).mapNotNull {
        val o = a.getJSONObject(it)
        val id = streamId(o.optString("url"))
        if (id.isEmpty()) null else PItem(o.optString("title"), o.optString("uploaderName").removeSuffix(" - Topic"), o.optInt("duration", -1), o.optString("thumbnail").ifEmpty { null }, id)
    }

    // ---- Invidious: the second source, used when no Piped instance answers (public instances of both come and go) ----
    private val INV_FALLBACK = listOf("https://inv.nadeko.net", "https://yewtu.be", "https://invidious.nerdvpn.de")
    private var invInstances: List<String>? = null

    private suspend fun invs(): List<String> {
        invInstances?.let { return it }
        val live = runCatching {
            val a = JSONArray(withContext(Dispatchers.IO) { get("https://api.invidious.io/instances.json?sort_by=type,health") })
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONArray(i).getJSONObject(1)
                if (o.optBoolean("api") && o.optString("type") == "https") o.optString("uri").trimEnd('/').takeIf { it.startsWith("https://") } else null
            }.take(6)
        }.getOrDefault(emptyList())
        return (live + INV_FALLBACK).distinct().also { invInstances = it }
    }

    /** The text of [path] from the first Invidious instance that answers. */
    private suspend fun invText(path: String): String = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (base in invs()) {
            try { return@withContext get(base + path) } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no instance")
    }

    private fun invItems(a: JSONArray): List<PItem> = (0 until a.length()).mapNotNull {
        val o = a.getJSONObject(it)
        val id = o.optString("videoId")
        if (id.isEmpty()) null else PItem(o.optString("title"), o.optString("author").removeSuffix(" - Topic"), o.optInt("lengthSeconds", -1),
            o.optJSONArray("videoThumbnails")?.optJSONObject(0)?.optString("url")?.ifEmpty { null } ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg", id)
    }

    suspend fun youtubePlaylist(id: String): PList = try {
        val o = api("/playlists/$id")
        PList(o.optString("name", "Playlist"), items(o.optJSONArray("relatedStreams") ?: JSONArray()), "https://www.youtube.com/playlist?list=$id")
        // shortcut: only the first page (about 100 songs), no "nextpage" paging; add /nextpage/playlists/$id if long lists matter
    } catch (e: Exception) {
        val o = JSONObject(invText("/api/v1/playlists/$id"))
        PList(o.optString("title", "Playlist"), invItems(o.optJSONArray("videos") ?: JSONArray()), "https://www.youtube.com/playlist?list=$id")
    }

    suspend fun searchSongs(q: String): PList = try {
        PList(q, items(api("/search?q=${enc(q)}&filter=music_songs").optJSONArray("items") ?: JSONArray()))
    } catch (e: Exception) {
        PList(q, invItems(JSONArray(invText("/api/v1/search?q=${enc(q)}&type=video"))))
    }

    /** Playlist ids found by a text search, as (id, name). */
    suspend fun searchPlaylists(q: String): List<Pair<String, String>> = try {
        val a = api("/search?q=${enc(q)}&filter=playlists").optJSONArray("items") ?: JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull {
            val id = it.optString("url").substringAfter("list=", "")
            if (id.isEmpty()) null else id to it.optString("name")
        }
    } catch (e: Exception) {
        val a = JSONArray(invText("/api/v1/search?q=${enc(q)}&type=playlist"))
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull {
            val id = it.optString("playlistId")
            if (id.isEmpty()) null else id to it.optString("title")
        }
    }

    /** Spotify track list from the public embed page (no login). Throws when Spotify changed the page. */
    suspend fun spotifyPlaylist(id: String): PList = withContext(Dispatchers.IO) {
        val html = get("https://open.spotify.com/embed/playlist/$id")
        val json = html.substringAfter("id=\"__NEXT_DATA__\"", "").substringAfter('>').substringBefore("</script>")
        val ent = JSONObject(json).getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        val tl = ent.getJSONArray("trackList")
        PList(ent.optString("name", ent.optString("title", "Spotify")), (0 until tl.length()).map {
            val t = tl.getJSONObject(it)
            PItem(t.optString("title"), t.optString("subtitle"), t.optInt("duration", 0) / 1000, null)
        })
    }

    /** YouTube id of [i]: its own, or the first music-search hit for "artist title". */
    suspend fun videoId(i: PItem): String? {
        i.videoId?.let { return it }
        val q = "${i.artist} ${i.title}"
        synchronized(ids) { if (ids.containsKey(q)) return ids[q] }
        val r = runCatching { searchSongs(q).items.firstOrNull()?.videoId }.getOrNull()
        synchronized(ids) { ids[q] = r }
        return r
    }

    /** Direct audio URL (Piped proxy, MP4/AAC preferred because ExoPlayer plays it everywhere). */
    suspend fun audioUrl(videoId: String): String? = runCatching {
        val a = api("/streams/$videoId").getJSONArray("audioStreams")
        val l = (0 until a.length()).map { a.getJSONObject(it) }
        (l.filter { it.optString("mimeType").contains("mp4") }.maxByOrNull { it.optInt("bitrate") } ?: l.maxByOrNull { it.optInt("bitrate") })?.optString("url")
    }.getOrNull() ?: runCatching {
        // Invidious: the audio-only formats; local=true makes the instance proxy the stream, so the link works without a YouTube session
        val a = JSONObject(invText("/api/v1/videos/$videoId?local=true")).getJSONArray("adaptiveFormats")
        val l = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("type").startsWith("audio/") && it.optString("url").isNotEmpty() }
        (l.filter { it.optString("type").contains("mp4") }.maxByOrNull { it.optString("bitrate").toIntOrNull() ?: 0 } ?: l.maxByOrNull { it.optString("bitrate").toIntOrNull() ?: 0 })?.optString("url")
    }.getOrNull()

    suspend fun audioUrls(list: List<PItem>): List<Pair<PItem, String>> = coroutineScope {
        list.map { i -> async { videoId(i)?.let { audioUrl(it) }?.let { i to it } } }.mapNotNull { it.await() }
    }

    /** What a pasted link or text points to. */
    sealed class Src {
        data class YtList(val id: String) : Src()
        data class YtVideo(val id: String) : Src()
        data class Spotify(val id: String) : Src()
        data class Text(val q: String) : Src()
    }

    fun parse(s0: String): Src {
        val s = s0.trim()
        Regex("open\\.spotify\\.com/(?:embed/)?playlist/([A-Za-z0-9]+)").find(s)?.let { return Src.Spotify(it.groupValues[1]) }
        if (s.startsWith("http")) {
            Regex("[?&]list=([A-Za-z0-9_-]+)").find(s)?.let { return Src.YtList(it.groupValues[1]) }
            Regex("(?:[?&]v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{11})").find(s)?.let { return Src.YtVideo(it.groupValues[1]) }
        }
        return Src.Text(s)
    }
}
