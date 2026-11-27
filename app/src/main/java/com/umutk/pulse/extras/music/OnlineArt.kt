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

    fun has(path: String) = urls.containsKey(path)

    fun isOnline(path: String) = path.startsWith("http://") || path.startsWith("https://")

    /** The cover of the stream [path] (blocking: call off the main thread), or null when none was remembered or it could not be fetched. */
    fun load(ctx: Context, path: String): ImageBitmap? {
        val u = bigger(urls[path] ?: return null)
        val dir = File(ctx.cacheDir, "online_art").also { it.mkdirs() }
        if (!purged) { purged = true; val now = System.currentTimeMillis(); dir.listFiles()?.forEach { if (now - it.lastModified() > DAY) it.delete() } }
        val f = File(dir, Integer.toHexString(u.hashCode()) + ".jpg")
        if (!f.isFile || f.length() == 0L || System.currentTimeMillis() - f.lastModified() > DAY) {
            // the picture is written to a side file and kept only when it came whole: a download cut half way was kept as the cover for a day
            // (BitmapFactory draws a cut JPEG as its top part over grey: the cover showed only its upper strip)
            runCatching {
                val tmp = File(dir, f.name + ".part")
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 15000
                if (c.responseCode in 200..299) {
                    val want = c.contentLengthLong
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    if ((want <= 0L || tmp.length() == want) && whole(tmp)) tmp.renameTo(f) else tmp.delete()
                }
                c.disconnect()
            }
        }
        // a video picture (16:9 or 4:3 with black bars) loses its bars, then is cut to the square in its middle, so the cover fills its square with no empty sides
        return runCatching {
            val b = BitmapFactory.decodeFile(f.path) ?: return@runCatching null
            val r = content(b)
            val side = minOf(r.width(), r.height())
            val sq = if (r.width() == r.height() && r.width() == b.width) b
                else android.graphics.Bitmap.createBitmap(b, r.left + (r.width() - side) / 2, r.top + (r.height() - side) / 2, side, side)
            sq.asImageBitmap()
        }.getOrNull()
    }

    /** The larger picture of the same cover: YouTube Music's covers come as small squares (=w60-h60 …), YouTube's as the 480 picture. */
    private fun bigger(u: String): String =
        if ("googleusercontent.com" in u || "ggpht.com" in u) u.replace(Regex("=w\\d+-h\\d+[^&]*$"), "=w544-h544-l90-rj").replace(Regex("=s\\d+[^&]*$"), "=s544")
        else u

    /** A JPEG ends with FF D9 (a cut one does not); other pictures are taken as they are. */
    private fun whole(f: File): Boolean = runCatching {
        java.io.RandomAccessFile(f, "r").use { r ->
            if (r.length() < 4) return@use false
            val head = ByteArray(2); r.readFully(head)
            if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return@use true
            r.seek(r.length() - 2); val tail = ByteArray(2); r.readFully(tail)
            tail[0] == 0xFF.toByte() && tail[1] == 0xD9.toByte()
        }
    }.getOrDefault(false)

    /** The picture without the black bars above and below (or at the sides) that YouTube's video pictures have. */
    private fun content(b: android.graphics.Bitmap): android.graphics.Rect {
        fun darkRow(y: Int) = (0 until b.width step (b.width / 24).coerceAtLeast(1)).all { x -> val p = b.getPixel(x, y); (p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF) < 60 }
        fun darkCol(x: Int) = (0 until b.height step (b.height / 24).coerceAtLeast(1)).all { y -> val p = b.getPixel(x, y); (p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF) < 60 }
        var top = 0; var bottom = b.height - 1; var left = 0; var right = b.width - 1
        while (top < b.height / 3 && darkRow(top)) top++
        while (bottom > b.height * 2 / 3 && darkRow(bottom)) bottom--
        while (left < b.width / 3 && darkCol(left)) left++
        while (right > b.width * 2 / 3 && darkCol(right)) right--
        return android.graphics.Rect(left, top, right + 1, bottom + 1)
    }
}
