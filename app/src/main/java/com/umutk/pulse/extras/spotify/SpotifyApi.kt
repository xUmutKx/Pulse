package com.umutk.pulse.extras.spotify

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SpTrack(val uri: String, val title: String, val artist: String, val album: String, val durationMs: Long, val previewUrl: String?, val image: String?, val url: String?)
data class SpPlaylist(val id: String, val name: String, val count: Int, val image: String?)
data class SpNow(val track: SpTrack, val playing: Boolean, val progressMs: Long)

/** Spotify Web API, official endpoints only. Playback control needs Premium and an active Spotify device. */
class SpotifyApi(private val ctx: Context) {

    companion object {
        /** Why the last request got nothing (empty when it went through): shown on the Spotify screens, a list that comes back empty says so instead of staying blank. */
        var lastError by androidx.compose.runtime.mutableStateOf("")
    }

    private suspend fun call(method: String, path: String, body: String? = null): String? = withContext(Dispatchers.IO) {
        val token = SpotifyAuth.token(ctx)
        if (token == null) { lastError = "No valid login: log in again (the saved login could not be renewed)."; return@withContext null }
        val h = URL(if (path.startsWith("http")) path else "https://api.spotify.com/v1$path").openConnection() as HttpURLConnection
        try {
            h.requestMethod = method; h.connectTimeout = 10_000; h.readTimeout = 15_000
            h.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { h.doOutput = true; h.setRequestProperty("Content-Type", "application/json"); h.outputStream.use { it.write(body.toByteArray()) } }
            val code = h.responseCode
            if (code == 204 || code == 202) return@withContext ""
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(h.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()).optJSONObject("error")?.optString("message").orEmpty() }.getOrDefault("")
                lastError = "Spotify answered $code" + (if (msg.isNotEmpty()) ": $msg" else "") + when (code) {
                    401 -> ". The login has run out: log in again."
                    403 -> ". While the app is in development mode Spotify only lets the e-mail addresses added under User Management in its dashboard use it (and asks the app's owner for Premium)."
                    429 -> ". Too many requests: wait a minute."
                    else -> ""
                }
                return@withContext null
            }
            lastError = ""
            h.inputStream.bufferedReader().use { it.readText() }
        } finally { h.disconnect() }
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun track(o: JSONObject?): SpTrack? {
        o ?: return null
        val uri = o.optString("uri").takeIf { it.startsWith("spotify:track:") } ?: return null // skips podcast episodes and local files
        val al = o.optJSONObject("album")
        return SpTrack(
            uri, o.optString("name"),
            o.optJSONArray("artists")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } }.orEmpty(),
            al?.optString("name").orEmpty(), o.optLong("duration_ms"),
            o.optString("preview_url").takeIf { it.isNotEmpty() && it != "null" }, // 30 s clip, often missing now
            al?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
            o.optJSONObject("external_urls")?.optString("spotify"),
        )
    }

    private fun tracks(a: JSONArray?, key: String? = null): List<SpTrack> = a?.let {
        (0 until it.length()).mapNotNull { i ->
            val e = it.optJSONObject(i)
            track(if (key == null) e else e?.optJSONObject(key) ?: e?.optJSONObject("item") ?: e?.optJSONObject("track"))
        }
    }.orEmpty()

    suspend fun search(query: String): List<SpTrack> =
        call("GET", "/search?type=track&limit=30&q=${q(query)}")?.let { tracks(JSONObject(it).optJSONObject("tracks")?.optJSONArray("items")) }.orEmpty()

    suspend fun playlists(): List<SpPlaylist> =
        call("GET", "/me/playlists?limit=50")?.let { s ->
            val a = JSONObject(s).optJSONArray("items") ?: return emptyList()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                SpPlaylist(o.optString("id"), o.optString("name"),
                    (o.optJSONObject("tracks") ?: o.optJSONObject("items"))?.optInt("total") ?: 0,
                    o.optJSONArray("images")?.optJSONObject(0)?.optString("url"))
            }
        }.orEmpty()

    /** Up to 500 tracks, page by page. */
    suspend fun playlistTracks(id: String): List<SpTrack> {
        val out = ArrayList<SpTrack>()
        var url: String? = "/playlists/$id/tracks?limit=100"
        var pages = 0
        while (url != null && pages++ < 5) {
            val o = call("GET", url)?.let { JSONObject(it) } ?: break
            out += tracks(o.optJSONArray("items"), "track")
            url = o.optString("next").takeIf { it.isNotEmpty() && it != "null" }
        }
        return out
    }

    suspend fun liked(): List<SpTrack> =
        call("GET", "/me/tracks?limit=50")?.let { tracks(JSONObject(it).optJSONArray("items"), "track") }.orEmpty()

    suspend fun now(): SpNow? {
        val s = call("GET", "/me/player/currently-playing")?.takeIf { it.isNotEmpty() } ?: return null
        val o = JSONObject(s)
        val t = track(o.optJSONObject("item")) ?: return null
        return SpNow(t, o.optBoolean("is_playing"), o.optLong("progress_ms"))
    }

    /** True when Spotify accepted the command. */
    suspend fun play(uri: String? = null): Boolean = call("PUT", "/me/player/play", uri?.let { JSONObject().put("uris", JSONArray().put(it)).toString() }) != null
    suspend fun pause(): Boolean = call("PUT", "/me/player/pause") != null
    suspend fun next(): Boolean = call("POST", "/me/player/next") != null
    suspend fun previous(): Boolean = call("POST", "/me/player/previous") != null

    /** A plain-words check of the connection: who is logged in, which plan, how many liked songs; or why it fails. */
    suspend fun diagnose(): String {
        val me = call("GET", "/me") ?: return lastError.ifEmpty { "No answer from Spotify." }
        val o = runCatching { JSONObject(me) }.getOrNull() ?: return "Spotify answered something that is not readable."
        val name = o.optString("display_name").ifEmpty { o.optString("id") }
        val plan = o.optString("product").ifEmpty { "?" }
        val liked = call("GET", "/me/tracks?limit=1")?.let { runCatching { JSONObject(it).optInt("total", -1) }.getOrDefault(-1) }
        return "Connected as $name ($plan). " + when {
            liked == null -> "Liked songs could not be read: " + lastError
            liked < 0 -> "Liked songs: unreadable answer."
            liked == 0 -> "Liked songs: none on this account."
            else -> "Liked songs: $liked."
        }
    }
}
