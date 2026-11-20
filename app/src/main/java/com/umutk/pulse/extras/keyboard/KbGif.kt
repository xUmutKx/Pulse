package com.umutk.pulse.extras.keyboard

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** GIF search (as in WM Keyboard), through Tenor with the user's own key (Settings > Keyboard > Typing). Nothing is asked for without a key. */
object KbGif {
    class Gif(val preview: String, val full: String, val w: Int, val h: Int)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { c.disconnect() }
    }

    /** Trending GIFs for an empty [q]. Blocking. */
    fun search(q: String, key: String, lang: String): List<Gif> {
        if (key.isBlank()) return emptyList()
        val k = URLEncoder.encode(key.trim(), "UTF-8")
        val base = if (q.isBlank()) "https://tenor.googleapis.com/v2/featured?" else "https://tenor.googleapis.com/v2/search?q=${URLEncoder.encode(q, "UTF-8")}&"
        val o = JSONObject(get(base + "key=$k&client_key=pulse_keyboard&limit=30&contentfilter=medium&media_filter=tinygif,gif&locale=$lang"))
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val m = a.getJSONObject(i).optJSONObject("media_formats") ?: return@mapNotNull null
            val tiny = m.optJSONObject("tinygif") ?: return@mapNotNull null
            val full = m.optJSONObject("gif") ?: tiny
            val dims = tiny.optJSONArray("dims")
            Gif(tiny.getString("url"), full.getString("url"), dims?.optInt(0) ?: 1, dims?.optInt(1) ?: 1)
        }
    }

    /** Downloads [g] into the cache (where the file provider reaches it). Blocking. */
    fun fetch(c: Context, g: Gif): File {
        val dir = File(c.cacheDir, "gifs").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val f = File(dir, "gif_" + g.full.hashCode().toUInt().toString(16) + ".gif")
        if (!f.exists() || f.length() == 0L) {
            val conn = URL(g.full).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 20000
            conn.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
            conn.disconnect()
        }
        return f
    }

    @Volatile private var loader: coil.ImageLoader? = null
    /** An image loader that plays GIFs. */
    fun loader(c: Context): coil.ImageLoader = loader ?: coil.ImageLoader.Builder(c.applicationContext).components {
        if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
    }.build().also { loader = it }
}
