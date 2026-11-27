package com.umutk.pulse.features.watch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.umutk.pulse.extras.ytx.Net
import com.umutk.pulse.extras.ytx.StreamCache
import com.umutk.pulse.extras.ytx.StreamSelection
import com.umutk.pulse.extras.ytx.YTXTubeApplication
import com.umutk.pulse.extras.ytx.YouTube
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * YouTube and YouTube Music for Pulse Watch (wear/PLAN.md part 6), done by the phone (no Google account on the watch): search, play on the phone, and
 * "save to the watch" (the phone downloads the audio into Download/Pulse/Watch, the watch then copies it with the file pieces of [WatchLink]).
 */
object WatchYt {
    fun search(c: Context, q: String, music: Boolean): JSONObject {
        if (q.isBlank()) return JSONObject().put("items", JSONArray())
        YTXTubeApplication.init(c)
        val list = runBlocking { if (music) YouTube.searchMusic(q) else YouTube.search(q) }
        val arr = JSONArray()
        list.filter { !it.isLive }.take(25).forEach { v -> arr.put(JSONObject().put("id", v.id).put("title", v.title).put("ch", v.channel.removeSuffix(" - Topic")).put("dur", v.durationSec)) }
        return JSONObject().put("items", arr)
    }

    fun playOnPhone(c: Context, id: String): JSONObject {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(YouTube.watchUrl(id))).setClassName(c, "com.umutk.pulse.extras.ytx.YtxActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return JSONObject().put("ok", runCatching { c.startActivity(i) }.isSuccess)
    }

    fun saveAudio(c: Context, id: String, title: String): JSONObject {
        YTXTubeApplication.init(c)
        val info = StreamCache.getOrLoad(id)
        val audio = StreamSelection.pick(info).audio ?: return JSONObject().put("err", "no audio")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse/Watch").apply { mkdirs() }
        val name = (title.ifBlank { info.name }).replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80) + ".m4a"
        val out = File(dir, name)
        if (!out.isFile || out.length() == 0L) {
            val tmp = File(dir, "$name.part")
            Net.client.newCall(Request.Builder().url(audio.content).build()).execute().use { r ->
                if (!r.isSuccessful) return JSONObject().put("err", "http ${r.code}")
                (r.body?.byteStream() ?: return JSONObject().put("err", "empty")).use { inp -> tmp.outputStream().use { inp.copyTo(it, 64 * 1024) } }
            }
            tmp.renameTo(out)
        }
        return JSONObject().put("p", out.path).put("n", out.name).put("size", out.length())
    }
}
