package com.umutk.pulse.extras.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The online songs (YouTube Music) that were played in the music player, newest first, kept on the phone: the "For you" list and the radio start from them. */
object OnlineHistory {
    class Entry(val id: String, val title: String, val artist: String, val thumb: String?, val at: Long)

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("online_history", Context.MODE_PRIVATE)

    fun all(c: Context): List<Entry> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Entry(o.getString("id"), o.optString("t"), o.optString("a"), o.optString("p").ifEmpty { null }, o.optLong("at")) } }
    }.getOrDefault(emptyList())

    @Synchronized fun record(c: Context, id: String, title: String, artist: String, thumb: String?) {
        val l = (listOf(Entry(id, title, artist, thumb, System.currentTimeMillis())) + all(c).filter { it.id != id }).take(400)
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist).put("p", it.thumb ?: "").put("at", it.at)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    fun clear(c: Context) { sp(c).edit().remove("list").apply() }
}
