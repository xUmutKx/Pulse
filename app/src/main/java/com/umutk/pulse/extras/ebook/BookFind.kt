package com.umutk.pulse.extras.ebook

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free books to put on the shelf, from the places the reader searches. A source is either "gutendex" (the Project Gutenberg
 * catalogue API) or an address with {q} where the search goes: a JSON answer is read like gutendex, an OPDS catalogue
 * (the standard feed of e-book libraries, Calibre-Web and the like) is read as Atom. Only sources that hold books the user
 * may take are meant to be used.
 */
object BookFind {
    const val GUTENDEX = "gutendex"

    class Found(val title: String, val author: String, val epub: String?, val txt: String?, val cover: String?)

    /** The books of [source] whose title or author matches [q]. Throws when there is no connection. */
    fun search(source: String, q: String): List<Found> {
        val term = URLEncoder.encode(q.trim(), "UTF-8")
        if (source == GUTENDEX) return gutendex(get("https://gutendex.com/books/?languages=tr&search=$term"))
        val url = source.replace("{q}", term)
        val text = get(url).trimStart()
        return if (text.startsWith("{")) gutendex(text) else opds(text, url)
    }

    private fun gutendex(json: String): List<Found> {
        val results = JSONObject(json).getJSONArray("results")
        return List(results.length()) { i ->
            val o = results.getJSONObject(i)
            val formats = o.getJSONObject("formats")
            val authors = o.optJSONArray("authors")
            val author = if (authors == null) "" else (0 until authors.length()).joinToString(", ") { authors.getJSONObject(it).optString("name") }
            val txt = formats.keys().asSequence().firstOrNull { it.startsWith("text/plain") }?.let { formats.getString(it) }
            Found(o.getString("title"), author, formats.optString("application/epub+zip").ifEmpty { null }, txt, formats.optString("image/jpeg").ifEmpty { null })
        }
    }

    /** An OPDS (Atom) catalogue: each entry with an EPUB link; links may be relative to the catalogue's address [base]. */
    private fun opds(xml: String, base: String): List<Found> {
        val out = ArrayList<Found>()
        val p = android.util.Xml.newPullParser().apply { setInput(StringReader(xml)) }
        var inEntry = false; var inAuthor = false; var field = ""
        var title = ""; var author = ""; var epub: String? = null; var cover: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "entry" -> { inEntry = true; title = ""; author = ""; epub = null; cover = null }
                    "title" -> if (inEntry) field = "title"
                    "author" -> inAuthor = true
                    "name" -> if (inAuthor) field = "author"
                    "link" -> if (inEntry) {
                        val rel = p.getAttributeValue(null, "rel").orEmpty()
                        val type = p.getAttributeValue(null, "type").orEmpty()
                        val href = p.getAttributeValue(null, "href")?.let { runCatching { URL(URL(base), it).toString() }.getOrNull() }
                        if (href != null) {
                            if (rel.contains("acquisition") && type == "application/epub+zip" && epub == null) epub = href
                            if (rel.contains("image") && cover == null) cover = href
                        }
                    }
                }
                XmlPullParser.TEXT -> when (field) {
                    "title" -> title += p.text
                    "author" -> author += p.text
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "entry" -> { if (inEntry && epub != null) out += Found(title.trim(), author.trim(), epub, null, cover); inEntry = false }
                    "author" -> { inAuthor = false; field = "" }
                    "title", "name" -> field = ""
                }
            }
            ev = p.next()
        }
        return out
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
