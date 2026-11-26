package com.umutk.pulse.extras.ytx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * YouTube Music's radio: the songs that follow [videoId] in its "automix" playlist (RDAMVM<id>), which is what the YouTube Music app plays after a song.
 * The music player used to stop after the picked song (and then guess from plain searches); now its queue goes on with this.
 */
object MusicRadio {
    private fun text(o: JSONObject?): String =
        o?.optString("simpleText").orEmpty().ifBlank { o?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty() }

    private fun collect(r: JSONObject, seed: String, into: LinkedHashMap<String, VideoItem>) {
        r.walk { key, obj ->
            if (key == "playlistPanelVideoRenderer") {
                val id = obj.optString("videoId")
                if (id.length == 11 && id != seed && id !in into) {
                    val by = obj.optJSONObject("longBylineText") ?: obj.optJSONObject("shortBylineText")
                    val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val len = text(obj.optJSONObject("lengthText"))
                    val secs = if (Regex("^\\d{1,2}(:\\d{2}){1,2}$").matches(len)) len.split(':').fold(0L) { acc, p -> acc * 60 + p.toLong() } else 0L
                    into[id] = VideoItem(
                        id = id, title = text(obj.optJSONObject("title")), channel = by?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty(),
                        channelUrl = null, thumbnail = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url"), avatar = null, views = -1, durationSec = secs, uploaded = null,
                    )
                }
            }
        }
    }

    /** The playlist id the answer itself names for its panel (a station of its own) when it differs from the one asked for. */
    private fun nestedPlaylist(r: JSONObject, asked: String): String? {
        var found: String? = null
        r.walk { key, obj -> if (found == null && key == "playlistPanelRenderer") obj.optString("playlistId").takeIf { it.isNotBlank() && it != asked }?.let { found = it } }
        return found
    }

    /**
     * The same three steps YouTube Music's own clients take (Flow, GPL-3.0, does the same in its Media3MusicService): the automix of the song; when that has one entry
     * or none, the station the answer names; and when still nothing, the plain watch-next of the song.
     */
    suspend fun after(videoId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, VideoItem>()
        val mix = "RDAMVM$videoId"
        runCatching {
            val first = InnerTube.postAny("next", JSONObject().put("videoId", videoId).put("playlistId", mix))
            collect(first, videoId, out)
            if (out.size <= 1) nestedPlaylist(first, mix)?.let { nested ->
                collect(InnerTube.postAny("next", JSONObject().put("videoId", videoId).put("playlistId", nested)), videoId, out)
            }
            if (out.size <= 1) collect(InnerTube.postAny("next", JSONObject().put("videoId", videoId)), videoId, out)
        }
        out.values.filter { it.title.isNotBlank() }
    }
}
