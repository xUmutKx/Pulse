package com.umutk.pulse.extras.ebook

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free books to put on the shelf: public-domain texts (copyright has run out) from Project Gutenberg, found through its
 * catalogue API (gutendex.com). Turkish books are preferred by the search; other languages are found too.
 */
object BookFind {
    class Found(val title: String, val author: String, val epub: String?, val txt: String?, val cover: String?)

    /** The books whose title or author matches [q]. Throws when there is no connection. */
    fun search(q: String): List<Found> {
        val url = "https://gutendex.com/books/?languages=tr&search=" + URLEncoder.encode(q.trim(), "UTF-8")
        val results = JSONObject(get(url)).getJSONArray("results")
        return List(results.length()) { i ->
            val o = results.getJSONObject(i)
            val formats = o.getJSONObject("formats")
            val authors = o.optJSONArray("authors")
            val author = if (authors == null) "" else (0 until authors.length()).joinToString(", ") { authors.getJSONObject(it).optString("name") }
            val txt = formats.keys().asSequence().firstOrNull { it.startsWith("text/plain") }?.let { formats.getString(it) }
            Found(o.getString("title"), author, formats.optString("application/epub+zip").ifEmpty { null }, txt, formats.optString("image/jpeg").ifEmpty { null })
        }
    }

    /** Downloads the book into [dir] (EPUB when there is one, else the plain text). Null when there is no file to take. */
    fun download(dir: File, found: Found): File? {
        val (url, ext) = found.epub?.let { it to "epub" } ?: found.txt?.let { it to "txt" } ?: return null
        val name = found.title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().take(80).ifEmpty { "book" } + "." + ext
        dir.mkdirs()
        val f = File(dir, name)
        open(url).use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    private fun open(url: String): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Pulse/1.0 (Android)")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        return c.inputStream
    }

    /** The cover picture of a book, or null when it cannot be loaded. */
    fun image(url: String): android.graphics.Bitmap? = runCatching { android.graphics.BitmapFactory.decodeStream(open(url)) }.getOrNull()

    private fun get(url: String): String = open(url).bufferedReader().use { it.readText() }
}
