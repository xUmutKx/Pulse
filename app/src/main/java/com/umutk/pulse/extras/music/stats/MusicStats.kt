package com.umutk.pulse.extras.music.stats

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.json.JSONObject

/** Per-track numbers. [plays] counts a track once it has been heard for 30 s (or to its end). */
data class TrackStat(val path: String, val title: String, val artist: String, val album: String, val plays: Int, val listenMs: Long, val lastPlayed: Long)

/** Play counts, listening time, last played and favourites, kept as one JSON string in prefs. */
object MusicStats {
    private const val PREFS = "pulse_music_stats"
    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun read(c: Context) = runCatching { JSONObject(sp(c).getString("t", "{}") ?: "{}") }.getOrDefault(JSONObject())

    /** Adds [ms] of listening to a track; [counted] adds one play. */
    @Synchronized
    fun add(c: Context, path: String, title: String?, artist: String?, album: String?, ms: Long, counted: Boolean) {
        if (ms <= 0 && !counted) return
        val all = read(c)
        val o = all.optJSONObject(path) ?: JSONObject()
        o.put("ti", title ?: path.substringAfterLast('/').substringBeforeLast('.')).put("ar", artist ?: "").put("al", album ?: "")
        o.put("ms", o.optLong("ms") + ms)
        if (counted) o.put("n", o.optInt("n") + 1).put("last", System.currentTimeMillis())
        all.put(path, o)
        sp(c).edit().putString("t", all.toString()).apply()
    }

    fun all(c: Context): List<TrackStat> {
        val j = read(c)
        return j.keys().asSequence().map { p ->
            val o = j.getJSONObject(p)
            TrackStat(p, o.optString("ti"), o.optString("ar"), o.optString("al"), o.optInt("n"), o.optLong("ms"), o.optLong("last"))
        }.toList()
    }

    fun mostPlayed(c: Context, n: Int = 20) = all(c).filter { it.plays > 0 }.sortedByDescending { it.plays }.take(n)
    fun recent(c: Context, n: Int = 20) = all(c).filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(n)
    fun totalMs(c: Context) = all(c).sumOf { it.listenMs }
    fun totalPlays(c: Context) = all(c).sumOf { it.plays }

    /** Top artists or albums by plays; blank names are left out. */
    fun top(c: Context, byArtist: Boolean, n: Int = 10): List<Pair<String, Int>> =
        all(c).filter { it.plays > 0 }.groupBy { if (byArtist) it.artist else it.album }
            .filterKeys { it.isNotBlank() }.mapValues { e -> e.value.sumOf { it.plays } }
            .toList().sortedByDescending { it.second }.take(n)

    fun clear(c: Context) { sp(c).edit().remove("t").apply() }

    fun favourites(c: Context): Set<String> = sp(c).getStringSet("fav", emptySet()) ?: emptySet()
    fun isFavourite(c: Context, path: String) = path in favourites(c)
    fun toggleFavourite(c: Context, path: String) {
        val s = favourites(c).toMutableSet()
        if (!s.add(path)) s.remove(path)
        sp(c).edit().putStringSet("fav", s).apply()
    }
}

/** Attach once to the engine's player: measures real playing time per track, even with the screen off. */
class StatsListener(private val app: Context, private val player: Player) : Player.Listener {
    private var item: MediaItem? = null
    private var since = 0L
    private var heard = 0L
    private var counted = false

    private fun flush() {
        val it = item ?: return
        if (since != 0L) { heard += System.currentTimeMillis() - since; since = 0L }
        val n = !counted && heard >= 30_000
        if (n) counted = true
        val m = it.mediaMetadata
        MusicStats.add(app, it.mediaId, m.title?.toString(), m.artist?.toString(), m.albumTitle?.toString(), heard, n)
        heard = 0
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (item == null) item = player.currentMediaItem
        if (isPlaying) since = System.currentTimeMillis() else flush()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        flush()
        item = mediaItem; counted = false
        since = if (player.isPlaying) System.currentTimeMillis() else 0L
    }
}
