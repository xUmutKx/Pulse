package com.umutk.pulse.extras.ytx

import org.json.JSONArray
import org.json.JSONObject

/**
 * Videos watched in this player, kept on the phone (newest first, 200): the History page shows them when no account is signed in
 * (a signed-in account keeps its own history at YouTube). Music mode has its own list ([RecentMusic]).
 */
object WatchedLog {
    private const val MAX = 200
    private val prefs by lazy { YTXTubeApplication.appContext.getSharedPreferences("watched_log", android.content.Context.MODE_PRIVATE) }

    @Synchronized fun all(): List<VideoItem> = runCatching {
        val a = JSONArray(prefs.getString("list", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); if (id.isBlank()) return@mapNotNull null
            VideoItem(id, o.optString("title"), o.optString("channel"), null, o.optString("thumb").takeIf { it.isNotBlank() }, null, o.optLong("duration"), -1, null)
        }
    }.getOrDefault(emptyList())

    @Synchronized fun record(v: VideoItem) {
        if (v.id.length != 11 || v.localUri != null) return
        val l = (listOf(v) + all().filter { it.id != v.id }).take(MAX)
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel).put("thumb", it.thumbnail ?: "").put("duration", it.durationSec)) }
        prefs.edit().putString("list", a.toString()).apply()
    }
}
