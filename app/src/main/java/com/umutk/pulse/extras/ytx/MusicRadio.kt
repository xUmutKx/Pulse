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

    suspend fun after(videoId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, VideoItem>()
        runCatching {
            val r = InnerTube.postAny("next", JSONObject().put("videoId", videoId).put("playlistId", "RDAMVM$videoId"))
            r.walk { key, obj ->
                if (key == "playlistPanelVideoRenderer") {
                    val id = obj.optString("videoId")
                    if (id.length == 11 && id != videoId && id !in out) {
                        val by = obj.optJSONObject("longBylineText") ?: obj.optJSONObject("shortBylineText")
                        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val len = text(obj.optJSONObject("lengthText"))
                        val secs = if (Regex("^\\d{1,2}(:\\d{2}){1,2}$").matches(len)) len.split(':').fold(0L) { acc, p -> acc * 60 + p.toLong() } else 0L
                        out[id] = VideoItem(
                            id = id, title = text(obj.optJSONObject("title")), channel = by?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty(),
                            channelUrl = null, thumbnail = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url"), avatar = null, views = -1, durationSec = secs, uploaded = null,
                        )
                    }
                }
            }
        }
        out.values.filter { it.title.isNotBlank() }
    }
}
