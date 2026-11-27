package com.umutk.pulse.extras.keyboard

import android.content.Context
import java.io.File

/** Emoji mix (from WM Keyboard's idea, MIT): two emoji made into one picture by Google's Emoji Kitchen, read through emojik.vercel.app and kept in the cache. */
object KbEmojiMix {
    /** The last two emoji of [text] and how many characters lie between and after them (spaces), or null. */
    fun lastTwo(text: String): Triple<String, String, Int>? {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(text)
        val parts = ArrayList<String>()
        var start = it.first(); var end = it.next()
        while (end != java.text.BreakIterator.DONE) { parts += text.substring(start, end); start = end; end = it.next() }
        fun emoji(g: String) = g.codePoints().anyMatch { cp -> Character.getType(cp) == Character.OTHER_SYMBOL.toInt() || cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF }
        var spaces = 0
        val tail = parts.asReversed().dropWhile { g -> (g.isBlank()).also { if (it) spaces += g.length } }
        if (tail.size < 2 || !emoji(tail[0]) || !emoji(tail[1])) return null
        return Triple(tail[1], tail[0], spaces)
    }

    fun fetch(c: Context, a: String, b: String): File? = runCatching {
        val dir = File(c.cacheDir, "emojimix").apply { mkdirs() }
        val f = File(dir, Integer.toHexString((a + b).hashCode()) + ".png")
        if (f.isFile && f.length() > 0) return@runCatching f
        val url = "https://emojik.vercel.app/s/" + java.net.URLEncoder.encode(a + "_" + b, "UTF-8") + "?size=256"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 12000
        if (conn.responseCode !in 200..299 || conn.contentType?.startsWith("image") != true) return@runCatching null
        conn.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.takeIf { it.length() > 0 }
    }.getOrNull()
}
