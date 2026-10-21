package com.umutk.pulse.extras.music

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import com.umutk.pulse.extras.music.poweramp.displayName
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Tags for a song from the iTunes Search API (no account or key): artist, album, genre and year. They are kept in the app
 * ([MusicTags]) beside the user's own edits and only fill what the song is missing. A song that finds nothing is not tried
 * again for a week.
 */
internal object OnlineTags {
    class Found(val artist: String?, val album: String?, val genre: String?, val year: Int)

    private const val WEEK_MS = 7L * 24 * 3600 * 1000
    // shortcut: a fixed pause between lookups, about 20 a minute, instead of reading the service's real rate limit
    private const val GAP_MS = 3_000L
    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun tried(c: Context) = c.getSharedPreferences("music_tags_tried", Context.MODE_PRIVATE)

    private fun online(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun unknown(s: String?) = s.isNullOrBlank() || s == "<unknown>"

    /** One lookup by title (and the artist when it is known). Null when nothing matches or the network fails. Never on the main thread. */
    fun lookup(t: Track): Found? {
        if (Looper.myLooper() == Looper.getMainLooper() || t.path.startsWith("http")) return null
        val title = t.displayName().trim()
        val term = listOfNotNull(t.artist?.takeIf { !unknown(it) }, title).joinToString(" ").trim()
        if (term.isEmpty()) return null
        return runCatching {
            val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&media=music&entity=song&limit=5"
            val res = JSONObject(String(PowerampCovers.fetch(url))).getJSONArray("results")
            val hits = (0 until res.length()).map { res.getJSONObject(it) }
            val hit = hits.firstOrNull { it.optString("trackName").equals(title, true) }
                ?: hits.firstOrNull { val n = it.optString("trackName"); n.isNotBlank() && (n.contains(title, true) || title.contains(n, true)) }
                ?: return@runCatching null
            Found(
                hit.optString("artistName").ifBlank { null }, hit.optString("collectionName").ifBlank { null },
                hit.optString("primaryGenreName").ifBlank { null }, hit.optString("releaseDate").take(4).toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    /** Looks the song up and stores only the tags it is missing. True when something was stored. */
    fun fill(c: Context, t: Track): Boolean {
        val f = lookup(t) ?: return false
        val ar = f.artist.takeIf { unknown(t.artist) }
        val al = f.album.takeIf { unknown(t.album) }
        val ge = f.genre.takeIf { unknown(t.genre) }
        val yr = f.year.takeIf { t.year <= 0 && it > 0 }
        if (ar == null && al == null && ge == null && yr == null) return false
        MusicTags.merge(c, t.path, ar, al, ge, yr)
        return true
    }

    private fun missing(t: Track) = unknown(t.album) || unknown(t.genre) || t.year <= 0

    /** Fills the missing tags of the songs one at a time in the background, while the phone has internet. Does nothing when the setting is off. */
    fun backfill(c: Context, tracks: List<Track>) {
        if (!MusicSettings.onlineTags(c)) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                var stored = 0
                for (t in tracks) {
                    if (!online(c)) break
                    if (t.path.startsWith("http") || !missing(t)) continue
                    val last = tried(c).getLong(t.path, 0L)
                    if (last > 0L && System.currentTimeMillis() - last < WEEK_MS) continue
                    if (fill(c, t)) stored++
                    tried(c).edit().putLong(t.path, System.currentTimeMillis()).apply()
                    // the lists show the new tags after every few songs, not only at the end of a long pass
                    if (stored > 0 && stored % 10 == 0) MusicLibrary.reapplyTags(c)
                    delay(GAP_MS)
                }
                if (stored > 0) MusicLibrary.reapplyTags(c)
            } finally {
                running.set(false)
            }
        }
    }
}
