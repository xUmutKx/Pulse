package com.umutk.pulse.extras.music

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The covers of songs that play from YouTube Music or Spotify (their file is a stream link): the picture of the search result is remembered per link, fetched once and kept in the
 * cache folder for a day, then deleted. They show in the player, the lists and the notification like the cover of a song of the phone.
 */
object OnlineArt {
    private const val DAY = 24L * 60 * 60 * 1000
    private val urls = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var purged = false

    fun put(path: String, thumb: String?) { if (!thumb.isNullOrBlank()) urls[path] = thumb }

    fun isOnline(path: String) = path.startsWith("http://") || path.startsWith("https://")

    /** The cover of the stream [path] (blocking: call off the main thread), or null when none was remembered or it could not be fetched. */
    fun load(ctx: Context, path: String): ImageBitmap? {
        val u = urls[path] ?: return null
        val dir = File(ctx.cacheDir, "online_art").also { it.mkdirs() }
        if (!purged) { purged = true; val now = System.currentTimeMillis(); dir.listFiles()?.forEach { if (now - it.lastModified() > DAY) it.delete() } }
        val f = File(dir, Integer.toHexString(u.hashCode()) + ".jpg")
        if (!f.isFile || System.currentTimeMillis() - f.lastModified() > DAY) {
            runCatching {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 8000
                if (c.responseCode in 200..299) c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                c.disconnect()
            }
        }
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }
}
