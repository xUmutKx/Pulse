package com.umutk.pulse.extras.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * What is listened to in the music player, online or on the phone, and what that says about taste (the idea of Flow's music brain, without its database).
 *
 * - Every song that starts is written to a list (newest first, 600 kept): the history of the player, saved without a press.
 * - How a song ended teaches the artist's score: heard to the end +1, skipped within half a minute -1.5, stars: 4 or 5 +3, 1 or 2 -5 and the song is never offered again.
 * - The online "For you" list and the radio leave out songs and artists the score says are not liked.
 * A song is known by its YouTube id when it is online, by its path when it is on the phone.
 */
object ListenLog {
    class Entry(val key: String, val title: String, val artist: String, val online: Boolean, val at: Long)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("listen_log", Context.MODE_PRIVATE)
    private const val MAX = 600
    /** At or below this artist score the artist's songs are no longer offered by "For you" and the radio. */
    const val DISLIKED_AT = -4f

    @Synchronized fun all(c: Context): List<Entry> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Entry(o.getString("k"), o.optString("t"), o.optString("a"), o.optBoolean("o"), o.optLong("at")) } }
    }.getOrDefault(emptyList())

    /** A song started: it goes first in the list (a song played again moves up, not twice). */
    @Synchronized fun record(c: Context, key: String, title: String, artist: String, online: Boolean) {
        val l = (listOf(Entry(key, title, artist, online, System.currentTimeMillis())) + all(c).filter { it.key != key }).take(MAX)
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("k", it.key).put("t", it.title).put("a", it.artist).put("o", it.online).put("at", it.at)) }
        sp(c).edit().putString("list", a.toString()).apply()
        bump(c, artist, 1f)
    }

    private fun scores(c: Context): MutableMap<String, Float> = runCatching {
        val o = JSONObject(sp(c).getString("scores", "{}")); o.keys().asSequence().associateWith { o.optDouble(it).toFloat() }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun artistKey(a: String) = a.lowercase().substringBefore(",").substringBefore("&").substringBefore(" feat").trim()

    @Synchronized private fun bump(c: Context, artist: String, by: Float) {
        val k = artistKey(artist); if (k.isEmpty()) return
        val m = scores(c)
        // older taste fades: every artist's score drifts a little toward zero each time anything is learned
        m.keys.toList().forEach { x -> m[x] = (m[x] ?: 0f) * 0.995f }
        m[k] = ((m[k] ?: 0f) + by).coerceIn(-12f, 40f)
        val o = JSONObject(); m.filterValues { kotlin.math.abs(it) > 0.05f }.forEach { (x, v) -> o.put(x, v.toDouble()) }
        sp(c).edit().putString("scores", o.toString()).apply()
    }

    /** A song ended or was left: [heardMs] of it were heard; [auto] is true when it played to its end by itself (not skipped). */
    fun finished(c: Context, key: String, artist: String, heardMs: Long, auto: Boolean) {
        when {
            !auto && heardMs < 30_000L -> bump(c, artist, -1.5f)
            auto || heardMs > 90_000L -> bump(c, artist, 1f)
        }
    }

    /** Stars were given: 4 or 5 teach the artist, 1 or 2 teach against it and the song itself is blocked from the lists that suggest. */
    @Synchronized fun rate(c: Context, key: String, stars: Int) {
        val artist = all(c).firstOrNull { it.key == key }?.artist.orEmpty()
        val blocked = sp(c).getStringSet("blocked", emptySet()).orEmpty().toMutableSet()
        if (stars in 1..2) { blocked += key; bump(c, artist, -5f) }
        else { blocked -= key; if (stars >= 4) bump(c, artist, 3f) }
        sp(c).edit().putStringSet("blocked", blocked).apply()
    }

    /** True when the song, or its artist, was given up: it is left out of "For you" and of the radio. */
    fun disliked(c: Context, key: String?, artist: String): Boolean {
        if (key != null && key in sp(c).getStringSet("blocked", emptySet()).orEmpty()) return true
        val k = artistKey(artist)
        return k.isNotEmpty() && (scores(c)[k] ?: 0f) <= DISLIKED_AT
    }

    /** The artists the player has learned the person likes most, best first. */
    fun topArtists(c: Context, n: Int = 5): List<String> = scores(c).entries.filter { it.value > 1f }.sortedByDescending { it.value }.take(n).map { it.key }

    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
